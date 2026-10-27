package com.areca.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Dibuja un icono dentro de una burbuja (el recorte con la forma lo pone quien la contiene).
 * [zoomPercent] es el encuadre: 100 muestra el lienzo completo del icono; 150, la zona visible estándar.
 */
@Composable
fun IconImage(bitmap: ImageBitmap, contentDescription: String?, zoomPercent: Int, padding: Dp, modifier: Modifier = Modifier) {
    val scale = zoomPercent / 100f
    Image(
        bitmap,
        contentDescription,
        modifier.fillMaxSize().padding(padding).graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
}

const val MIN_ICON_ZOOM = 100
const val MAX_ICON_ZOOM = 200
