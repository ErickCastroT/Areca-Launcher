package com.areca.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.areca.launcher.R
import com.areca.launcher.ui.HomeViewModel
import kotlinx.coroutines.withTimeoutOrNull

/** Arrastre de un widget para reordenarlo; lo mueve la pantalla que lo contiene. */
class WidgetDrag(val onStart: () -> Unit, val onMove: (dy: Float) -> Unit, val onEnd: () -> Unit)

/**
 * Un widget de otra app dentro del escritorio. La pulsación larga se detecta aquí, antes que el widget
 * (muchos widgets capturan sus toques), y abre el menú para moverlo, cambiar su alto, configurarlo o quitarlo.
 */
@Composable
fun WidgetView(vm: HomeViewModel, id: Int, width: Dp, height: Dp, modifier: Modifier = Modifier, drag: WidgetDrag? = null) {
    val info = remember(id) { vm.widgets.info(id) }
    val haptic = LocalHapticFeedback.current
    val currentDrag by rememberUpdatedState(drag)

    Box(
        modifier.pointerInput(id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var moved = 0f
                val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                        if (!change.pressed) return@withTimeoutOrNull true
                        moved += change.positionChange().getDistance()
                        if (moved > viewConfiguration.touchSlop) return@withTimeoutOrNull true
                    }
                    @Suppress("UNREACHABLE_CODE") false
                }
                if (released == null) {
                    // Pulsación larga: arrastrar reordena el widget; soltar sin mover abre su menú.
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    var dragging = false
                    var total = 0f
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        val dy = change.positionChange().y // se lee antes de consumir
                        change.consume()
                        if (!change.pressed) break
                        total += dy
                        val d = currentDrag
                        if (d != null && !dragging && kotlin.math.abs(total) > viewConfiguration.touchSlop) {
                            dragging = true
                            d.onStart()
                            d.onMove(total)
                        } else if (dragging && d != null) d.onMove(dy)
                    }
                    if (dragging) currentDrag?.onEnd() else vm.openWidgetMenu(id)
                }
            }
        },
    ) {
        if (info == null) {
            // El proveedor se desinstaló: se ofrece quitarlo con la pulsación larga.
            Text(
                stringResource(R.string.widgets_unavailable),
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                factory = { context -> vm.widgets.createView(context, id) ?: AppWidgetHostView(context) },
                update = { view ->
                    val w = width.value.toInt()
                    val h = height.value.toInt()
                    view.updateAppWidgetSize(null, w, h, w, h)
                },
            )
        }
    }
}
