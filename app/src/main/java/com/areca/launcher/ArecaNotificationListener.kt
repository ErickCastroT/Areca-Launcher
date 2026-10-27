package com.areca.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.areca.launcher.data.NotificationBadges

/**
 * Opcional: cuenta las notificaciones de cada app para mostrarlas como insignia en las burbujas.
 * Solo lee el paquete de cada notificación y si es ongoing o un resumen; no guarda título ni texto.
 */
class ArecaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() = refresh()

    override fun onListenerDisconnected() = NotificationBadges.update(emptyMap())

    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        NotificationBadges.update(
            active
                .filter { !it.isOngoing && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .groupingBy { it.packageName }
                .eachCount(),
        )
    }
}
