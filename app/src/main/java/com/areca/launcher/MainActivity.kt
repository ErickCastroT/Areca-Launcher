package com.areca.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import com.areca.launcher.data.LanguageStore
import com.areca.launcher.data.ThemeMode
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.areca.launcher.ui.ArecaRoot
import com.areca.launcher.ui.HomeViewModel
import com.areca.launcher.ui.Screen
import com.areca.launcher.ui.theme.ArecaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private val widgets get() = viewModel.widgets

    // Widget en proceso de alta (enlazar → configurar → guardar).
    private var pendingWidgetId = -1
    private var pendingProvider: AppWidgetProviderInfo? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            // Iconos de las barras del sistema con el contraste que pide el tema actual.
            val darkBackdrop by viewModel.darkHomeBackdrop.collectAsStateWithLifecycle()
            val darkUi = settings.themeMode != ThemeMode.SYSTEM || isSystemInDarkTheme() || darkBackdrop
            DisposableEffect(darkUi) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkUi) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (darkUi) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose { }
            }
            ArecaTheme(settings.themeMode, settings.accent) { ArecaRoot(viewModel) }
        }
        if (savedInstanceState == null) handleIntent(intent)
        viewModel.widgetStarter = ::beginAddWidget
        viewModel.widgetConfigurer = { id -> widgets.startConfigure(this, id, REQUEST_RECONFIGURE_WIDGET) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.statusBarHidden.collect { applyStatusBar(it) } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onStop() {
        widgets.stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        viewModel.widgetStarter = null
        viewModel.widgetConfigurer = null
        super.onDestroy()
    }

    private fun beginAddWidget(provider: AppWidgetProviderInfo) {
        val id = widgets.allocateId()
        pendingWidgetId = id
        pendingProvider = provider
        if (widgets.bindIfAllowed(id, provider.provider)) {
            onWidgetBound()
        } else {
            // Android pide al usuario permitir que Areca muestre este widget.
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_BIND_WIDGET)
        }
    }

    private fun onWidgetBound() {
        if (pendingProvider?.configure != null) widgets.startConfigure(this, pendingWidgetId, REQUEST_CONFIGURE_WIDGET)
        else commitWidget()
    }

    private fun commitWidget() {
        if (pendingWidgetId != -1) viewModel.addWidgetId(pendingWidgetId)
        pendingWidgetId = -1
        pendingProvider = null
    }

    private fun cancelWidget() {
        if (pendingWidgetId != -1) widgets.release(pendingWidgetId)
        pendingWidgetId = -1
        pendingProvider = null
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND_WIDGET -> if (resultCode == RESULT_OK) onWidgetBound() else cancelWidget()
            REQUEST_CONFIGURE_WIDGET -> if (resultCode == RESULT_OK) commitWidget() else cancelWidget()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Botón Home: escritorio. Icono de Areca en el listado de apps: Ajustes (no hay iconos fijos en pantalla). */
    private fun handleIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_MAIN) return
        when {
            intent.hasCategory(Intent.CATEGORY_HOME) -> viewModel.goHome()
            intent.hasCategory(Intent.CATEGORY_LAUNCHER) -> viewModel.open(Screen.SETTINGS)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // El sistema puede volver a mostrar la barra (p. ej. tras un swipe); se reaplica el ajuste.
        if (hasFocus && viewModel.statusBarHidden.value) applyStatusBar(true)
    }

    private fun applyStatusBar(hidden: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    private companion object {
        const val REQUEST_RECONFIGURE_WIDGET = 103
        const val REQUEST_BIND_WIDGET = 101
        const val REQUEST_CONFIGURE_WIDGET = 102
    }
}
