package com.areca.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.areca.launcher.R
import com.areca.launcher.data.ThemeMode

/**
 * Paleta propia, tomada de la palma areca: verdes casi negros del follaje y el dorado de la cáscara
 * del fruto. Un solo acento (primary); el rojo del fruto masticado queda para lo destructivo.
 * Se usa cuando el tema es "Areca" y como respaldo en Android < 12.
 */
private val ArecaDark = darkColorScheme(
    primary = Color(0xFFF0B84A),
    onPrimary = Color(0xFF0D1310),
    secondary = Color(0xFF8FA898),
    background = Color(0xFF0D1310),
    onBackground = Color(0xFFE8F0E9),
    surface = Color(0xFF16211B),
    onSurface = Color(0xFFE8F0E9),
    surfaceContainer = Color(0xFF16211B),
    surfaceContainerHigh = Color(0xFF1C2A23),
    surfaceVariant = Color(0xFF2A3F33),
    onSurfaceVariant = Color(0xFF8FA898),
    outline = Color(0xFF2A3F33),
    error = Color(0xFFEB6A7D),
)

private val ArecaLight = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF52665A),
    background = Color(0xFFF4F7F4),
    onBackground = Color(0xFF111A15),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111A15),
    surfaceContainer = Color(0xFFE6EDE7),
    surfaceContainerHigh = Color(0xFFDDE6DE),
    surfaceVariant = Color(0xFFCFDBD2),
    onSurfaceVariant = Color(0xFF52665A),
    outline = Color(0xFFB4C4B9),
    error = Color(0xFFB3202F),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun figtree(weight: FontWeight) =
    Font(R.font.figtree, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private val Figtree = FontFamily(
    figtree(FontWeight.Light),
    figtree(FontWeight.Normal),
    figtree(FontWeight.Medium),
    figtree(FontWeight.SemiBold),
)

/** Escala corta y deliberada: reloj ligero y grande, títulos semibold, cuerpo en 16 sp. */
private val ArecaTypography = Typography(
    displayLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Light, fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = (-1.5).sp),
    headlineMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
)

/**
 * Integración con el tema global de Android: en modo SYSTEM usa los colores dinámicos del fondo de
 * pantalla (Material You, Android 12+) y sigue el modo claro/oscuro del sistema.
 */
@Composable
fun ArecaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        mode == ThemeMode.ARECA -> ArecaDark
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> ArecaDark
        else -> ArecaLight
    }
    MaterialTheme(colorScheme = scheme, typography = ArecaTypography) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
