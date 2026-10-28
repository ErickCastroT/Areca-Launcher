package com.areca.launcher.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.areca.launcher.data.LauncherSettings
import com.areca.launcher.data.WallpaperMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Capa bajo el escritorio: la imagen propia (modo CUSTOM) y el oscurecimiento que se aplica tanto a ella
 * como al fondo del sistema para que el reloj y los nombres se lean. Los colores lisos no se oscurecen.
 */
@Composable
fun WallpaperBackdrop(settings: LauncherSettings, vm: HomeViewModel) {
    val mode = settings.wallpaper
    val file = settings.customWallpaper
    val image by produceState<ImageBitmap?>(null, mode, file) {
        value = if (mode == WallpaperMode.CUSTOM && file.isNotEmpty()) {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(vm.wallpaperFile(file).path)?.asImageBitmap() }
        } else null
    }
    image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    val dims = mode == WallpaperMode.SYSTEM || mode == WallpaperMode.CUSTOM
    if (dims && settings.wallpaperDim > 0) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.wallpaperDim / 100f)))
    }
}
