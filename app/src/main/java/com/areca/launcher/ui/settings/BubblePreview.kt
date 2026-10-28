package com.areca.launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areca.launcher.ui.IconImage
import com.areca.launcher.data.zoomFor
import com.areca.launcher.data.AppInfo
import com.areca.launcher.data.GridLayout
import com.areca.launcher.data.LauncherSettings
import com.areca.launcher.ui.HomeViewModel
import com.areca.launcher.ui.home.fisheye
import com.areca.launcher.ui.theme.toShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PITCH = 0.866f

/**
 * Muestra en vivo cómo quedarán las burbujas con los ajustes actuales (tamaño, separación,
 * forma, relleno, disposición e icon pack), con las mismas reglas que el escritorio.
 */
@Composable
fun BubblePreview(vm: HomeViewModel, settings: LauncherSettings, modifier: Modifier = Modifier) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val all by vm.visibleApps.collectAsStateWithLifecycle()
    val sample = remember(favorites, all) { favorites.ifEmpty { all }.take(7) }
    val shape = remember(settings.iconShape) { settings.iconShape.toShape() }
    val honeycomb = settings.layout == GridLayout.HONEYCOMB
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val cell = with(density) { (settings.iconSizeDp.dp + settings.spacingDp.dp).toPx() }
        val iconPx = with(density) { settings.iconSizeDp.dp.toPx() }
        val margin = with(density) { 14.dp.toPx() }

        val slots = remember(sample.size, cell, honeycomb, settings.columns) {
            previewSlots(sample.size, cell, honeycomb, settings.columns)
        }
        // Reduce todo por igual para que el conjunto quepa en la vista previa.
        val extentX = (slots.maxOfOrNull { abs(it.x) } ?: 0f) + cell / 2
        val extentY = (slots.maxOfOrNull { abs(it.y) } ?: 0f) + cell / 2
        val fit = min(1f, min((w / 2 - margin) / max(extentX, 1f), (h / 2 - margin) / max(extentY, 1f)))

        sample.forEachIndexed { index, app ->
            val rel = slots[index] * fit
            // El panal usa la misma lupa que el escritorio; las hileras no.
            val fish = if (honeycomb) fisheye(rel, w / 2, h * 0.38f) else null
            val scale = fish?.scale ?: 1f
            val position = fish?.offset ?: rel
            val sizeDp = with(density) { (iconPx * fit * scale).toDp() }
            PreviewBubble(
                app = app,
                vm = vm,
                settings = settings,
                shape = shape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                    .size(sizeDp)
                    .graphicsLayer { alpha = fish?.alpha ?: 1f },
            )
        }
    }
}

@Composable
private fun PreviewBubble(
    app: AppInfo,
    vm: HomeViewModel,
    settings: LauncherSettings,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier,
) {
    val override = settings.iconOverrides[app.key]
    val icon by produceState<ImageBitmap?>(
        vm.iconLoader.cached(app, settings.iconPack, override), app, settings.iconPack, override, vm.iconLoader.styleKey,
    ) {
        value = withContext(Dispatchers.IO) { vm.iconLoader.load(app, settings.iconPack, override) }
    }
    BoxWithConstraints(modifier.clip(shape).background(Color.White.copy(alpha = 0.12f))) {
        icon?.let {
            IconImage(it, null, settings.zoomFor(app.key), maxWidth * settings.iconPaddingPercent / 100)
        }
    }
}

/** Posiciones respecto al centro: racimo hexagonal (panal) o filas rectas de [columns]. */
private fun previewSlots(count: Int, cell: Float, honeycomb: Boolean, columns: Int): List<Offset> {
    if (honeycomb) {
        val axial = listOf(0 to 0, 1 to 0, 0 to 1, -1 to 1, -1 to 0, 0 to -1, 1 to -1)
        return axial.take(count).map { (q, r) -> Offset(cell * (q + r / 2f), cell * PITCH * r) }
    }
    val rows = (count + columns - 1) / columns
    return (0 until count).map { i ->
        val row = i / columns
        val inRow = min(columns, count - row * columns)
        Offset((i % columns - (inRow - 1) / 2f) * cell, (row - (rows - 1) / 2f) * cell)
    }
}
