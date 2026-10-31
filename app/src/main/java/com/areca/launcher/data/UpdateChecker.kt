package com.areca.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pregunta a GitHub cuál es el último release. Es la única conexión a internet de la app y solo ocurre cuando
 * el usuario pulsa "Buscar actualizaciones" o activa la búsqueda semanal; no envía nada del teléfono.
 */
object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/ErickCastroT/Areca-Launcher/releases/latest"
    private const val TIMEOUT_MS = 10_000

    sealed interface Result {
        data object UpToDate : Result
        data class Available(val version: String, val notes: String, val url: String) : Result
        data object Failed : Result
    }

    suspend fun check(current: String, spanish: Boolean): Result = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(LATEST).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "ArecaLauncher/$current")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching Result.Failed
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val version = json.getString("tag_name").removePrefix("v")
                if (!isNewer(version, current)) Result.UpToDate
                else Result.Available(version, whatsNew(json.optString("body"), spanish), json.getString("html_url"))
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(Result.Failed)
    }

    /** Compara versiones AA.MM.N número a número. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Las viñetas de novedades del idioma pedido; las notas de los releases traen una sección por idioma. */
    private fun whatsNew(body: String, spanish: Boolean): String {
        val lines = body.lines()
        val start = lines.indexOfFirst { it.startsWith("## ") && (if (spanish) "Español" in it else "English" in it) }
        if (start < 0) return ""
        return lines.drop(start + 1)
            .takeWhile { !it.startsWith("## ") && !it.startsWith("**Install") && !it.startsWith("**Instal") }
            .filter { it.startsWith("- ") }
            .joinToString("\n") { "• " + it.removePrefix("- ").replace("**", "").replace("`", "") }
    }
}
