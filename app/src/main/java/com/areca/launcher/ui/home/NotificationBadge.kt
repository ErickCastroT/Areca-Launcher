package com.areca.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areca.launcher.data.AppInfo

/** Número de notificaciones por paquete; vacío si las insignias están apagadas. */
val LocalBadgeCounts = compositionLocalOf<Map<String, Int>> { emptyMap() }

/** Notificaciones de una burbuja: las de su app o, si es una carpeta, la suma de las de sus apps. */
fun badgeCountFor(app: AppInfo, counts: Map<String, Int>): Int =
    if (app.isFolder) app.folderMembers.sumOf { counts[it.packageName] ?: 0 } else counts[app.packageName] ?: 0

/**
 * Círculo con el número de notificaciones: blanco con texto negro en tema oscuro y negro con texto blanco en tema
 * claro, con un anillo del color del fondo del tema que lo separa del icono y del fondo.
 * Se pega a la esquina superior derecha del cuadro de la burbuja, así que es igual de visible con cualquier
 * forma de icono (círculo, cuadrado redondeado, hexágono, gota…). No se dibuja si no hay notificaciones.
 */
@Composable
fun BoxScope.NotificationBadge(count: Int, bubbleSize: Dp) {
    if (count <= 0) return
    // Crece con la burbuja, pero sin pasar de un tamaño legible ni taparla.
    val size = (bubbleSize * 0.3f).coerceIn(22.dp, 30.dp)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fill = if (dark) Color.White else Color.Black
    val onFill = if (dark) Color.Black else Color.White
    val ring = MaterialTheme.colorScheme.background
    Box(
        Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp)
            .defaultMinSize(minWidth = size, minHeight = size)
            .background(ring, CircleShape)
            .padding(2.dp)
            .defaultMinSize(minWidth = size - 4.dp, minHeight = size - 4.dp)
            .background(fill, CircleShape)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = onFill,
            fontSize = (size.value * 0.5f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
