package com.areca.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.areca.launcher.ui.home.bottomSwipeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.areca.launcher.data.IconLoader
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areca.launcher.ui.settings.brush
import com.areca.launcher.ui.apps.AppsScreen
import com.areca.launcher.ui.home.HomeScreen
import com.areca.launcher.ui.settings.AboutScreen
import com.areca.launcher.ui.settings.SettingsScreen
import com.areca.launcher.ui.widgets.WidgetMenuSheet
import com.areca.launcher.ui.widgets.WidgetPickerDialog

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ArecaRoot(vm: HomeViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val iconTarget by vm.iconEditTarget.collectAsStateWithLifecycle()
    val menuOpen by vm.homeMenuOpen.collectAsStateWithLifecycle()
    val widgetPicker by vm.widgetPickerOpen.collectAsStateWithLifecycle()
    val wallpaperDialog by vm.wallpaperDialogOpen.collectAsStateWithLifecycle()
    val a11yPrompt by vm.accessibilityPrompt.collectAsStateWithLifecycle()
    val widgetMenu by vm.widgetMenuId.collectAsStateWithLifecycle()
    val openFolder by vm.openFolder.collectAsStateWithLifecycle()
    val folderChooser by vm.folderChooserApp.collectAsStateWithLifecycle()
    val renameFolder by vm.renameFolderKey.collectAsStateWithLifecycle()

    BackHandler(enabled = screen != Screen.HOME) { if (screen == Screen.ABOUT) vm.open(Screen.SETTINGS) else vm.goHome() }

    val background = settings.wallpaper.brush()

    // Los iconos tematizados toman su color del tema y el acento vigentes.
    val scheme = MaterialTheme.colorScheme
    val tint = remember(settings.themedIcons, scheme.primary, scheme.background) {
        if (!settings.themedIcons) null else {
            val dark = scheme.background.luminance() < 0.5f
            IconLoader.Tint(
                tile = (if (dark) lerp(Color.Black, scheme.primary, 0.28f) else lerp(Color.White, scheme.primary, 0.2f)).toArgb(),
                glyph = (if (dark) lerp(scheme.primary, Color.White, 0.55f) else lerp(scheme.primary, Color.Black, 0.35f)).toArgb(),
            )
        }
    }
    SideEffect {
        vm.iconLoader.themed = tint
        vm.iconLoader.packFallback = settings.packFallback
    }

    // Zona del gesto "apps abiertas": la franja del sistema más 80 dp (el sistema reconoce solo ~20 dp).
    val density = LocalDensity.current
    val bandPx = WindowInsets.navigationBars.getBottom(density) + with(density) { 80.dp.toPx() }
    Box(
        Modifier.fillMaxSize().background(background).bottomSwipeUp(
            enabled = settings.bottomSwipeRecents && screen == Screen.HOME,
            bandHeightPx = bandPx,
            triggerDistance = 56.dp,
            onTrigger = vm::openRecentApps,
        ),
    ) {
        WallpaperBackdrop(settings, vm)
        // La transición responde a la acción: al abrir, la pantalla sube desde abajo; al volver, se retira.
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState == Screen.HOME) {
                    fadeIn(tween(240)) togetherWith (slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { it / 10 } + fadeOut(tween(200)))
                } else {
                    (slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 10 } + fadeIn(tween(240))) togetherWith fadeOut(tween(160))
                }
            },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.HOME -> HomeScreen(vm)
                Screen.APPS -> AppsScreen(vm)
                Screen.SETTINGS -> SettingsScreen(vm)
                Screen.ABOUT -> AboutScreen(vm)
            }
        }
    }

    if (menuOpen) {
        HomeMenuSheet(
            onDismiss = vm::closeHomeMenu,
            onAddWidget = vm::openWidgetPicker,
            onPickApps = { vm.open(Screen.APPS) },
            onSettings = { vm.open(Screen.SETTINGS) },
            onWallpaper = vm::openWallpaperDialog,
        )
    }
    if (widgetPicker) WidgetPickerDialog(vm)
    widgetMenu?.let { WidgetMenuSheet(vm, it) }
    if (wallpaperDialog) WallpaperDialog(vm::closeWallpaperDialog)
    if (a11yPrompt) AccessibilityPromptDialog(vm::dismissAccessibilityPrompt)
    iconTarget?.let { IconPickerDialog(vm, it) }
    openFolder?.let { FolderDialog(vm, it) }
    folderChooser?.let { FolderChooserDialog(vm, it) }
    renameFolder?.let { RenameFolderDialog(vm, it) }
}
