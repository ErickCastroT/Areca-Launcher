package com.areca.launcher.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Idioma de la app. Se guarda en SharedPreferences (no en DataStore) porque hay que leerlo
 * de forma síncrona al crear la activity, antes de que cargue cualquier texto.
 * Valores: "" = el del sistema, "es" o "en".
 */
object LanguageStore {
    const val SYSTEM = ""
    const val SPANISH = "es"
    const val ENGLISH = "en"

    private const val PREFS = "areca_language"
    private const val KEY = "tag"

    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    /** Contexto con el idioma elegido; si se deja en "sistema", se devuelve tal cual. */
    fun wrap(base: Context): Context {
        val tag = get(base)
        if (tag == SYSTEM) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
