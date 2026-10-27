package com.areca.launcher.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

fun isDefaultLauncher(context: Context): Boolean {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val info = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
    return info?.activityInfo?.packageName == context.packageName
}

/** Pide al sistema que Areca sea el launcher predeterminado (o abre los ajustes de inicio). */
fun requestDefaultLauncher(context: Context, launcher: ActivityResultLauncher<Intent>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            launcher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
            return
        }
    }
    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
