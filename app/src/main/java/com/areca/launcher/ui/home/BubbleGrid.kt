package com.areca.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.areca.launcher.R
import com.areca.launcher.ui.IconImage
import com.areca.launcher.data.zoomFor
import com.areca.launcher.ui.widgets.WidgetDrag
import com.areca.launcher.data.AppInfo
import com.areca.launcher.data.GridLayout
import com.areca.launcher.data.IconLoader
import com.areca.launcher.data.LauncherSettings
import com.areca.launcher.ui.AppContextMenu
import com.areca.launcher.ui.AppMenuActions
import com.areca.launcher.ui.theme.toShape
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val HEX_ROW_PITCH = 0.866f
private const val TAP_SLOP_PX = 12f
private const val LIFT_SCALE = 0.2f
private const val SWAP_RADIUS = 0.4f // fracción del tamaño de celda alrededor del centro de la burbuja destino
private val LIFT_SHADOW = 28.dp
private val AUTOSCROLL_EDGE = 110.dp
private const val AUTOSCROLL_MAX_PX = 28f

private class PositionHolder { var pos: Offset? = null }

/**
 * Burbujas en panal o en hileras rectas, centradas en la pantalla (con scroll si no caben).
 *
 * Reordenar: mantener pulsado y arrastrar. La burbuja se levanta (escala, sombra, vibración),
 * las demás se reacomodan en vivo con un resorte y, al soltar, cae a su lugar con rebote.
 * Mantener pulsado sin mover abre el menú.
 */
@Composable
fun BubbleGrid(
    apps: List<AppInfo>,
    settings: LauncherSettings,
    iconLoader: IconLoader,
    onLaunch: (AppInfo) -> Unit,
    onRemove: (AppInfo) -> Unit,
    actions: AppMenuActions,
    onReorder: (orderedKeys: List<String>) -> Unit,
    onEmptyLongPress: () -> Unit,
    onDoubleTapEmpty: () -> Unit,
    onPullDown: () -> Unit,
    widgetIds: List<Int>,
    bottomWidgetIds: Set<Int>,
    widgetHeight: (Int) -> Dp,
    widgetWidth: (Int) -> Dp?,
    onWidgetOrder: (List<Int>) -> Unit,
    widget: @Composable (id: Int, modifier: Modifier, width: Dp, height: Dp, drag: WidgetDrag?) -> Unit,
    modifier: Modifier = Modifier,
    recenterSignal: Int = 0,
) {
    if (settings.layout == GridLayout.HONEYCOMB) {
        WatchGrid(apps, settings, iconLoader, onLaunch, onRemove, actions, onReorder, onEmptyLongPress, onDoubleTapEmpty, onPullDown, widgetIds, bottomWidgetIds, widgetHeight, widgetWidth, onWidgetOrder, widget, recenterSignal, modifier)
        return
    }
    val honeycomb = settings.layout == GridLayout.HONEYCOMB
    val iconSize = settings.iconSizeDp.dp
    val shape = remember(settings.iconShape) { settings.iconShape.toShape() }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Orden local para reacomodar al instante mientras se arrastra; se persiste al soltar.
    // Un solo estado para toda la vida del componente: los gestos se registran una vez y capturan este
    // objeto; si se creara uno nuevo por cada lista (remember(apps)) verían una lista vieja y fallarían.
    val itemsState = remember { mutableStateOf(apps) }
    var syncedApps by remember { mutableStateOf(apps) }
    if (syncedApps !== apps) {
        syncedApps = apps
        itemsState.value = apps
    }
    var items by itemsState
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) } // coordenadas de pantalla
    val bounds = remember { HashMap<String, Rect>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }

    fun swapIfOverTarget() {
        val key = dragKey ?: return
        val target = items.firstOrNull { other ->
            other.key != key && bounds[other.key]?.let {
                (dragPointer - it.center).getDistance() < it.width * SWAP_RADIUS
            } == true
        } ?: return
        val list = items.toMutableList()
        list.add(list.indexOfFirst { it.key == target.key }, list.removeAt(list.indexOfFirst { it.key == key }))
        items = list
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Auto-scroll al acercar la burbuja arrastrada a los bordes de la pantalla.
    LaunchedEffect(dragKey) {
        if (dragKey == null) return@LaunchedEffect
        val edge = with(density) { AUTOSCROLL_EDGE.toPx() }
        while (true) {
            val vp = viewport
            val y = dragPointer.y
            val dy = when {
                y < vp.top + edge -> -((vp.top + edge - y) / edge).coerceIn(0f, 1f) * AUTOSCROLL_MAX_PX
                y > vp.bottom - edge -> ((y - (vp.bottom - edge)) / edge).coerceIn(0f, 1f) * AUTOSCROLL_MAX_PX
                else -> 0f
            }
            if (dy != 0f) {
                scrollState.scrollBy(dy)
                swapIfOverTarget()
            }
            delay(16)
        }
    }

    BoxWithConstraints(
        modifier
            .onGloballyPositioned { viewport = it.boundsInRoot() }
            // Si ya está al principio y se sigue tirando hacia abajo, baja la barra de notificaciones.
            // Solo observa (pase Initial): no consume nada.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startedAtTop = scrollState.value == 0
                    var dx = 0f
                    var dy = 0f
                    while (startedAtTop) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (dy > viewConfiguration.touchSlop * 3 && abs(dx) < dy) {
                            onPullDown()
                            break
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                // Solo cuenta si el dedo no está sobre ninguna burbuja (esas gestionan su propia pulsación larga).
                detectTapGestures(
                    onLongPress = { local ->
                        val root = local + viewport.topLeft
                        if (bounds.values.none { it.contains(root) }) onEmptyLongPress()
                    },
                    onDoubleTap = { local ->
                        val root = local + viewport.topLeft
                        if (bounds.values.none { it.contains(root) }) onDoubleTapEmpty()
                    },
                )
            },
    ) {
        // La celda no puede ser más ancha que el espacio disponible por columna.
        val cell = minOf(iconSize + settings.spacingDp.dp, maxWidth / settings.columns)
        val viewportHeight = maxHeight
        val viewportWidth = maxWidth

        Box(Modifier.fillMaxSize().verticalScroll(scrollState)) {
          Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            widgetIds.filter { it !in bottomWidgetIds }.forEach { id ->
                key(id) {
                    val h = widgetHeight(id)
                    val w = minOf((viewportWidth - 32.dp).coerceIn(0.dp, 480.dp), widgetWidth(id) ?: 480.dp)
                    widget(id, Modifier.padding(horizontal = 16.dp, vertical = 6.dp).size(w, h), w, h, null)
                }
            }
            BubbleLayout(
                columns = settings.columns,
                honeycomb = honeycomb,
                cell = cell,
                modifier = Modifier.fillMaxWidth().heightIn(min = viewportHeight),
            ) {
                items.forEach { app ->
                    key(app.key) {
                        BubbleItem(
                            app = app,
                            cellSize = cell,
                            iconSize = minOf(iconSize, cell),
                            iconPadding = minOf(iconSize, cell) * settings.iconPaddingPercent / 100,
                            iconZoom = settings.zoomFor(app.key),
                            iconPack = settings.iconPack,
                            iconOverride = settings.iconOverrides[app.key],
                            shape = shape,
                            iconLoader = iconLoader,
                            dragging = dragKey == app.key,
                            dragPointer = { dragPointer },
                            bounds = bounds,
                            onLaunch = onLaunch,
                            onRemove = onRemove,
                            actions = actions,
                            onDragStart = {
                                dragKey = app.key
                                dragPointer = bounds[app.key]?.center ?: Offset.Zero
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragBy = {
                                dragPointer += it
                                swapIfOverTarget()
                            },
                            onDragEnd = { moved ->
                                dragKey = null
                                if (moved) onReorder(items.map { it.key })
                            },
                        )
                    }
                }
            }
            widgetIds.filter { it in bottomWidgetIds }.forEach { id ->
                key(id) {
                    val h = widgetHeight(id)
                    val w = minOf((viewportWidth - 32.dp).coerceIn(0.dp, 480.dp), widgetWidth(id) ?: 480.dp)
                    widget(id, Modifier.padding(horizontal = 16.dp, vertical = 6.dp).size(w, h), w, h, null)
                }
            }
          }
        }
    }
}

/** Coloca las burbujas en panal/rectas, centradas horizontal y verticalmente. */
@Composable
private fun BubbleLayout(
    columns: Int,
    honeycomb: Boolean,
    cell: Dp,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val cellPx = cell.roundToPx()
        val placeables = measurables.map { it.measure(Constraints.fixed(cellPx, cellPx)) }
        val pitch = if (honeycomb) (cellPx * HEX_ROW_PITCH).roundToInt() else cellPx

        // Panal: filas pares con [columns] elementos, impares con columns-1.
        val rowSizes = ArrayList<Int>()
        var i = 0
        while (i < placeables.size) {
            val n = min(placeables.size - i, if (honeycomb && rowSizes.size % 2 == 1) max(1, columns - 1) else columns)
            rowSizes += n
            i += n
        }
        val contentHeight = if (rowSizes.isEmpty()) 0 else (rowSizes.size - 1) * pitch + cellPx
        val width = constraints.maxWidth
        val height = max(contentHeight, constraints.minHeight)

        layout(width, height) {
            val top = (height - contentHeight) / 2
            var index = 0
            rowSizes.forEachIndexed { row, n ->
                val left = (width - n * cellPx) / 2
                repeat(n) { col -> placeables[index++].place(left + col * cellPx, top + row * pitch) }
            }
        }
    }
}

@Composable
private fun BubbleItem(
    app: AppInfo,
    cellSize: Dp,
    iconSize: Dp,
    iconPadding: Dp,
    iconZoom: Int,
    iconPack: String,
    iconOverride: String?,
    shape: Shape,
    iconLoader: IconLoader,
    dragging: Boolean,
    dragPointer: () -> Offset,
    bounds: MutableMap<String, Rect>,
    onLaunch: (AppInfo) -> Unit,
    onRemove: (AppInfo) -> Unit,
    actions: AppMenuActions,
    onDragStart: () -> Unit,
    onDragBy: (Offset) -> Unit,
    onDragEnd: (moved: Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val tilt = LocalTilt.current
    val icon by produceState<ImageBitmap?>(iconLoader.cached(app, iconPack, iconOverride), app, iconPack, iconOverride, iconLoader.styleKey) {
        value = withContext(Dispatchers.IO) { iconLoader.load(app, iconPack, iconOverride) }
    }
    DisposableEffect(app.key) { onDispose { bounds.remove(app.key) } }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val shadowPx = with(density) { LIFT_SHADOW.toPx() }
    // Desplazamiento animado: reacomodo de la cuadrícula y caída al soltar.
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val lastPos = remember { PositionHolder() }
    var rootCenter by remember { mutableStateOf(Offset.Zero) }
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "lift",
    )
    val isDragging by rememberUpdatedState(dragging)
    val currentPointer by rememberUpdatedState(dragPointer)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragBy by rememberUpdatedState(onDragBy)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        Modifier
            .size(cellSize)
            .zIndex(if (dragging || lift > 0.01f) 1f else 0f)
            .onGloballyPositioned {
                val r = it.boundsInRoot()
                bounds[app.key] = r
                rootCenter = r.center
            }
            // Cuando la burbuja cambia de lugar en la cuadrícula, se anima desde el sitio anterior (FLIP).
            .onPlaced { coords ->
                val p = coords.positionInParent()
                val prev = lastPos.pos
                lastPos.pos = p
                if (prev != null && !isDragging && (prev - p).getDistance() > 2f) {
                    scope.launch {
                        offset.snapTo(offset.value + (prev - p))
                        offset.animateTo(
                            Offset.Zero,
                            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                }
            }
            // Los gestos van antes de graphicsLayer para que la traslación no altere el arrastre.
            .pointerInput(app.key) {
                var total = Offset.Zero
                detectDragGesturesAfterLongPress(
                    onDragStart = { total = Offset.Zero; currentOnDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        total += amount
                        currentOnDragBy(amount)
                    },
                    onDragEnd = {
                        val moved = total.getDistance() > TAP_SLOP_PX
                        // Caída con rebote desde donde quedó el dedo hasta su lugar.
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            offset.snapTo(currentPointer() - rootCenter)
                            offset.animateTo(Offset.Zero, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
                        }
                        currentOnDragEnd(moved)
                        if (!moved) menu = true
                    },
                    onDragCancel = {
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            offset.snapTo(currentPointer() - rootCenter)
                            offset.animateTo(Offset.Zero, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
                        }
                        currentOnDragEnd(false)
                    },
                )
            }
            .clickable { onLaunch(app) }
            .graphicsLayer {
                val t = if (dragging) dragPointer() - rootCenter else offset.value + tilt()
                translationX = t.x
                translationY = t.y
                val s = 1f + LIFT_SCALE * lift
                scaleX = s
                scaleY = s
                shadowElevation = shadowPx * lift
                this.shape = shape
                clip = false
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(iconSize)) {
            Box(Modifier.fillMaxSize().clip(shape).background(Color.White.copy(alpha = 0.12f))) {
                icon?.let {
                    IconImage(it, app.label, iconZoom, iconPadding)
                }
            }
            NotificationBadge(badgeCountFor(app, LocalBadgeCounts.current), iconSize)
        }
        AppContextMenu(menu, { menu = false }, app, actions, isFavorite = true, onToggleFavorite = { onRemove(app) })
    }
}
