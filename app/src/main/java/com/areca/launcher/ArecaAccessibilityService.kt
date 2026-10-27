package com.areca.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * No observa nada: existe solo para que los gestos del escritorio puedan bloquear la pantalla y
 * abrir las notificaciones, acciones que Android únicamente permite a un servicio de accesibilidad.
 */
class ArecaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        var instance: ArecaAccessibilityService? = null
    }
}
