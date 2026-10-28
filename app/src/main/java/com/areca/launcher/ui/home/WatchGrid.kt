package com.areca.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.areca.launcher.R
import com.areca.launcher.ui.IconImage
import com.areca.launcher.data.zoomFor
import com.areca.launcher.ui.widgets.WidgetDrag
import com.areca.launcher.data.AppInfo
import com.areca.launcher.data.IconLoader
import com.areca.launcher.data.LauncherSettings
import com.areca.launcher.ui.AppContextMenu
import com.areca.launcher.ui.AppMenuActions
import com.areca.launcher.ui.theme.toShape
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val HEX_ROW_PITCH = 0.866f
private const val SWAP_RADIUS = 0.45f
private const val DOUBLE_TAP_MS = 320L
private const val PULL_TOLERANCE_PX = 6f
private val WIDGET_GAP = 12.dp
private val WIDGET_MARGIN = 16.dp

/** En pantallas anchas (tablets, horizontal) los widgets no se estiran a todo el ancho. */
private val WIDGET_MAX_WIDTH = 480.dp

private enum class Phase { LONG_PRESS, TAP, DRAG, CANCELLED }

/** Medidas del contenido (burbujas + widgets) respecto al centro de la pantalla. */
private class WatchLayout(
    val slots: List<Offset>,
    val widgetCenters: List<Float>,
    val widgetHeightsPx: List<Float>,
    val widgetHalfWidthsPx: List<Float>,
    val snapPans: List<Float>,
    val homePanY: Float,
    val maxPanY: Float,
    val boundY: Float,
)
private const val RESISTANCE = 0.35f
private const val AUTOPAN_MAX_PX = 16f
private val AUTOPAN_EDGE = 96.dp
private val LIFT_SHADOW = 28.dp

/** Resultado del efecto lupa para una burbuja: posición, escala y opacidad. */
internal data class Fish(val offset: Offset, val scale: Float, val alpha: Float)

/**
 * Efecto "lupa" estilo Apple Watch: las burbujas del centro son más grandes, las de los
 * bordes se achican, se juntan y se atenúan. [rel] es la posición respecto al centro.
 */
internal fun fisheye(rel: Offset, rx: Float, ry: Float): Fish {
    val d = hypot(rel.x / max(rx, 1f), rel.y / max(ry, 1f))
    val t = (d / 1.25f).coerceIn(0f, 1f)
    val scale = 1.12f - 0.84f * t.pow(1.4f)
    val warp = 1f - 0.28f * t * t
    val alpha = 1f - 0.4f * t.pow(3f)
    return Fish(rel * warp, scale, alpha)
}

/**
 * Posiciones del panal por filas (pares con [columns] burbujas, impares con columns-1), cada fila
 * centrada horizontalmente y el conjunto centrado verticalmente respecto al origen.
 */
private fun rowSlots(count: Int, columns: Int, cell: Float): List<Offset> {
    val rowSizes = ArrayList<Int>()
    var i = 0
    while (i < count) {
        val n = min(count - i, if (rowSizes.size % 2 == 1) max(1, columns - 1) else columns)
        rowSizes += n
        i += n
    }
    val pitch = cell * HEX_ROW_PITCH
    val out = ArrayList<Offset>(count)
    rowSizes.forEachIndexed { row, n ->
        val y = (row - (rowSizes.size - 1) / 2f) * pitch
        repeat(n) { c -> out += Offset((c - (n - 1) / 2f) * cell, y) }
    }
    return out
}

private fun rubber(value: Float, delta: Float, bound: Float, slack: Float): Float {
    val resisting = abs(value) > bound && (value > 0) == (delta > 0)
    return (value + delta * if (resisting) RESISTANCE else 1f).coerceIn(-bound - slack, bound + slack)
}

/**
 * Panal estilo smartwatch: se desplaza en vertical con inercia y se asienta en la fila más
 * cercana; efecto lupa según la distancia al centro. Mantener pulsado y arrastrar
 * reordena (las demás se reacomodan en vivo); mantener sin mover abre el menú.
 */
@Composable
fun WatchGrid(
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
    recenterSignal: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val iconSize = settings.iconSizeDp.dp
    val shadowPx = with(density) { LIFT_SHADOW.toPx() }
    val iconPx = with(density) { iconSize.toPx() }
    val cellPx = with(density) { (iconSize + settings.spacingDp.dp).toPx() }
    val shape = remember(settings.iconShape) { settings.iconShape.toShape() }

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
    var size by remember { mutableStateOf(IntSize.Zero) }
    val baseSlots = remember(items.size, cellPx, settings.columns) { rowSlots(items.size, settings.columns, cellPx) }

    // Los widgets van arriba de las burbujas y se desplazan con ellas; el conjunto queda centrado.
    val gapPx = with(density) { WIDGET_GAP.toPx() }
    // Orden de arriba abajo: widgets de arriba, burbujas, widgets de abajo.
    // Orden local para reacomodar los widgets al instante mientras se arrastran; se guarda al soltar.
    var localWidgetIds by remember(widgetIds) { mutableStateOf(widgetIds) }
    val topIds = localWidgetIds.filter { it !in bottomWidgetIds }
    val orderedIds = topIds + localWidgetIds.filter { it in bottomWidgetIds }
    val widgetHeightsDp = orderedIds.map(widgetHeight)
    val maxWidgetWidth = (with(density) { size.width.toDp() } - WIDGET_MARGIN * 2).coerceIn(0.dp, WIDGET_MAX_WIDTH)
    val widgetWidthsDp = orderedIds.map { id -> minOf(maxWidgetWidth, widgetWidth(id) ?: maxWidgetWidth) }
    val widgetHeightsPx = widgetHeightsDp.map { with(density) { it.toPx() } }
    fun stack(heights: List<Float>) = if (heights.isEmpty()) 0f else heights.sum() + gapPx * (heights.size - 1)
    val topHeights = widgetHeightsPx.take(topIds.size)
    val bottomHeights = widgetHeightsPx.drop(topIds.size)
    val topTotal = stack(topHeights)
    val bottomTotal = stack(bottomHeights)
    val bubbleExtent = if (baseSlots.isEmpty()) 0f else (baseSlots.maxOf { it.y } - baseSlots.minOf { it.y }) + cellPx
    val gapTop = if (topIds.isEmpty() || baseSlots.isEmpty()) 0f else cellPx * 0.4f
    val gapBottom = if (bottomHeights.isEmpty() || baseSlots.isEmpty()) 0f else cellPx * 0.4f
    val totalExtent = topTotal + gapTop + bubbleExtent + gapBottom + bottomTotal
    val contentTop = -totalExtent / 2f
    val bubblesCenterY = contentTop + topTotal + gapTop + bubbleExtent / 2f
    val slots = remember(baseSlots, bubblesCenterY) { baseSlots.map { it + Offset(0f, bubblesCenterY) } }
    val widgetCenters = run {
        fun centers(start: Float, heights: List<Float>): List<Float> {
            var y = start
            return heights.map { h -> (y + h / 2f).also { y += h + gapPx } }
        }
        centers(contentTop, topHeights) + centers(contentTop + topTotal + gapTop + bubbleExtent + gapBottom, bottomHeights)
    }
    val snapYs = (slots.map { it.y } + widgetCenters).distinct()
    // Desplazamientos (pan) límite y de reposo. Con widgets arriba/abajo, el contenido arranca alineado
    // a su borde superior (y termina en el inferior) en vez de centrado, para que se vean los widgets.
    val viewH = size.height.toFloat()
    val marginPx = with(density) { 24.dp.toPx() }
    val fits = totalExtent <= viewH - 2 * marginPx
    val topPan = if (topIds.isEmpty() || fits) 0f else -contentTop - viewH / 2f + marginPx
    val bottomPan = if (bottomHeights.isEmpty() || fits) 0f else -(contentTop + totalExtent) + viewH / 2f - marginPx
    val maxPan = if (topIds.isNotEmpty()) topPan else -(snapYs.minOrNull() ?: 0f)
    val minPan = if (bottomHeights.isNotEmpty()) bottomPan else -(snapYs.maxOrNull() ?: 0f)
    val lo = minOf(minPan, maxPan)
    val hi = maxOf(minPan, maxPan)
    val snapPans = ((snapYs.map { -it } + topPan + bottomPan).map { it.coerceIn(lo, hi) }).distinct()
    val layout = WatchLayout(
        slots = slots,
        widgetCenters = widgetCenters,
        widgetHeightsPx = widgetHeightsPx,
        widgetHalfWidthsPx = widgetWidthsDp.map { with(density) { it.toPx() } / 2f },
        snapPans = snapPans,
        homePanY = if (topIds.isNotEmpty()) topPan else 0f,
        maxPanY = hi,
        boundY = maxOf(abs(lo), abs(hi)),
    )
    // Los gestos se registran una sola vez; esto evita que lean una disposición vieja.
    val currentLayout by rememberUpdatedState(layout)
    val pan = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragRel by remember { mutableStateOf(Offset.Zero) } // posición del dedo respecto al centro
    var menuKey by remember { mutableStateOf<String?>(null) }
    val animJob = remember { arrayOfNulls<Job>(1) }

    // Arrastre de un widget para reordenarlo (solo entre los de su mismo lado de las burbujas).
    var draggingWidget by remember { mutableStateOf<Int?>(null) }
    val widgetDragY = remember { Animatable(0f) }
    val swapGuard = remember { arrayOfNulls<Any>(1) } // evita un segundo cambio antes de que se recalcule la disposición
    val currentOnWidgetOrder by rememberUpdatedState(onWidgetOrder)
    fun dragCallbacks(id: Int) = WidgetDrag(
        onStart = { draggingWidget = id; scope.launch { widgetDragY.snapTo(0f) } },
        onMove = { dy ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) { widgetDragY.snapTo(widgetDragY.value + dy) }
            val l = currentLayout
            if (swapGuard[0] !== l) {
                val order = (localWidgetIds.filter { it !in bottomWidgetIds } + localWidgetIds.filter { it in bottomWidgetIds })
                val i = order.indexOf(id)
                val center = l.widgetCenters[i] + widgetDragY.value
                val bottomGroup = id in bottomWidgetIds
                val j = when {
                    i > 0 && (order[i - 1] in bottomWidgetIds) == bottomGroup && center < l.widgetCenters[i - 1] -> i - 1
                    i < order.lastIndex && (order[i + 1] in bottomWidgetIds) == bottomGroup && center > l.widgetCenters[i + 1] -> i + 1
                    else -> i
                }
                if (j != i) {
                    // El widget que se salta ocupa su alto más el hueco; se compensa para que siga bajo el dedo.
                    val hi = l.widgetHeightsPx[i]
                    val hj = l.widgetHeightsPx[j]
                    val newCenter = if (j < i) l.widgetCenters[j] - hj / 2f + hi / 2f else l.widgetCenters[j] + hj / 2f - hi / 2f
                    val shift = newCenter - l.widgetCenters[i]
                    val reordered = order.toMutableList().apply { add(j, removeAt(i)) }
                    // Se conserva el orden relativo entre arriba y abajo: solo cambia el de este grupo.
                    localWidgetIds = reordered
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { widgetDragY.snapTo(widgetDragY.value - shift) }
                    swapGuard[0] = l
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        },
        onEnd = {
            currentOnWidgetOrder(localWidgetIds)
            scope.launch {
                widgetDragY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
                draggingWidget = null
            }
        },
    )

    val boundX = 0f // solo se mueve en vertical
    val boundY = layout.boundY
    val slack = cellPx * 0.6f
    LaunchedEffect(boundX, boundY, slack) {
        pan.updateBounds(Offset(-boundX - slack, -boundY - slack), Offset(boundX + slack, boundY + slack))
    }
    // Al abrir (y con el botón Home) el contenido vuelve a su posición inicial.
    LaunchedEffect(recenterSignal, size) {
        if (size != IntSize.Zero) {
            pan.animateTo(Offset(0f, currentLayout.homePanY), spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
        }
    }

    fun fishAt(index: Int) = fisheye(currentLayout.slots[index] + pan.value, size.width / 2f, size.height * 0.38f)

    /** Cuántas burbujas tienen casilla calculada; evita desbordes durante el instante en que cambia la lista. */
    fun placed() = minOf(items.size, currentLayout.slots.size)

    fun widgetAt(rel: Offset): Int? {
        val l = currentLayout
        for (i in l.widgetCenters.indices) {
            val cy = l.widgetCenters[i] + pan.value.y
            if (abs(rel.y - cy) < l.widgetHeightsPx[i] / 2f && abs(rel.x) < l.widgetHalfWidthsPx[i]) return i
        }
        return null
    }

    fun hitTest(rel: Offset): Int? {
        var best: Int? = null
        var bestDist = Float.MAX_VALUE
        for (i in 0 until placed()) {
            val f = fishAt(i)
            val dist = (rel - f.offset).getDistance()
            if (dist < iconPx / 2f * f.scale * 1.15f && dist < bestDist) {
                best = i
                bestDist = dist
            }
        }
        return best
    }

    fun swapIfOverTarget() {
        val key = dragKey ?: return
        val from = items.indexOfFirst { it.key == key }
        if (from < 0) return
        for (i in items.indices) {
            if (i == from) continue
            val f = fishAt(i)
            if ((dragRel - f.offset).getDistance() < cellPx * f.scale * SWAP_RADIUS) {
                val list = items.toMutableList()
                list.add(i, list.removeAt(from))
                items = list
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                return
            }
        }
    }

    // Desplaza el panal solo cuando se arrastra una burbuja hacia el borde de la pantalla.
    LaunchedEffect(dragKey) {
        if (dragKey == null) return@LaunchedEffect
        val edge = with(density) { AUTOPAN_EDGE.toPx() }
        while (true) {
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            val pos = dragRel + Offset(w / 2f, h / 2f)
            val dx = 0f
            val dy = when {
                pos.y < edge -> (edge - pos.y) / edge
                pos.y > h - edge -> -(pos.y - (h - edge)) / edge
                else -> 0f
            } * AUTOPAN_MAX_PX
            if (dx != 0f || dy != 0f) {
                pan.snapTo(pan.value + Offset(dx, dy))
                swapIfOverTarget()
            }
            delay(16)
        }
    }

    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnEmptyLongPress by rememberUpdatedState(onEmptyLongPress)
    val currentOnDoubleTapEmpty by rememberUpdatedState(onDoubleTapEmpty)
    val currentOnPullDown by rememberUpdatedState(onPullDown)
    val lastEmptyTap = remember { LongArray(1) } // instante del último toque en vacío

    /** Reordena una burbuja sin arrastrar (acción de accesibilidad). */
    fun moveItem(index: Int, delta: Int) {
        val to = (index + delta).coerceIn(0, items.lastIndex)
        if (to == index) return
        val list = items.toMutableList().apply { add(to, removeAt(index)) }
        items = list
        currentOnReorder(list.map { it.key })
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Pase Initial: el panal decide antes que un widget si el movimiento es un arrastre vertical.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    animJob[0]?.cancel()
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { pan.stop() }
                    menuKey = null
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val rel0 = down.position - center
                    val startedAtTop = pan.value.y >= currentLayout.maxPanY - PULL_TOLERANCE_PX
                    val slopPx = viewConfiguration.touchSlop

                    // Hasta la pulsación larga: esperar a que el dedo se suelte o se mueva más que el umbral.
                    var phase = Phase.LONG_PRESS
                    var moved = Offset.Zero
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                phase = Phase.CANCELLED
                                return@withTimeoutOrNull
                            }
                            if (!change.pressed) {
                                phase = Phase.TAP
                                return@withTimeoutOrNull
                            }
                            moved += change.positionChange()
                            if (moved.getDistance() > slopPx) {
                                phase = Phase.DRAG
                                return@withTimeoutOrNull
                            }
                        }
                    }

                    when (phase) {
                        Phase.CANCELLED -> return@awaitEachGesture

                        // Pulsación larga: reordenar (o menú si no se mueve).
                        Phase.LONG_PRESS -> {
                            if (widgetAt(rel0) != null) return@awaitEachGesture // el widget gestiona su propia pulsación larga
                            val index = hitTest(rel0)
                            if (index == null) {
                                // Espacio vacío: abre el menú del escritorio.
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnEmptyLongPress()
                                return@awaitEachGesture
                            }
                            val key = items[index].key
                            dragKey = key
                            dragRel = rel0
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            var total = Offset.Zero
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    change.consume()
                                    total += delta
                                    dragRel += delta
                                    swapIfOverTarget()
                                }
                            }
                            dragKey = null
                            if (total.getDistance() > slopPx) currentOnReorder(items.map { it.key }) else menuKey = key
                        }

                        // Toque: abrir la app bajo el dedo, o contar un doble toque en un espacio vacío.
                        Phase.TAP -> {
                            if (widgetAt(rel0) != null) return@awaitEachGesture
                            val hit = hitTest(rel0)
                            if (hit != null) {
                                currentOnLaunch(items[hit])
                            } else {
                                val now = down.uptimeMillis
                                if (now - lastEmptyTap[0] < DOUBLE_TAP_MS) {
                                    lastEmptyTap[0] = 0L
                                    currentOnDoubleTapEmpty()
                                } else lastEmptyTap[0] = now
                            }
                        }

                        Phase.DRAG -> {
                            // Los swipes horizontales quedan para el paginador (no se consumen).
                            if (abs(moved.y) <= abs(moved.x)) return@awaitEachGesture

                            if (startedAtTop && moved.y > 0f) {
                                // Ya estaba al principio y se sigue tirando hacia abajo: baja la barra de notificaciones.
                                currentOnPullDown()
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                }
                                return@awaitEachGesture
                            }

                            // Mover el panal con inercia y asentarlo en la fila (o widget) más cercana.
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var current = Offset(0f, rubber(pan.value.y, moved.y, currentLayout.boundY, slack))
                            scope.launch(start = CoroutineStart.UNDISPATCHED) { pan.snapTo(current) }
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val d = change.positionChange() // antes de consumir: tras consume() devuelve cero
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                current = Offset(0f, rubber(current.y, d.y, currentLayout.boundY, slack))
                                scope.launch(start = CoroutineStart.UNDISPATCHED) { pan.snapTo(current) }
                            }
                            val v = tracker.calculateVelocity().let { Offset(0f, it.y) }
                            animJob[0] = scope.launch {
                                val decay = exponentialDecay<Offset>(frictionMultiplier = 1.6f)
                                val projected = decay.calculateTargetValue(Offset.VectorConverter, pan.value, v)
                                val target = currentLayout.snapPans.minByOrNull { abs(projected.y - it) } ?: 0f
                                pan.animateTo(
                                    Offset(0f, target),
                                    spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                                    initialVelocity = v,
                                )
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        orderedIds.forEachIndexed { i, id ->
            key(id) {
                val cy = widgetCenters[i]
                val lifted = draggingWidget == id
                widget(
                    id,
                    Modifier.size(widgetWidthsDp[i], widgetHeightsDp[i])
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer {
                            translationY = cy + pan.value.y + if (lifted) widgetDragY.value else 0f
                            val s = if (lifted) 1.03f else 1f
                            scaleX = s
                            scaleY = s
                            shadowElevation = if (lifted) shadowPx else 0f
                        },
                    widgetWidthsDp[i],
                    widgetHeightsDp[i],
                    if (id in widgetIds) dragCallbacks(id) else null,
                )
            }
        }
        // Del borde al centro, para que las burbujas centrales (más grandes) se dibujen encima.
        for (index in items.indices.reversed()) {
            val app = items[index]
            key(app.key) {
                WatchBubble(
                    app = app,
                    slotTarget = slots[index],
                    iconSize = iconSize,
                    iconPadding = iconSize * settings.iconPaddingPercent / 100,
                    iconZoom = settings.zoomFor(app.key),
                    iconPack = settings.iconPack,
                    iconOverride = settings.iconOverrides[app.key],
                    shape = shape,
                    iconLoader = iconLoader,
                    pan = { pan.value },
                    viewSize = { size },
                    dragging = dragKey == app.key,
                    dragRel = { dragRel },
                    menuOpen = menuKey == app.key,
                    onOpenMenu = { menuKey = app.key },
                    onDismissMenu = { menuKey = null },
                    onLaunch = { currentOnLaunch(app) },
                    onMove = { delta -> moveItem(index, delta) },
                    position = index,
                    count = items.size,
                    onRemove = onRemove,
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun WatchBubble(
    app: AppInfo,
    slotTarget: Offset,
    iconSize: Dp,
    iconPadding: Dp,
    iconZoom: Int,
    iconPack: String,
    iconOverride: String?,
    shape: Shape,
    iconLoader: IconLoader,
    pan: () -> Offset,
    viewSize: () -> IntSize,
    dragging: Boolean,
    dragRel: () -> Offset,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onLaunch: () -> Unit,
    onMove: (Int) -> Unit,
    position: Int,
    count: Int,
    onRemove: (AppInfo) -> Unit,
    actions: AppMenuActions,
) {
    val density = LocalDensity.current
    val shadowPx = with(density) { LIFT_SHADOW.toPx() }
    val openLabel = stringResource(R.string.a11y_open)
    val optionsLabel = stringResource(R.string.a11y_app_options)
    val earlierLabel = stringResource(R.string.a11y_move_earlier)
    val laterLabel = stringResource(R.string.a11y_move_later)
    val icon by produceState<ImageBitmap?>(iconLoader.cached(app, iconPack, iconOverride), app, iconPack, iconOverride, iconLoader.styleKey) {
        value = withContext(Dispatchers.IO) { iconLoader.load(app, iconPack, iconOverride) }
    }

    // Reacomodo: cada burbuja se desliza con un resorte a su nueva casilla.
    val slot = remember { Animatable(slotTarget, Offset.VectorConverter) }
    LaunchedEffect(slotTarget) {
        slot.animateTo(slotTarget, spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow))
    }
    // Al levantarla sigue al dedo; al soltarla cae a su casilla con rebote.
    val blend = remember { Animatable(0f) }
    LaunchedEffect(dragging) {
        if (dragging) blend.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
        else blend.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
    }
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "lift",
    )

    Box(
        Modifier
            .size(iconSize)
            .zIndex(if (dragging || blend.value > 0.01f) 1f else 0f)
            // Los gestos del panal no los ve un lector de pantalla: se exponen como acciones de accesibilidad.
            .semantics(mergeDescendants = true) {
                onClick(label = openLabel) { onLaunch(); true }
                onLongClick(label = optionsLabel) { onOpenMenu(); true }
                customActions = buildList {
                    if (position > 0) add(CustomAccessibilityAction(earlierLabel) { onMove(-1); true })
                    if (position < count - 1) add(CustomAccessibilityAction(laterLabel) { onMove(1); true })
                }
            }
            .graphicsLayer {
                val sz = viewSize()
                val fish = fisheye(slot.value + pan(), sz.width / 2f, sz.height * 0.38f)
                val b = blend.value
                val rel = lerp(fish.offset, dragRel(), b)
                translationX = rel.x
                translationY = rel.y
                val s = lerp(fish.scale, 1.2f, b)
                scaleX = s
                scaleY = s
                alpha = lerp(fish.alpha, 1f, b)
                shadowElevation = shadowPx * lift
                this.shape = shape
                clip = false
            },
    ) {
        Box(Modifier.size(iconSize).clip(shape).background(Color.White.copy(alpha = 0.12f))) {
            icon?.let {
                IconImage(it, app.label, iconZoom, iconPadding)
            }
        }
        NotificationBadge(badgeCountFor(app, LocalBadgeCounts.current), iconSize)
        AppContextMenu(menuOpen, onDismissMenu, app, actions, isFavorite = true, onToggleFavorite = { onRemove(app) })
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
