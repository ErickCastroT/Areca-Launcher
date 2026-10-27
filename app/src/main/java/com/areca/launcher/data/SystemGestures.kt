package com.areca.launcher.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import com.areca.launcher.ArecaAccessibilityService

/** Acciones del sistema que disparan los gestos del escritorio. Devuelven false si no se pudo. */
object SystemGestures {

    val accessibilityActive: Boolean get() = ArecaAccessibilityService.instance != null

    fun lockScreen(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return ArecaAccessibilityService.instance
            ?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) == true
    }

    /** Abre la pantalla de apps abiertas. */
    fun openRecents(): Boolean = ArecaAccessibilityService.instance
        ?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) == true

    /** Primero la vía directa (permiso EXPAND_STATUS_BAR); si el sistema la bloquea, el servicio de accesibilidad. */
    fun openNotifications(context: Context): Boolean {
        val direct = runCatching {
            val statusBar = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(statusBar)
        }.isSuccess
        if (direct) return true
        return ArecaAccessibilityService.instance
            ?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) == true
    }
}
