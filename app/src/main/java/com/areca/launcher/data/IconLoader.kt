package com.areca.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Carga y cachea iconos. Prioridad: icono personalizado de la app, paquete de iconos, icono del sistema.
 * [override] tiene la forma "pack:<paquete>:<drawable>" o "file:<archivo>".
 */
class IconLoader(
    private val context: Context,
    private val packs: IconPackManager,
    private val apps: AppRepository,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    // La caché se mide en bytes: unos 80 iconos de 112 dp, en vez de un número fijo de entradas grandes.
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    // El mapa de bits es el lienzo completo del icono (108 dp): la zona visible estándar (72 dp) es el centro
    // y el resto es sangrado que se ve al reducir el encuadre.
    private val sizePx = (150 * context.resources.displayMetrics.density).toInt()

    /** Icono personalizado de una app (para las miniaturas dentro de una carpeta); lo conecta el ViewModel. */
    var overrideOf: (String) -> String? = { null }

    fun cached(app: AppInfo, pack: String, override: String?): ImageBitmap? =
        cache.get(cacheKey(app, pack, override))

    /** Bloqueante: llamar desde un hilo de fondo. */
    fun load(app: AppInfo, pack: String, override: String?): ImageBitmap {
        val key = cacheKey(app, pack, override)
        cache.get(key)?.let { return it }
        if (app.isFolder) return renderFolder(app, pack).also { cache.put(key, it) }
        val component = ComponentName(app.packageName, app.className)
        val drawable = overrideDrawable(override)
            ?: (if (pack.isNotEmpty()) packs.iconFor(pack, component) else null)
            ?: systemIcon(component)
        return render(drawable).also { cache.put(key, it) }
    }

    /** Vista previa de un icono personalizado (selector de iconos). Bloqueante. */
    fun loadOverride(override: String): ImageBitmap {
        val key = "ov|$override"
        cache.get(key)?.let { return it }
        return render(overrideDrawable(override)).also { cache.put(key, it) }
    }

    private fun overrideDrawable(override: String?): Drawable? = when {
        override == null -> null
        override.startsWith("pack:") -> {
            val rest = override.removePrefix("pack:")
            val i = rest.indexOf(':')
            if (i < 0) null else packs.drawableByName(rest.substring(0, i), rest.substring(i + 1))
        }
        override.startsWith("file:") -> {
            val file = File(context.filesDir, "icons/${override.removePrefix("file:")}")
            BitmapFactory.decodeFile(file.path)?.let { BitmapDrawable(context.resources, it) }
        }
        else -> null
    }

    private fun systemIcon(component: ComponentName): Drawable? =
        (apps.activityInfo(component)
            ?: launcherApps.getActivityList(component.packageName, Process.myUserHandle())
                .firstOrNull { it.componentName == component })
            ?.getIcon(0)

    private fun render(drawable: Drawable?): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        if (drawable != null) {
            val canvas = Canvas(bitmap)
            if (drawable is AdaptiveIconDrawable) {
                // Las capas se dibujan sin la máscara del sistema, a lienzo completo: la forma la pone Areca.
                drawable.background?.apply { setBounds(0, 0, sizePx, sizePx); draw(canvas) }
                drawable.foreground?.apply { setBounds(0, 0, sizePx, sizePx); draw(canvas) }
            } else {
                // Icono plano: ocupa la zona visible estándar (el centro, 2/3 del lienzo).
                val inset = sizePx / 6
                drawable.setBounds(inset, inset, sizePx - inset, sizePx - inset)
                drawable.draw(canvas)
            }
        }
        return bitmap.asImageBitmap()
    }

    /** Fondo oscuro y hasta cuatro miniaturas de las apps de la carpeta, centradas según cuántas haya. */
    private fun renderFolder(folder: AppInfo, pack: String): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val half = sizePx / 2f
        canvas.scale(VIEWPORT, VIEWPORT, half, half) // la carpeta ocupa la zona visible estándar
        canvas.drawCircle(half, half, half, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FOLDER_BACKGROUND })
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val members = folder.folderMembers.take(4)
        val margin = ((1 - VIEWPORT) / 2 * sizePx).toInt()
        val memberSource = Rect(margin, margin, sizePx - margin, sizePx - margin) // zona visible de cada miniatura
        // Filas de miniaturas: 1 → [1]; 2 → [2]; 3 → [2, 1]; 4 → [2, 2].
        val rows = when (members.size) {
            0 -> emptyList()
            1 -> listOf(1)
            2 -> listOf(2)
            3 -> listOf(2, 1)
            else -> listOf(2, 2)
        }
        val cell = sizePx * (if (members.size == 1) 0.52f else 0.34f)
        val gap = sizePx * 0.06f
        val blockHeight = rows.size * cell + (rows.size - 1) * gap
        var index = 0
        rows.forEachIndexed { r, count ->
            val top = (sizePx - blockHeight) / 2f + r * (cell + gap)
            val rowWidth = count * cell + (count - 1) * gap
            repeat(count) { c ->
                val left = (sizePx - rowWidth) / 2f + c * (cell + gap)
                val rect = RectF(left, top, left + cell, top + cell)
                val member = members[index++]
                val icon = load(member, pack, overrideOf(member.key)).asAndroidBitmap()
                canvas.save()
                canvas.clipPath(Path().apply { addOval(rect, Path.Direction.CW) })
                canvas.drawBitmap(icon, memberSource, rect, paint)
                canvas.restore()
            }
        }
        return bitmap.asImageBitmap()
    }

    private fun cacheKey(app: AppInfo, pack: String, override: String?): String {
        val members = if (app.isFolder) app.folderMembers.joinToString(",") { it.key + "=" + overrideOf(it.key).orEmpty() } else ""
        return "$pack|${override.orEmpty()}|${app.key}|$members"
    }

    private companion object {
        const val CACHE_BYTES = 48 * 1024 * 1024
        const val VIEWPORT = 2f / 3f
        const val FOLDER_BACKGROUND = 0xE61B1D22.toInt()
    }
}
