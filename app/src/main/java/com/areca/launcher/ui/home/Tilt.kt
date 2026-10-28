package com.areca.launcher.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Desplazamiento en px que produce la inclinación del teléfono; (0, 0) si el efecto está apagado. */
val LocalTilt = compositionLocalOf<() -> Offset> { { Offset.Zero } }

/** ¿Tiene el teléfono acelerómetro? (No hace falta ningún permiso para leerlo.) */
fun hasAccelerometer(context: Context): Boolean =
    context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

/** Con las animaciones del sistema apagadas (accesibilidad) el efecto no se aplica. */
private fun animationsDisabled(context: Context) =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Lee el acelerómetro mientras la pantalla está visible ([active]) y devuelve cuánto deben desplazarse las burbujas,
 * hasta [maxPx] en cada eje. Se mide contra una posición de reposo que se va adaptando (unos 2 s), así que sostener
 * el teléfono en cualquier ángulo lo deja centrado y solo el movimiento al inclinarlo desplaza las burbujas: se
 * desplazan en sentido contrario a la inclinación, como un fondo con paralaje.
 * El valor es un estado que solo se lee al dibujar, así que no provoca recomposiciones.
 */
@Composable
fun rememberTilt(enabled: Boolean, active: Boolean, maxPx: Float): () -> Offset {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled, active, maxPx, lifecycle) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || !active || sensor == null || maxPx <= 0f || animationsDisabled(context)) {
            tilt.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val windowManager = context.getSystemService(WindowManager::class.java)
        var baseX = 0f
        var baseY = 0f
        var smoothX = 0f
        var smoothY = 0f
        var started = false
        val listener = object : SensorEventListener {
            @Suppress("DEPRECATION")
            override fun onSensorChanged(event: SensorEvent) {
                // Los ejes del sensor son los del teléfono; se pasan a los de la pantalla según la rotación.
                val g = SensorManager.GRAVITY_EARTH
                val dx = event.values[0] / g
                val dy = event.values[1] / g
                val (sx, sy) = when (windowManager?.defaultDisplay?.rotation) {
                    Surface.ROTATION_90 -> dy to -dx
                    Surface.ROTATION_180 -> -dx to -dy
                    Surface.ROTATION_270 -> -dy to dx
                    else -> dx to dy
                }
                if (!started) { baseX = sx; baseY = sy; started = true }
                baseX += (sx - baseX) * BASE_ALPHA
                baseY += (sy - baseY) * BASE_ALPHA
                // Inclinar el borde derecho hacia abajo da x negativo: las burbujas se desplazan hacia la izquierda
                // (contra la inclinación, como un fondo con paralaje); al enderezar el teléfono (y sube) suben.
                smoothX += ((sx - baseX) - smoothX) * SMOOTH_ALPHA
                smoothY += (-(sy - baseY) - smoothY) * SMOOTH_ALPHA
                tilt.value = Offset(
                    (smoothX * GAIN).coerceIn(-1f, 1f) * maxPx,
                    (smoothY * GAIN).coerceIn(-1f, 1f) * maxPx,
                )
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var registered = false
        fun register() {
            if (!registered) {
                started = false
                registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        fun unregister() {
            if (registered) { manager.unregisterListener(listener); registered = false }
            tilt.value = Offset.Zero
        }
        // Solo escucha con la pantalla en primer plano.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> register()
                Lifecycle.Event.ON_STOP -> unregister()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
        onDispose {
            lifecycle.removeObserver(observer)
            unregister()
        }
    }
    return { tilt.value }
}

/** Con el teléfono a 30° del reposo (sin 30° = 0,5) el desplazamiento ya es el máximo. */
private const val GAIN = 2f
private const val BASE_ALPHA = 0.01f
private const val SMOOTH_ALPHA = 0.15f
