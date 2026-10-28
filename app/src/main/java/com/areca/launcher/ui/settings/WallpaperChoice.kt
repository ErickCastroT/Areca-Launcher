package com.areca.launcher.ui.settings

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.areca.launcher.data.LauncherSettings
import com.areca.launcher.data.MAX_WALLPAPER_DIM
import com.areca.launcher.ui.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.areca.launcher.R
import com.areca.launcher.data.WallpaperMode

/** Pincel de un fondo: transparente (SYSTEM), liso o degradado vertical. */
fun WallpaperMode.brush(): Brush = when (colors.size) {
    0 -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    1 -> Brush.verticalGradient(listOf(Color(colors[0]), Color(colors[0])))
    else -> Brush.verticalGradient(colors.map { Color(it) })
}

private fun WallpaperMode.label() = when (this) {
    WallpaperMode.SYSTEM -> R.string.wallpaper_system
    WallpaperMode.CUSTOM -> R.string.wallpaper_custom
    WallpaperMode.BLACK -> R.string.wallpaper_black
    WallpaperMode.ARECA -> R.string.wallpaper_areca
    WallpaperMode.GRAPHITE -> R.string.wallpaper_graphite
    WallpaperMode.MIDNIGHT -> R.string.wallpaper_midnight
    WallpaperMode.WINE -> R.string.wallpaper_wine
    WallpaperMode.FOREST -> R.string.wallpaper_forest
    WallpaperMode.DUSK -> R.string.wallpaper_dusk
    WallpaperMode.OCEAN -> R.string.wallpaper_ocean
    WallpaperMode.EMBER -> R.string.wallpaper_ember
}

/**
 * Cuadrícula de fondos con su nombre debajo; el elegido lleva el borde del acento. "Mi imagen" abre la galería
 * la primera vez; con imagen ya elegida solo se selecciona y el botón de abajo permite cambiarla.
 * El control de oscurecimiento aparece con los fondos que dejan ver una imagen (la del sistema o la propia).
 */
@Composable
fun WallpaperChoice(vm: HomeViewModel, settings: LauncherSettings) {
    val selected = settings.wallpaper
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::setCustomWallpaper) }
    val thumbnail by produceState<ImageBitmap?>(null, settings.customWallpaper) {
        value = if (settings.customWallpaper.isEmpty()) null else withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inSampleSize = 8 }
            BitmapFactory.decodeFile(vm.wallpaperFile(settings.customWallpaper).path, options)?.asImageBitmap()
        }
    }
    FlowRow(
        Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WallpaperMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val shape = RoundedCornerShape(18.dp)
            val onClick = {
                if (mode == WallpaperMode.CUSTOM && thumbnail == null) picker.launch("image/*") else vm.setWallpaper(mode)
            }
            Column(
                Modifier.width(80.dp).selectable(isSelected, role = Role.RadioButton) { onClick() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(80.dp, 104.dp).clip(shape)
                        .background(
                            when (mode) {
                                WallpaperMode.SYSTEM -> Brush.linearGradient(listOf(Color(0xFF3B6B4F), Color(0xFF1B2A44), Color(0xFF6B3B55)))
                                WallpaperMode.CUSTOM -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
                                else -> mode.brush()
                            },
                        )
                        .border(
                            if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (mode == WallpaperMode.CUSTOM) {
                        val image = thumbnail
                        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    stringResource(mode.label()),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    if (selected == WallpaperMode.CUSTOM && thumbnail != null) {
        TextButton(onClick = { picker.launch("image/*") }) { Text(stringResource(R.string.wallpaper_change_image)) }
    }
    if (selected == WallpaperMode.SYSTEM || selected == WallpaperMode.CUSTOM) {
        SliderRow(
            label = stringResource(R.string.wallpaper_dim),
            valueText = "${settings.wallpaperDim}%",
            value = settings.wallpaperDim.toFloat(),
            range = 0f..MAX_WALLPAPER_DIM.toFloat(),
            steps = MAX_WALLPAPER_DIM / 5 - 1,
            onChange = { vm.setWallpaperDim(it.toInt()) },
        )
    }
}
