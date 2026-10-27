package com.areca.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context

/** Aloja widgets de otras apps. Los ids se guardan en los ajustes; el host los mantiene vivos. */
class WidgetController(context: Context) {

    private val manager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)

    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun release(id: Int) = host.deleteAppWidgetId(id)

    /** true si Areca ya tiene permiso para enlazar este proveedor; si no, hay que pedirlo al usuario. */
    fun bindIfAllowed(id: Int, provider: ComponentName): Boolean = manager.bindAppWidgetIdIfAllowed(id, provider)

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return host.createView(context, id, info).apply { setAppWidget(id, info) }
    }

    fun startConfigure(activity: android.app.Activity, id: Int, requestCode: Int) {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
    }

    fun startListening() = host.startListening()
    fun stopListening() = host.stopListening()

    private companion object {
        const val HOST_ID = 1024
    }
}
