package com.areca.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Apps abiertas desde Areca, de la más reciente a la más antigua. Solo se guarda en el teléfono y
 * solo cuenta lo que se abre desde el launcher (no necesita permiso de uso de apps).
 */
class UsageStore(context: Context) {
    private val prefs = context.getSharedPreferences("areca_usage", Context.MODE_PRIVATE)
    private val _recent = MutableStateFlow(prefs.getString(KEY, "").orEmpty().split('\n').filter { it.isNotEmpty() })
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    fun record(appKey: String) {
        _recent.value = (listOf(appKey) + _recent.value.filter { it != appKey }).take(MAX)
        save()
    }

    fun clear() {
        _recent.value = emptyList()
        save()
    }

    private fun save() = prefs.edit().putString(KEY, _recent.value.joinToString("\n")).apply()

    private companion object {
        const val KEY = "recent"
        const val MAX = 20
    }
}
