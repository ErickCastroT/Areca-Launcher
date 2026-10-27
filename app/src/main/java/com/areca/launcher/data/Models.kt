package com.areca.launcher.data

enum class GridLayout { HONEYCOMB, ROWS }

enum class IconShape { CIRCLE, SQUIRCLE, ROUNDED_SQUARE, HEXAGON, TEARDROP }

/** SYSTEM: colores dinámicos de Android (Material You) y claro/oscuro del sistema. ARECA: paleta propia, siempre oscura. */
enum class ThemeMode { SYSTEM, ARECA }

enum class WallpaperMode { SYSTEM, BLACK }

data class AppInfo(
    /** ComponentName.flattenToString(); identificador estable de la app. */
    val key: String,
    val label: String,
    val packageName: String,
    val className: String,
    /** Apps del sistema no se pueden desinstalar desde aquí. */
    val isSystem: Boolean = false,
    /** Solo en carpetas: las apps que contiene (visibles), en orden. */
    val folderMembers: List<AppInfo> = emptyList(),
) {
    val isFolder: Boolean get() = key.startsWith(FOLDER_PREFIX)
}

data class LauncherSettings(
    val columns: Int = 3,
    val iconSizeDp: Int = 64,
    /** Separación entre burbujas. */
    val spacingDp: Int = 28,
    /** Relleno interno del icono dentro de su burbuja, en % del tamaño. */
    val iconPaddingPercent: Int = 0,
    val iconShape: IconShape = IconShape.CIRCLE,
    /** Encuadre del icono: 100 = el icono completo, 150 = zona visible estándar (llena la burbuja), más = acercar. */
    val iconZoomPercent: Int = 150,
    /** Encuadre propio por app (clave → %); sin entrada = el global. */
    val iconZooms: Map<String, Int> = emptyMap(),
    val layout: GridLayout = GridLayout.HONEYCOMB,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val wallpaper: WallpaperMode = WallpaperMode.SYSTEM,
    val iconPack: String = "",
    val showClock: Boolean = true,
    /** Fila de apps recientes al inicio del listado de apps. */
    val showRecents: Boolean = true,
    /** Insignias con el número de notificaciones en las burbujas (requiere acceso a notificaciones). */
    val showBadges: Boolean = false,
    /** Deslizar hacia arriba desde la parte baja del escritorio abre las apps abiertas. */
    val bottomSwipeRecents: Boolean = true,
    val doubleTapLock: Boolean = true,
    val swipeDownNotifications: Boolean = true,
    /** Ids de widgets (AppWidgetHost) en el orden en que se muestran. */
    val widgetIds: List<Int> = emptyList(),
    /** Alto elegido por widget (dp); sin entrada = el mínimo que declara el widget. */
    val widgetHeights: Map<Int, Int> = emptyMap(),
    /** Ancho elegido por widget (dp); sin entrada = el máximo que cabe. */
    val widgetWidths: Map<Int, Int> = emptyMap(),
    /** Widgets colocados debajo de las burbujas (el resto va arriba). */
    val widgetBottomIds: Set<Int> = emptySet(),
    val hideStatusBar: Boolean = false,
    /** Posición vertical del widget de reloj como fracción de la altura (se ajusta a los límites). */
    val clockY: Float = 0.02f,
    /** Claves de apps favoritas, en el orden en que se muestran. */
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    /** Carpetas de burbujas; cada una ocupa un lugar en [favorites] con su clave "folder:<id>". */
    val folders: List<Folder> = emptyList(),
    /** Icono personalizado por app: "pack:<paquete>:<drawable>" o "file:<archivo>". */
    val iconOverrides: Map<String, String> = emptyMap(),
)

data class IconPackInfo(val packageName: String, val label: String)

/** Una app cuenta como favorita si está en el escritorio o dentro de una carpeta. */
fun LauncherSettings.isFavorite(appKey: String): Boolean = appKey in favorites || folders.any { appKey in it.apps }

/** Encuadre efectivo de una app, en %. Las carpetas siempre usan el estándar. */
fun LauncherSettings.zoomFor(appKey: String): Int =
    if (appKey.startsWith(FOLDER_PREFIX)) DEFAULT_ICON_ZOOM else iconZooms[appKey] ?: iconZoomPercent

const val DEFAULT_ICON_ZOOM = 150
