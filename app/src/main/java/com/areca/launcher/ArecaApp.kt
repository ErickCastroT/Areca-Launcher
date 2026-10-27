package com.areca.launcher

import android.app.Application
import com.areca.launcher.data.AppRepository
import com.areca.launcher.data.IconLoader
import com.areca.launcher.data.ImageStore
import com.areca.launcher.data.IconPackManager
import com.areca.launcher.data.SettingsRepository
import com.areca.launcher.data.UsageStore
import com.areca.launcher.data.WidgetController

class ArecaApp : Application() {
    val settingsRepository by lazy { SettingsRepository(this) }
    val appRepository by lazy { AppRepository(this) }
    val iconPackManager by lazy { IconPackManager(this) }
    val iconLoader by lazy { IconLoader(this, iconPackManager, appRepository) }
    val usageStore by lazy { UsageStore(this) }
    val imageStore by lazy { ImageStore(this) }
    val widgetController by lazy { WidgetController(this) }
}
