package com.areca.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lista de apps lanzables del usuario actual; se refresca al instalar/desinstalar. */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val user = Process.myUserHandle()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    /** Actividades de la última consulta; evita consultar al sistema una vez por cada icono. */
    @Volatile private var activities: Map<ComponentName, LauncherActivityInfo> = emptyMap()

    fun activityInfo(component: ComponentName): LauncherActivityInfo? = activities[component]

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        refresh()
        launcherApps.registerCallback(callback)
    }

    fun refresh() {
        scope.launch {
            val list = launcherApps.getActivityList(null, user)
            activities = list.associateBy { it.componentName }
            _apps.value = list
                .map {
                    val cn = it.componentName
                    AppInfo(
                        key = cn.flattenToString(),
                        label = it.label.toString(),
                        packageName = cn.packageName,
                        className = cn.className,
                        isSystem = it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    fun launch(app: AppInfo) {
        runCatching {
            launcherApps.startMainActivity(ComponentName(app.packageName, app.className), user, null, null)
        }
    }

    /**
     * Atajos de la app (cámara → selfie, etc.). Android solo los entrega al launcher predeterminado;
     * si Areca no lo es, devuelve una lista vacía.
     */
    fun shortcuts(app: AppInfo): List<ShortcutInfo> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        return runCatching {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            launcherApps.getShortcuts(query, user)?.filter { it.isEnabled }?.take(4) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun startShortcut(shortcut: ShortcutInfo) {
        runCatching { launcherApps.startShortcut(shortcut, null, null) }
    }

    /** Abre el diálogo del sistema para desinstalar; la lista se actualiza sola al terminar. */
    fun uninstall(app: AppInfo) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openAppInfo(app: AppInfo) {
        runCatching {
            launcherApps.startAppDetailsActivity(ComponentName(app.packageName, app.className), user, null, null)
        }
    }
}
