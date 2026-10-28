package com.areca.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Guarda en el almacenamiento interno las imágenes elegidas por el usuario (iconos). */
class ImageStore(private val context: Context) {

    private fun dir(name: String) = File(context.filesDir, name).apply { mkdirs() }

    /** Recorta al centro en cuadrado y guarda como PNG de 192 px. Devuelve el nombre del archivo. */
    fun saveIcon(uri: Uri): String? {
        val source = decode(uri, 512) ?: return null
        val side = min(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val icon = Bitmap.createScaledBitmap(square, 192, 192, true)
        val name = "${UUID.randomUUID()}.png"
        File(dir("icons"), name).outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }

    /**
     * Guarda la imagen de fondo del escritorio, reducida al tamaño de la pantalla, como JPEG. Cada imagen lleva
     * un nombre nuevo para que la caché de la pantalla se renueve; la anterior se borra. Devuelve el nombre.
     */
    fun saveWallpaper(uri: Uri): String? {
        val metrics = context.resources.displayMetrics
        val bitmap = decode(uri, max(metrics.widthPixels, metrics.heightPixels)) ?: return null
        val name = "${UUID.randomUUID()}.jpg"
        val folder = dir("wallpapers")
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        folder.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
        return name
    }

    fun wallpaperFile(name: String): File = File(File(context.filesDir, "wallpapers"), name)

    /** Borra las imágenes de iconos personalizados desde galería. */
    fun clearIcons() {
        dir("icons").listFiles()?.forEach { it.delete() }
    }

    private fun decode(uri: Uri, maxDim: Int): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@runCatching null
        val scale = maxDim.toFloat() / max(decoded.width, decoded.height)
        if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded
    }.getOrNull()
}
