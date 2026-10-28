package com.areca.launcher.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Resultado de leer un respaldo: los ajustes y el idioma, listos para aplicar. */
data class Backup(val settings: LauncherSettings, val language: String)

/**
 * Exporta e importa la configuración como un archivo JSON. Los widgets no se incluyen (sus ids
 * pertenecen a este teléfono); los iconos elegidos desde la galería van dentro del archivo.
 */
object BackupManager {
    private const val FORMAT = 1

    fun toJson(context: Context, s: LauncherSettings, language: String): String {
        val icons = JSONObject()
        s.iconOverrides.values.filter { it.startsWith("file:") }.forEach { value ->
            val name = value.removePrefix("file:")
            val file = File(context.filesDir, "icons/$name")
            if (file.exists()) icons.put(name, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        }
        return JSONObject().apply {
            put("format", FORMAT)
            put("columns", s.columns)
            put("iconSizeDp", s.iconSizeDp)
            put("spacingDp", s.spacingDp)
            put("iconPaddingPercent", s.iconPaddingPercent)
            put("iconShape", s.iconShape.name)
            put("iconZoomPercent", s.iconZoomPercent)
            put("iconZooms", JSONObject(s.iconZooms))
            put("layout", s.layout.name)
            put("themeMode", s.themeMode.name)
            put("wallpaper", s.wallpaper.name)
            put("wallpaperDim", s.wallpaperDim)
            put("accent", s.accent.name)
            put("themedIcons", s.themedIcons)
            put("tiltEnabled", s.tiltEnabled)
            put("tiltStrength", s.tiltStrength)
            put("packFallback", s.packFallback)
            put("iconPack", s.iconPack)
            put("showClock", s.showClock)
            put("showRecents", s.showRecents)
            put("showBadges", s.showBadges)
            put("bottomSwipeRecents", s.bottomSwipeRecents)
            put("clockY", s.clockY.toDouble())
            put("hideStatusBar", s.hideStatusBar)
            put("doubleTapLock", s.doubleTapLock)
            put("swipeDownNotifications", s.swipeDownNotifications)
            put("favorites", JSONArray(s.favorites))
            put("hidden", JSONArray(s.hidden.toList()))
            put("folders", FolderCodec.encode(s.folders))
            put("iconOverrides", JSONObject(s.iconOverrides))
            put("iconFiles", icons)
            put("language", language)
        }.toString(2)
    }

    /** Lee el respaldo y restaura las imágenes de iconos. Devuelve null si el archivo no es válido. */
    fun fromJson(context: Context, text: String): Backup? = runCatching {
        val o = JSONObject(text)
        if (o.optInt("format", -1) != FORMAT) return null
        val d = LauncherSettings()
        fun strings(key: String) = o.optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
        val overrides = o.optJSONObject("iconOverrides")?.let { j -> j.keys().asSequence().associateWith { j.getString(it) } } ?: emptyMap()
        o.optJSONObject("iconFiles")?.let { files ->
            val dir = File(context.filesDir, "icons").apply { mkdirs() }
            files.keys().forEach { name ->
                // El nombre viene del archivo: solo se aceptan nombres simples para no escribir fuera de la carpeta.
                if (name.isNotEmpty() && '/' !in name && '\\' !in name && ".." !in name) {
                    File(dir, name).writeBytes(Base64.decode(files.getString(name), Base64.DEFAULT))
                }
            }
        }
        Backup(
            settings = LauncherSettings(
                columns = o.optInt("columns", d.columns),
                iconSizeDp = o.optInt("iconSizeDp", d.iconSizeDp),
                spacingDp = o.optInt("spacingDp", d.spacingDp),
                iconPaddingPercent = o.optInt("iconPaddingPercent", d.iconPaddingPercent),
                iconZoomPercent = o.optInt("iconZoomPercent", d.iconZoomPercent),
                iconZooms = o.optJSONObject("iconZooms")?.let { j -> j.keys().asSequence().associateWith { j.getInt(it) } } ?: emptyMap(),
                iconShape = enumOr(o.optString("iconShape"), d.iconShape),
                layout = enumOr(o.optString("layout"), d.layout),
                themeMode = ThemeMode.parse(o.optString("themeMode")) ?: d.themeMode,
                wallpaper = enumOr(o.optString("wallpaper"), d.wallpaper),
                wallpaperDim = o.optInt("wallpaperDim", d.wallpaperDim).coerceIn(0, MAX_WALLPAPER_DIM),
                accent = enumOr(o.optString("accent"), d.accent),
                themedIcons = o.optBoolean("themedIcons", d.themedIcons),
                tiltEnabled = o.optBoolean("tiltEnabled", d.tiltEnabled),
                tiltStrength = o.optInt("tiltStrength", d.tiltStrength).coerceIn(MIN_TILT_STRENGTH, 100),
                packFallback = o.optBoolean("packFallback", d.packFallback),
                iconPack = o.optString("iconPack", d.iconPack),
                showClock = o.optBoolean("showClock", d.showClock),
                showRecents = o.optBoolean("showRecents", d.showRecents),
                showBadges = o.optBoolean("showBadges", d.showBadges),
                bottomSwipeRecents = o.optBoolean("bottomSwipeRecents", d.bottomSwipeRecents),
                clockY = o.optDouble("clockY", d.clockY.toDouble()).toFloat(),
                hideStatusBar = o.optBoolean("hideStatusBar", d.hideStatusBar),
                doubleTapLock = o.optBoolean("doubleTapLock", d.doubleTapLock),
                swipeDownNotifications = o.optBoolean("swipeDownNotifications", d.swipeDownNotifications),
                favorites = strings("favorites"),
                hidden = strings("hidden").toSet(),
                folders = FolderCodec.decode(o.optString("folders", null)),
                iconOverrides = overrides,
            ),
            language = o.optString("language", LanguageStore.SYSTEM),
        )
    }.getOrNull()

    private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
}
