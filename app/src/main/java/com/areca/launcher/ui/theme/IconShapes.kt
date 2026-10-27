package com.areca.launcher.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.areca.launcher.data.IconShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

fun IconShape.toShape(): Shape = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.ROUNDED_SQUARE -> RoundedCornerShape(percent = 14)
    IconShape.SQUIRCLE -> SquircleShape
    IconShape.HEXAGON -> HexagonShape
    IconShape.TEARDROP -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 8, bottomStartPercent = 50)
}

/** Superelipse |x|^n + |y|^n = 1 con n = 4. */
private object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val steps = 72
        val rx = size.width / 2f
        val ry = size.height / 2f
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = rx + rx * (sign(c) * abs(c).pow(0.5)).toFloat()
            val y = ry + ry * (sign(s) * abs(s).pow(0.5)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Hexágono con vértice hacia arriba, a juego con la disposición en panal. */
private object HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (i in 0 until 6) {
            val a = PI / 3.0 * i - PI / 2.0
            val x = cx + cx * cos(a).toFloat()
            val y = cy + cy * sin(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
