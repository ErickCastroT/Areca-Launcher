package com.areca.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
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
    // Versión compacta para el listado de apps: se dibuja a 44 dp y el encuadre máximo es 200 %, así que basta un lienzo
    // de 96 dp (la mitad de píxeles que el de 150 dp). Ahorra memoria y tiempo de dibujado, sobre todo con muchas apps.
    private val compactPx = (COMPACT_DP * context.resources.displayMetrics.density).toInt()

    /** Colores de los iconos tematizados: [tile] es el fondo de la burbuja y [glyph] el dibujo. */
    class Tint(val tile: Int, val glyph: Int)

    /**
     * Estilo que se aplica a los iconos del sistema. Es estado de Compose y [styleKey] entra en la clave de caché
     * y en la de las pantallas, así que al cambiarlo los iconos se vuelven a dibujar solos.
     */
    var themed: Tint? by mutableStateOf(null)
    var packFallback: Boolean by mutableStateOf(true)
    val styleKey: String get() = "${themed?.let { "${it.tile}.${it.glyph}" }.orEmpty()}|$packFallback"

    /** Icono personalizado de una app (para las miniaturas dentro de una carpeta); lo conecta el ViewModel. */
    var overrideOf: (String) -> String? = { null }

    fun cached(app: AppInfo, pack: String, override: String?, compact: Boolean = false): ImageBitmap? =
        cache.get(cacheKey(app, pack, override, compact))

    /** Bloqueante: llamar desde un hilo de fondo. */
    fun load(app: AppInfo, pack: String, override: String?, compact: Boolean = false): ImageBitmap {
        val key = cacheKey(app, pack, override, compact)
        cache.get(key)?.let { return it }
        val px = if (compact) compactPx else sizePx
        if (app.isFolder) return renderFolder(app, pack, px, compact).also { cache.put(key, it) }
        val component = ComponentName(app.packageName, app.className)
        val styled = overrideDrawable(override) ?: (if (pack.isNotEmpty()) packs.iconFor(pack, component) else null)
        if (styled != null) return render(styled, px).also { cache.put(key, it) }
        // Sin icono propio ni del paquete: el del sistema, recompuesto con el estilo del paquete o tematizado.
        val system = systemIcon(component)
        val packStyle = if (pack.isNotEmpty() && packFallback) packs.styleOf(pack) else null
        val image = when {
            system != null && packStyle != null -> renderWithPackStyle(system, packStyle, pack, app.packageName, px)
            system is AdaptiveIconDrawable && themed != null -> renderThemed(system, themed!!, px) ?: render(system, px)
            else -> render(system, px)
        }
        return image.also { cache.put(key, it) }
    }

    /** Vista previa de un icono personalizado (selector de iconos). Bloqueante. */
    fun loadOverride(override: String): ImageBitmap {
        val key = "ov|$override"
        cache.get(key)?.let { return it }
        return render(overrideDrawable(override), sizePx).also { cache.put(key, it) }
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

    private fun render(drawable: Drawable?, px: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        if (drawable != null) {
            val canvas = Canvas(bitmap)
            if (drawable is AdaptiveIconDrawable) {
                // Las capas se dibujan sin la máscara del sistema, a lienzo completo: la forma la pone Areca.
                drawable.background?.apply { setBounds(0, 0, px, px); draw(canvas) }
                drawable.foreground?.apply { setBounds(0, 0, px, px); draw(canvas) }
            } else {
                // Icono plano: ocupa la zona visible estándar (el centro, 2/3 del lienzo).
                val inset = px / 6
                drawable.setBounds(inset, inset, px - inset, px - inset)
                drawable.draw(canvas)
            }
        }
        return bitmap.asImageBitmap()
    }

    /**
     * Icono monocromo de Android 13 sobre el fondo [Tint.tile], con el dibujo en [Tint.glyph].
     * Devuelve null si el icono no trae capa monocroma (entonces se deja el icono original, como hace Android).
     */
    private fun renderThemed(icon: AdaptiveIconDrawable, tint: Tint, px: Int): ImageBitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val mono = icon.monochrome?.mutate() ?: return null
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(tint.tile)
        mono.setTint(tint.glyph)
        mono.setBounds(0, 0, px, px)
        mono.draw(canvas)
        return bitmap.asImageBitmap()
    }

    /**
     * Recompone un icono que el paquete no cubre, como hacen ADW y Nova: fondo (iconback, uno según la app),
     * el icono de la app reducido por el factor de escala y recortado con la máscara, y la capa superior.
     * Todo ocupa la zona visible estándar, igual que los iconos del propio paquete.
     */
    private fun renderWithPackStyle(source: Drawable, style: IconPackManager.Style, pack: String, packageName: String, px: Int): ImageBitmap {
        val side = (px * VIEWPORT).toInt()
        val tile = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        fun draw(name: String?, target: Canvas = canvas) {
            val d = name?.let { packs.drawableByName(pack, it) } ?: return
            d.setBounds(0, 0, side, side)
            d.draw(target)
        }
        if (style.backs.isNotEmpty()) draw(style.backs[Math.floorMod(packageName.hashCode(), style.backs.size)])

        // El icono de la app, en su propia capa para poder recortarlo con la máscara sin tocar el fondo.
        val appLayer = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val appCanvas = Canvas(appLayer)
        val content = (side * style.scale).toInt()
        val left = (side - content) / 2
        if (source is AdaptiveIconDrawable) {
            // Las capas adaptables miden 108 dp y solo se ve el centro (72 dp): se amplían para que ese centro llene [content].
            val full = content * 3 / 2
            val start = (side - full) / 2
            source.background?.apply { setBounds(start, start, start + full, start + full); draw(appCanvas) }
            source.foreground?.apply { setBounds(start, start, start + full, start + full); draw(appCanvas) }
        } else {
            source.setBounds(left, left, left + content, left + content)
            source.draw(appCanvas)
        }
        if (style.mask != null) {
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
            val mask = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            draw(style.mask, Canvas(mask))
            appCanvas.drawBitmap(mask, 0f, 0f, maskPaint)
        }
        canvas.drawBitmap(appLayer, 0f, 0f, null)
        draw(style.upon)

        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val offset = (px - side) / 2f
        Canvas(bitmap).drawBitmap(tile, offset, offset, Paint(Paint.FILTER_BITMAP_FLAG))
        return bitmap.asImageBitmap()
    }

    /** Fondo oscuro y hasta cuatro miniaturas de las apps de la carpeta, centradas según cuántas haya. */
    private fun renderFolder(folder: AppInfo, pack: String, px: Int, compact: Boolean): ImageBitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val half = px / 2f
        canvas.scale(VIEWPORT, VIEWPORT, half, half) // la carpeta ocupa la zona visible estándar
        canvas.drawCircle(half, half, half, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FOLDER_BACKGROUND })
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val members = folder.folderMembers.take(4)
        // Las miniaturas se cargan en la misma versión (completa o compacta), así que miden px.
        val margin = ((1 - VIEWPORT) / 2 * px).toInt()
        val memberSource = Rect(margin, margin, px - margin, px - margin) // zona visible de cada miniatura
        // Filas de miniaturas: 1 → [1]; 2 → [2]; 3 → [2, 1]; 4 → [2, 2].
        val rows = when (members.size) {
            0 -> emptyList()
            1 -> listOf(1)
            2 -> listOf(2)
            3 -> listOf(2, 1)
            else -> listOf(2, 2)
        }
        val cell = px * (if (members.size == 1) 0.52f else 0.34f)
        val gap = px * 0.06f
        val blockHeight = rows.size * cell + (rows.size - 1) * gap
        var index = 0
        rows.forEachIndexed { r, count ->
            val top = (px - blockHeight) / 2f + r * (cell + gap)
            val rowWidth = count * cell + (count - 1) * gap
            repeat(count) { c ->
                val left = (px - rowWidth) / 2f + c * (cell + gap)
                val rect = RectF(left, top, left + cell, top + cell)
                val member = members[index++]
                val icon = load(member, pack, overrideOf(member.key), compact).asAndroidBitmap()
                canvas.save()
                canvas.clipPath(Path().apply { addOval(rect, Path.Direction.CW) })
                canvas.drawBitmap(icon, memberSource, rect, paint)
                canvas.restore()
            }
        }
        return bitmap.asImageBitmap()
    }

    private fun cacheKey(app: AppInfo, pack: String, override: String?, compact: Boolean = false): String {
        val members = if (app.isFolder) app.folderMembers.joinToString(",") { it.key + "=" + overrideOf(it.key).orEmpty() } else ""
        return "$pack|${override.orEmpty()}|${app.key}|$members|$styleKey|${if (compact) "c" else "f"}"
    }

    private companion object {
        const val COMPACT_DP = 96
        const val CACHE_BYTES = 48 * 1024 * 1024
        const val VIEWPORT = 2f / 3f
        const val FOLDER_BACKGROUND = 0xE61B1D22.toInt()
    }
}
