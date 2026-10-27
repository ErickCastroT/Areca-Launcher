package com.areca.launcher.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cuántas notificaciones activas tiene cada app (por paquete). Solo cuentas: nunca se guarda su contenido. */
object NotificationBadges {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun update(counts: Map<String, Int>) {
        _counts.value = counts
    }
}
