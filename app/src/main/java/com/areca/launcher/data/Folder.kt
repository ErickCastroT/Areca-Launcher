package com.areca.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/** Las carpetas viven en la lista de favoritas con esta clave: "folder:<id>". */
const val FOLDER_PREFIX = "folder:"

/** Paquete ficticio de la burbuja de una carpeta (no es una app instalada). */
const val FOLDER_PACKAGE = "areca.folder"

/** Una carpeta de burbujas: sus apps se guardan por clave y no aparecen sueltas en el escritorio. */
data class Folder(val id: String, val name: String, val apps: List<String>) {
    val key: String get() = FOLDER_PREFIX + id
}

object FolderCodec {
    fun encode(folders: List<Folder>): String = JSONArray().apply {
        folders.forEach { f ->
            put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.apps)))
        }
    }.toString()

    fun decode(text: String?): List<Folder> = runCatching {
        val array = JSONArray(text ?: return emptyList())
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            val apps = o.getJSONArray("apps")
            Folder(o.getString("id"), o.getString("name"), List(apps.length()) { apps.getString(it) })
        }
    }.getOrDefault(emptyList())
}
