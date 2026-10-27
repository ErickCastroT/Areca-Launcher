package com.areca.launcher.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.areca.launcher.ui.settings.SliderRow
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areca.launcher.R
import com.areca.launcher.ui.HomeViewModel

/** Acciones de un widget del escritorio. Mover y cambiar alto se pueden repetir sin cerrar el menú. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetMenuSheet(vm: HomeViewModel, id: Int) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val index = settings.widgetIds.indexOf(id)
    if (index < 0) { vm.closeWidgetMenu(); return }

    ModalBottomSheet(
        // Abre completa (en horizontal la mitad no alcanza) y el contenido se desplaza si aun así no cabe.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = vm::closeWidgetMenu,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            val below = id in settings.widgetBottomIds
            // Mover solo cuenta los widgets del mismo lado de las burbujas; arrastrar el widget hace lo mismo.
            val sameSide = settings.widgetIds.filter { (it in settings.widgetBottomIds) == below }
            val sidePos = sameSide.indexOf(id)
            if (sidePos > 0) Entry(stringResource(R.string.widget_move_up)) { vm.moveWidget(id, settings.widgetIds.indexOf(sameSide[sidePos - 1]) - index) }
            if (sidePos < sameSide.lastIndex) Entry(stringResource(R.string.widget_move_down)) { vm.moveWidget(id, settings.widgetIds.indexOf(sameSide[sidePos + 1]) - index) }
            Entry(stringResource(if (below) R.string.widget_place_above else R.string.widget_place_below)) {
                vm.toggleWidgetBottom(id)
                vm.closeWidgetMenu()
            }
            val maxWidth = minOf(LocalConfiguration.current.screenWidthDp - 32, 480).coerceAtLeast(HomeViewModel.MIN_WIDGET_WIDTH_DP + 10)
            SliderRow(
                label = stringResource(R.string.widget_height),
                valueText = stringResource(R.string.value_dp, vm.widgetHeightDp(id).value.toInt()),
                value = vm.widgetHeightDp(id).value,
                range = HomeViewModel.MIN_WIDGET_DP.toFloat()..HomeViewModel.MAX_WIDGET_DP.toFloat(),
                onChange = { vm.setWidgetHeight(id, snap(it)) },
            )
            SliderRow(
                label = stringResource(R.string.widget_width),
                valueText = stringResource(R.string.value_dp, (vm.widgetWidthDp(id)?.value?.toInt() ?: maxWidth)),
                value = (vm.widgetWidthDp(id)?.value ?: maxWidth.toFloat()).coerceAtMost(maxWidth.toFloat()),
                range = HomeViewModel.MIN_WIDGET_WIDTH_DP.toFloat()..maxWidth.toFloat(),
                onChange = { vm.setWidgetWidth(id, snap(it)) },
            )
            if (vm.canConfigureWidget(id)) {
                Entry(stringResource(R.string.widget_configure)) {
                    vm.closeWidgetMenu()
                    vm.configureWidget(id)
                }
            }
            Entry(stringResource(R.string.widgets_remove), danger = true) {
                vm.closeWidgetMenu()
                vm.removeWidget(id)
            }
        }
    }
}

/** Redondea a pasos de 10 dp para que los tamaños queden parejos. */
private fun snap(value: Float): Int = (value / 10f).roundToInt() * 10

@Composable
private fun Entry(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
    )
}
