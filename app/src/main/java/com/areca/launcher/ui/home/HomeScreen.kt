package com.areca.launcher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import com.areca.launcher.ui.widgets.WidgetView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areca.launcher.R
import com.areca.launcher.ui.HomeViewModel
import com.areca.launcher.ui.Screen
import com.areca.launcher.ui.apps.DrawerPage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Dos páginas: el escritorio (burbujas y widgets) y, deslizando a la izquierda, todas las apps instaladas. */
@Composable
fun HomeScreen(vm: HomeViewModel) {
    val pager = rememberPagerState(initialPage = HomeViewModel.HOME_PAGE) { 2 }
    val scope = rememberCoroutineScope()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { vm.setHomePage(it) }
    }
    LaunchedEffect(homeTick) {
        if (pager.currentPage != HomeViewModel.HOME_PAGE) pager.animateScrollToPage(HomeViewModel.HOME_PAGE)
    }
    BackHandler(enabled = pager.currentPage != HomeViewModel.HOME_PAGE) {
        scope.launch { pager.animateScrollToPage(HomeViewModel.HOME_PAGE) }
    }

    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
        if (page == HomeViewModel.HOME_PAGE) HomePage(vm, active = pager.currentPage == HomeViewModel.HOME_PAGE) else DrawerPage(vm)
    }
}

@Composable
private fun HomePage(vm: HomeViewModel, active: Boolean) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val homeTick by vm.homeTick.collectAsStateWithLifecycle()
    val badges by vm.badgeCounts.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val context = LocalContext.current
    val barPx = with(density) { BOTTOM_MARGIN.toPx() }
    val tilt = rememberTilt(
        enabled = settings.tiltEnabled,
        active = active,
        maxPx = with(density) { (TILT_MAX_DP * settings.tiltStrength / 100f).dp.toPx() },
    )

    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().displayCutoutPadding()) {
        val maxH = constraints.maxHeight.toFloat()

        if (favorites.isEmpty() && settings.widgetIds.isEmpty()) {
            Column(
                Modifier.align(Alignment.Center).clickable { vm.open(Screen.APPS) }.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    stringResource(R.string.home_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else CompositionLocalProvider(LocalBadgeCounts provides badges, LocalTilt provides tilt) {
            // Horizontal con widgets: los widgets van centrados en una columna y las burbujas en la otra.
            val twoColumns = maxWidth > maxHeight && settings.widgetIds.isNotEmpty()
            @Composable
            fun bubbles(widgetIds: List<Int>, modifier: Modifier) {
                BubbleGrid(
                    apps = favorites,
                    settings = settings,
                    iconLoader = vm.iconLoader,
                    onLaunch = vm::launch,
                    onRemove = vm::removeFavorite,
                    actions = vm.menuActions,
                    onReorder = vm::setFavoriteOrder,
                    onEmptyLongPress = vm::openHomeMenu,
                    onDoubleTapEmpty = { if (settings.doubleTapLock) vm.lockScreen() },
                    onPullDown = { if (settings.swipeDownNotifications) vm.openNotifications(context) },
                    widgetIds = widgetIds,
                    bottomWidgetIds = settings.widgetBottomIds,
                    widgetHeight = vm::widgetHeightDp,
                    widgetWidth = vm::widgetWidthDp,
                    onWidgetOrder = vm::setWidgetOrder,
                    widget = { id, mod, w, h, drag -> WidgetView(vm, id, w, h, mod, drag) },
                    modifier = modifier,
                    recenterSignal = homeTick,
                )
            }
            if (twoColumns) {
                Row(Modifier.fillMaxSize()) {
                    WidgetColumn(
                        vm = vm,
                        ids = settings.widgetIds,
                        onLongPress = vm::openHomeMenu,
                        onDoubleTap = { if (settings.doubleTapLock) vm.lockScreen() },
                        modifier = Modifier.weight(WIDGET_COLUMN_WEIGHT).fillMaxHeight(),
                    )
                    // Con las burbujas en su propia columna, el panal se centra en ese espacio.
                    bubbles(emptyList(), Modifier.weight(1f - WIDGET_COLUMN_WEIGHT).fillMaxHeight())
                }
            } else {
                bubbles(settings.widgetIds, Modifier.fillMaxSize())
            }
        }

        if (settings.showClock) {
            var clockH by remember { mutableIntStateOf(0) }
            var liveY by remember { mutableStateOf<Float?>(null) }
            LaunchedEffect(settings.clockY) { liveY = null }

            val maxY = (maxH - barPx - clockH).coerceAtLeast(0f)
            val restingY = (settings.clockY * maxH).coerceIn(0f, maxY)
            val y = liveY ?: restingY
            val currentY by rememberUpdatedState(y)
            val currentMaxY by rememberUpdatedState(maxY)

            ClockWidget(
                Modifier
                    .then(if (maxWidth > maxHeight && settings.widgetIds.isNotEmpty()) Modifier.fillMaxWidth(WIDGET_COLUMN_WEIGHT) else Modifier.fillMaxWidth())
                    .offset { IntOffset(0, y.roundToInt()) }
                    .onSizeChanged { clockH = it.height }
                    // Mantener pulsado y arrastrar para mover el widget arriba o abajo.
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { liveY = currentY },
                            onDrag = { change, amount ->
                                change.consume()
                                liveY = ((liveY ?: currentY) + amount.y).coerceIn(0f, currentMaxY)
                            },
                            onDragEnd = { liveY?.let { vm.setClockY(it / maxH) } },
                            onDragCancel = { liveY = null },
                        )
                    },
            )
        }
    }
}

private val BOTTOM_MARGIN = 16.dp

/** Desplazamiento máximo (a intensidad 100 %) de las burbujas al inclinar el teléfono. */
private const val TILT_MAX_DP = 18f

/** Parte del ancho que ocupa la columna de widgets en horizontal. */
private const val WIDGET_COLUMN_WEIGHT = 0.42f

/** Los widgets apilados y centrados; si no caben a lo alto, la columna se desplaza. */
@Composable
private fun WidgetColumn(vm: HomeViewModel, ids: List<Int>, onLongPress: () -> Unit, onDoubleTap: () -> Unit, modifier: Modifier = Modifier) {
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentDoubleTap by rememberUpdatedState(onDoubleTap)
    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = { currentLongPress() }, onDoubleTap = { currentDoubleTap() })
        },
    ) {
        val maxWidgetWidth = (maxWidth - 32.dp).coerceIn(0.dp, 480.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ids.forEach { id ->
                key(id) {
                    val width = minOf(maxWidgetWidth, vm.widgetWidthDp(id) ?: maxWidgetWidth)
                    val height = vm.widgetHeightDp(id)
                    WidgetView(vm, id, width, height, Modifier.size(width, height))
                }
            }
        }
    }
}
