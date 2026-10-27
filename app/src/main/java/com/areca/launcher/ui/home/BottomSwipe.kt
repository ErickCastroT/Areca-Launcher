package com.areca.launcher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/**
 * Amplía la zona del gesto de Android "deslizar hacia arriba para ver las apps abiertas": la zona del
 * sistema mide ~20 dp, y un deslizamiento que empieza un poco más arriba movería el panal. Aquí, un
 * deslizamiento rápido hacia arriba que empieza en los [bandHeightPx] inferiores dispara [onTrigger]
 * y no llega al contenido. Un toque, o un arrastre lento (reordenar), sigue su camino normal.
 */
fun Modifier.bottomSwipeUp(enabled: Boolean, bandHeightPx: Float, triggerDistance: Dp, onTrigger: () -> Unit): Modifier {
    if (!enabled) return this
    return pointerInput(bandHeightPx) {
        val triggerPx = triggerDistance.toPx()
        awaitEachGesture {
            // Pase Initial: se decide antes de que el panal o un widget vean el movimiento.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.y < size.height - bandHeightPx) return@awaitEachGesture

            val slop = viewConfiguration.touchSlop
            val quickMs = viewConfiguration.longPressTimeoutMillis
            var dx = 0f
            var dy = 0f
            var claimed = false
            var fired = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                val delta = change.positionChange() // se lee antes de consumir
                if (!change.pressed) return@awaitEachGesture
                dx += delta.x
                dy += delta.y
                if (!claimed) {
                    val quick = change.uptimeMillis - down.uptimeMillis < quickMs
                    when {
                        // Lento o hacia los lados: no es este gesto; el contenido lo gestiona.
                        !quick || abs(dx) > 2 * slop -> return@awaitEachGesture
                        -dy > slop && -dy > abs(dx) * 1.5f -> claimed = true
                    }
                }
                if (claimed) {
                    change.consume()
                    if (!fired && -dy >= triggerPx) {
                        fired = true
                        onTrigger()
                    }
                }
            }
        }
    }
}
