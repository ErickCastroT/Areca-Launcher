package com.areca.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "areca_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val COLUMNS = intPreferencesKey("columns")
        val ICON_SIZE = intPreferencesKey("icon_size_dp")
        val SPACING = intPreferencesKey("spacing_dp")
        val ICON_PADDING = intPreferencesKey("icon_padding_percent")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val ICON_ZOOM = intPreferencesKey("icon_zoom_percent")
        val ICON_ZOOMS = stringSetPreferencesKey("icon_zooms")
        val LAYOUT = stringPreferencesKey("layout")
        val THEME = stringPreferencesKey("theme_mode")
        val WALLPAPER = stringPreferencesKey("wallpaper")
        val CUSTOM_WALLPAPER = stringPreferencesKey("custom_wallpaper")
        val WALLPAPER_DIM = intPreferencesKey("wallpaper_dim")
        val ACCENT = stringPreferencesKey("accent")
        val THEMED_ICONS = booleanPreferencesKey("themed_icons")
        val PACK_FALLBACK = booleanPreferencesKey("pack_fallback")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val HIDE_STATUS_BAR = booleanPreferencesKey("hide_status_bar")
        val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock")
        val SWIPE_DOWN = booleanPreferencesKey("swipe_down_notifications")
        val WIDGET_IDS = stringPreferencesKey("widget_ids")
        val WIDGET_HEIGHTS = stringPreferencesKey("widget_heights")
        val WIDGET_BOTTOM = stringPreferencesKey("widget_bottom")
        val WIDGET_WIDTHS = stringPreferencesKey("widget_widths")
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val SHOW_RECENTS = booleanPreferencesKey("show_recents")
        val SHOW_BADGES = booleanPreferencesKey("show_badges")
        val BOTTOM_SWIPE = booleanPreferencesKey("bottom_swipe_recents")
        val CLOCK_Y = floatPreferencesKey("clock_y")
        val FAVORITES = stringPreferencesKey("favorites")
        val ICON_OVERRIDES = stringSetPreferencesKey("icon_overrides")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val FOLDERS = stringPreferencesKey("folders")
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { p ->
        val d = LauncherSettings()
        LauncherSettings(
            columns = p[Keys.COLUMNS] ?: d.columns,
            iconSizeDp = p[Keys.ICON_SIZE] ?: d.iconSizeDp,
            spacingDp = p[Keys.SPACING] ?: d.spacingDp,
            iconPaddingPercent = p[Keys.ICON_PADDING] ?: d.iconPaddingPercent,
            iconZoomPercent = p[Keys.ICON_ZOOM] ?: d.iconZoomPercent,
            iconZooms = (p[Keys.ICON_ZOOMS] ?: emptySet())
                .mapNotNull { e -> e.split('\t', limit = 2).takeIf { it.size == 2 && it[1].toIntOrNull() != null } }
                .associate { it[0] to it[1].toInt() },
            iconShape = p[Keys.ICON_SHAPE]?.let { runCatching { IconShape.valueOf(it) }.getOrNull() } ?: d.iconShape,
            layout = p[Keys.LAYOUT]?.let { runCatching { GridLayout.valueOf(it) }.getOrNull() } ?: d.layout,
            themeMode = ThemeMode.parse(p[Keys.THEME]) ?: d.themeMode,
            wallpaper = p[Keys.WALLPAPER]?.let { runCatching { WallpaperMode.valueOf(it) }.getOrNull() } ?: d.wallpaper,
            customWallpaper = p[Keys.CUSTOM_WALLPAPER] ?: d.customWallpaper,
            wallpaperDim = p[Keys.WALLPAPER_DIM] ?: d.wallpaperDim,
            accent = p[Keys.ACCENT]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() } ?: d.accent,
            themedIcons = p[Keys.THEMED_ICONS] ?: d.themedIcons,
            packFallback = p[Keys.PACK_FALLBACK] ?: d.packFallback,
            iconPack = p[Keys.ICON_PACK] ?: d.iconPack,
            hideStatusBar = p[Keys.HIDE_STATUS_BAR] ?: d.hideStatusBar,
            doubleTapLock = p[Keys.DOUBLE_TAP_LOCK] ?: d.doubleTapLock,
            swipeDownNotifications = p[Keys.SWIPE_DOWN] ?: d.swipeDownNotifications,
            widgetHeights = p[Keys.WIDGET_HEIGHTS]?.split(',')?.mapNotNull { e ->
                e.split(':').takeIf { it.size == 2 }?.let { (a, b) -> a.toIntOrNull()?.let { id -> b.toIntOrNull()?.let { id to it } } }
            }?.toMap() ?: d.widgetHeights,
            widgetWidths = p[Keys.WIDGET_WIDTHS]?.split(',')?.mapNotNull { e ->
                e.split(':').takeIf { it.size == 2 }?.let { (a, b) -> a.toIntOrNull()?.let { id -> b.toIntOrNull()?.let { id to it } } }
            }?.toMap() ?: d.widgetWidths,
            widgetBottomIds = p[Keys.WIDGET_BOTTOM]?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet() ?: d.widgetBottomIds,
            widgetIds = p[Keys.WIDGET_IDS]?.split(',')?.mapNotNull { it.toIntOrNull() } ?: d.widgetIds,
            showClock = p[Keys.SHOW_CLOCK] ?: d.showClock,
            showRecents = p[Keys.SHOW_RECENTS] ?: d.showRecents,
            showBadges = p[Keys.SHOW_BADGES] ?: d.showBadges,
            bottomSwipeRecents = p[Keys.BOTTOM_SWIPE] ?: d.bottomSwipeRecents,
            clockY = p[Keys.CLOCK_Y] ?: d.clockY,
            favorites = p[Keys.FAVORITES]?.split('\n')?.filter { it.isNotEmpty() } ?: d.favorites,
            hidden = p[Keys.HIDDEN] ?: d.hidden,
            folders = FolderCodec.decode(p[Keys.FOLDERS]),
            iconOverrides = (p[Keys.ICON_OVERRIDES] ?: emptySet())
                .mapNotNull { e -> e.split('\t', limit = 2).takeIf { it.size == 2 } }
                .associate { it[0] to it[1] },
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setColumns(v: Int) = edit { it[Keys.COLUMNS] = v }
    suspend fun setIconSize(v: Int) = edit { it[Keys.ICON_SIZE] = v }
    suspend fun setSpacing(v: Int) = edit { it[Keys.SPACING] = v }
    suspend fun setIconPadding(v: Int) = edit { it[Keys.ICON_PADDING] = v }
    suspend fun setIconZoom(v: Int) = edit { it[Keys.ICON_ZOOM] = v }
    /** [percent] null deja la app con el encuadre global. */
    suspend fun setAppIconZoom(key: String, percent: Int?) = edit { prefs ->
        val current = (prefs[Keys.ICON_ZOOMS] ?: emptySet()).filterNot { it.startsWith("$key\t") }.toMutableSet()
        if (percent != null) current += "$key\t$percent"
        prefs[Keys.ICON_ZOOMS] = current
    }
    suspend fun clearIconZooms() = edit { it.remove(Keys.ICON_ZOOMS) }
    suspend fun setIconShape(v: IconShape) = edit { it[Keys.ICON_SHAPE] = v.name }
    suspend fun setLayout(v: GridLayout) = edit { it[Keys.LAYOUT] = v.name }
    suspend fun setThemeMode(v: ThemeMode) = edit { it[Keys.THEME] = v.name }
    suspend fun setWallpaper(v: WallpaperMode) = edit { it[Keys.WALLPAPER] = v.name }
    suspend fun setCustomWallpaper(file: String) = edit { it[Keys.CUSTOM_WALLPAPER] = file }
    suspend fun setWallpaperDim(v: Int) = edit { it[Keys.WALLPAPER_DIM] = v }
    suspend fun setAccent(v: AccentColor) = edit { it[Keys.ACCENT] = v.name }
    suspend fun setThemedIcons(v: Boolean) = edit { it[Keys.THEMED_ICONS] = v }
    suspend fun setPackFallback(v: Boolean) = edit { it[Keys.PACK_FALLBACK] = v }
    suspend fun setIconPack(v: String) = edit { it[Keys.ICON_PACK] = v }
    suspend fun setHideStatusBar(v: Boolean) = edit { it[Keys.HIDE_STATUS_BAR] = v }
    suspend fun setDoubleTapLock(v: Boolean) = edit { it[Keys.DOUBLE_TAP_LOCK] = v }
    suspend fun setSwipeDownNotifications(v: Boolean) = edit { it[Keys.SWIPE_DOWN] = v }
    suspend fun setWidgetHeights(v: Map<Int, Int>) = edit { it[Keys.WIDGET_HEIGHTS] = v.entries.joinToString(",") { (k, h) -> "$k:$h" } }
    suspend fun setWidgetWidths(v: Map<Int, Int>) = edit { it[Keys.WIDGET_WIDTHS] = v.entries.joinToString(",") { (k, w) -> "$k:$w" } }
    suspend fun setWidgetBottomIds(v: Set<Int>) = edit { it[Keys.WIDGET_BOTTOM] = v.joinToString(",") }
    suspend fun setWidgetIds(v: List<Int>) = edit { it[Keys.WIDGET_IDS] = v.joinToString(",") }
    suspend fun setShowClock(v: Boolean) = edit { it[Keys.SHOW_CLOCK] = v }
    suspend fun setShowRecents(v: Boolean) = edit { it[Keys.SHOW_RECENTS] = v }
    suspend fun setShowBadges(v: Boolean) = edit { it[Keys.SHOW_BADGES] = v }
    suspend fun setBottomSwipeRecents(v: Boolean) = edit { it[Keys.BOTTOM_SWIPE] = v }
    suspend fun setClockY(v: Float) = edit { it[Keys.CLOCK_Y] = v }
    /** Favoritas y carpetas se guardan juntas para que nunca queden desacordes. */
    suspend fun setFavoritesAndFolders(favorites: List<String>, folders: List<Folder>) = edit {
        it[Keys.FAVORITES] = favorites.joinToString("\n")
        it[Keys.FOLDERS] = FolderCodec.encode(folders)
    }
    suspend fun setFavorites(v: List<String>) = edit { it[Keys.FAVORITES] = v.joinToString("\n") }
    /** [value] null restablece el icono original de la app. */
    suspend fun setIconOverride(key: String, value: String?) = edit { prefs ->
        val current = (prefs[Keys.ICON_OVERRIDES] ?: emptySet()).filterNot { it.startsWith("$key\t") }.toMutableSet()
        if (value != null) current += "$key\t$value"
        prefs[Keys.ICON_OVERRIDES] = current
    }
    suspend fun clearIconOverrides() = edit { it.remove(Keys.ICON_OVERRIDES) }
    suspend fun setHidden(v: Set<String>) = edit { it[Keys.HIDDEN] = v }

    /** Aplica un respaldo; los widgets actuales se conservan porque sus ids son de este teléfono. */
    suspend fun restore(v: LauncherSettings) = edit { p ->
        p[Keys.COLUMNS] = v.columns
        p[Keys.ICON_SIZE] = v.iconSizeDp
        p[Keys.SPACING] = v.spacingDp
        p[Keys.ICON_PADDING] = v.iconPaddingPercent
        p[Keys.ICON_SHAPE] = v.iconShape.name
        p[Keys.ICON_ZOOM] = v.iconZoomPercent
        p[Keys.ICON_ZOOMS] = v.iconZooms.map { (k, z) -> "$k\t$z" }.toSet()
        p[Keys.LAYOUT] = v.layout.name
        p[Keys.THEME] = v.themeMode.name
        p[Keys.WALLPAPER] = v.wallpaper.name
        p[Keys.WALLPAPER_DIM] = v.wallpaperDim
        p[Keys.ACCENT] = v.accent.name
        p[Keys.THEMED_ICONS] = v.themedIcons
        p[Keys.PACK_FALLBACK] = v.packFallback
        p[Keys.ICON_PACK] = v.iconPack
        p[Keys.SHOW_CLOCK] = v.showClock
        p[Keys.SHOW_RECENTS] = v.showRecents
        p[Keys.SHOW_BADGES] = v.showBadges
        p[Keys.BOTTOM_SWIPE] = v.bottomSwipeRecents
        p[Keys.CLOCK_Y] = v.clockY
        p[Keys.HIDE_STATUS_BAR] = v.hideStatusBar
        p[Keys.DOUBLE_TAP_LOCK] = v.doubleTapLock
        p[Keys.SWIPE_DOWN] = v.swipeDownNotifications
        p[Keys.FAVORITES] = v.favorites.joinToString("\n")
        p[Keys.HIDDEN] = v.hidden
        p[Keys.FOLDERS] = FolderCodec.encode(v.folders)
        p[Keys.ICON_OVERRIDES] = v.iconOverrides.map { (k, value) -> "$k\t$value" }.toSet()
    }
}
