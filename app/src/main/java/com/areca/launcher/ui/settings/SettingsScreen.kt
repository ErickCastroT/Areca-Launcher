package com.areca.launcher.ui.settings

import android.os.Build
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areca.launcher.BuildConfig
import com.areca.launcher.R
import com.areca.launcher.ui.home.hasAccelerometer
import com.areca.launcher.ui.MAX_ICON_ZOOM
import com.areca.launcher.ui.MIN_ICON_ZOOM
import com.areca.launcher.data.LanguageStore
import com.areca.launcher.data.GridLayout
import com.areca.launcher.data.IconShape
import com.areca.launcher.data.ThemeMode
import com.areca.launcher.data.MIN_TILT_STRENGTH
import com.areca.launcher.ui.HomeViewModel
import com.areca.launcher.ui.Screen
import com.areca.launcher.ui.theme.toShape

/**
 * Ajustes: la vista previa queda fija arriba para ver cada cambio al instante;
 * debajo, los controles agrupados por lo que cambian (burbujas, apariencia, pantalla, apps, sistema).
 */
@Composable
fun SettingsScreen(vm: HomeViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var showPacks by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val packs = remember { vm.iconPacks() }
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val isDefault = remember(resumes) { isDefaultLauncher(context) }
    val listenerEnabled = remember(resumes) {
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val a11yActive = remember(resumes) { com.areca.launcher.data.SystemGestures.accessibilityActive }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri) { ok ->
            Toast.makeText(context, if (ok) R.string.backup_saved else R.string.backup_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri) { backup ->
            Toast.makeText(context, if (backup != null) R.string.backup_restored else R.string.backup_invalid, Toast.LENGTH_SHORT).show()
            if (backup != null) context.findActivity()?.recreate() // el idioma restaurado se aplica al recrear
        }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resumes++ }

    // Subvista abierta: se conserva al girar el teléfono o cambiar el idioma.
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }
    val permissionsMissing = listOf(isDefault, a11yActive, listenerEnabled).count { !it }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().displayCutoutPadding(),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 24.dp, top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (page == SettingsPage.MAIN) vm.goHome() else page = SettingsPage.MAIN }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            Text(stringResource(page.titleRes), style = MaterialTheme.typography.headlineMedium)
        }

        SettingsPanes(
            wide = LocalConfiguration.current.screenWidthDp >= 720,
            preview = if (page.showsPreview) ({ modifier -> BubblePreview(vm, s, modifier) }) else null,
        ) {
            when (page) {
            SettingsPage.MAIN -> SettingsMain(page = { page = it }, permissionsMissing = permissionsMissing, vm = vm)
            SettingsPage.BUBBLES -> {
                SettingsSection(null) {
                    SegmentedChoice(
                        options = listOf(stringResource(R.string.settings_layout_honeycomb), stringResource(R.string.settings_layout_rows)),
                        selected = if (s.layout == GridLayout.HONEYCOMB) 0 else 1,
                        onSelect = { vm.setLayout(if (it == 0) GridLayout.HONEYCOMB else GridLayout.ROWS) },
                    )
                    Text(stringResource(R.string.label_shape), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconShape.entries.forEach { option ->
                            ShapeSwatch(option.toShape(), option == s.iconShape) { vm.setIconShape(option) }
                        }
                    }
                    // Límites según el ancho de la pantalla: la fila más ancha debe caber.
                    val availableDp = LocalConfiguration.current.screenWidthDp - 2 * SCREEN_SIDE_DP
                    val maxSize = steppedMax(MIN_SIZE, MAX_SIZE, SIZE_STEP, (availableDp - s.spacingDp * (s.columns - 1)) / s.columns)
                    val maxSpacing = steppedMax(0, MAX_SPACING, SPACING_STEP, (availableDp - s.iconSizeDp * s.columns) / (s.columns - 1))
                    val maxColumns = ((availableDp - s.iconSizeDp) / (s.iconSizeDp + s.spacingDp) + 1).coerceIn(3, MAX_COLUMNS)
                    SliderRow(
                        label = stringResource(R.string.label_size),
                        valueText = stringResource(R.string.value_dp, s.iconSizeDp),
                        value = s.iconSizeDp.toFloat(),
                        range = MIN_SIZE.toFloat()..maxSize.toFloat(),
                        steps = (maxSize - MIN_SIZE) / SIZE_STEP - 1,
                        onChange = { vm.setIconSize(it.toInt()) },
                    )
                    SliderRow(
                        label = stringResource(R.string.label_spacing),
                        valueText = stringResource(R.string.value_dp, s.spacingDp),
                        value = s.spacingDp.toFloat(),
                        range = 0f..maxSpacing.toFloat(),
                        steps = maxSpacing / SPACING_STEP - 1,
                        onChange = { vm.setSpacing(it.toInt()) },
                    )
                    SliderRow(
                        label = stringResource(R.string.label_padding),
                        valueText = stringResource(R.string.value_percent, s.iconPaddingPercent),
                        value = s.iconPaddingPercent.toFloat(),
                        range = 0f..50f,
                        steps = 9, // de 5 en 5
                        onChange = { vm.setIconPadding(it.toInt()) },
                    )
                    SliderRow(
                        label = stringResource(R.string.label_zoom),
                        valueText = stringResource(R.string.value_percent, s.iconZoomPercent),
                        value = s.iconZoomPercent.toFloat(),
                        range = MIN_ICON_ZOOM.toFloat()..MAX_ICON_ZOOM.toFloat(),
                        steps = (MAX_ICON_ZOOM - MIN_ICON_ZOOM) / 5 - 1, // de 5 en 5
                        onChange = { vm.setIconZoom(it.toInt()) },
                    )
                    Text(
                        stringResource(R.string.label_zoom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SliderRow(
                        label = stringResource(R.string.label_columns),
                        valueText = s.columns.toString(),
                        value = s.columns.toFloat(),
                        range = 2f..maxColumns.toFloat(),
                        steps = maxColumns - 3,
                        onChange = { vm.setColumns(it.toInt()) },
                    )
                    if (hasAccelerometer(LocalContext.current)) {
                        ToggleRow(
                            title = stringResource(R.string.settings_tilt),
                            subtitle = stringResource(R.string.settings_tilt_hint),
                            checked = s.tiltEnabled,
                            onChange = vm::setTiltEnabled,
                        )
                        if (s.tiltEnabled) {
                            SliderRow(
                                label = stringResource(R.string.settings_tilt_strength),
                                valueText = "${s.tiltStrength}%",
                                value = s.tiltStrength.toFloat(),
                                range = MIN_TILT_STRENGTH.toFloat()..100f,
                                steps = (100 - MIN_TILT_STRENGTH) / 10 - 1,
                                onChange = { vm.setTiltStrength((it / 10).toInt() * 10) },
                            )
                        }
                    }
                }
            }
            SettingsPage.LOOK -> {
                SettingsSection(null) {
                    ValueRow(
                        title = stringResource(R.string.settings_icon_pack),
                        value = packs.firstOrNull { it.packageName == s.iconPack }?.label
                            ?: stringResource(R.string.settings_icon_pack_default),
                        onClick = { showPacks = true },
                    )
                    ValueRow(
                        title = stringResource(R.string.reset_icons_title),
                        value = (s.iconOverrides.keys + s.iconZooms.keys).size.let { changed ->
                            if (changed == 0) stringResource(R.string.reset_icons_none) else stringResource(R.string.reset_icons_count, changed)
                        },
                        onClick = { if (s.iconOverrides.isNotEmpty() || s.iconZooms.isNotEmpty()) confirmReset = true },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ToggleRow(
                            title = stringResource(R.string.themed_icons_title),
                            subtitle = stringResource(R.string.themed_icons_hint),
                            checked = s.themedIcons,
                            onChange = vm::setThemedIcons,
                        )
                    }
                    if (s.iconPack.isNotEmpty()) {
                        ToggleRow(
                            title = stringResource(R.string.pack_fallback_title),
                            subtitle = stringResource(R.string.pack_fallback_hint),
                            checked = s.packFallback,
                            onChange = vm::setPackFallback,
                        )
                    }
                    Text(stringResource(R.string.label_theme), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    SegmentedChoice(
                        options = listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_dark), stringResource(R.string.theme_amoled)),
                        selected = s.themeMode.ordinal,
                        onSelect = { vm.setThemeMode(ThemeMode.entries[it]) },
                    )
                    Text(
                        stringResource(
                            when (s.themeMode) {
                                ThemeMode.SYSTEM -> R.string.theme_system_hint
                                ThemeMode.DARK -> R.string.theme_dark_hint
                                ThemeMode.AMOLED -> R.string.theme_amoled_hint
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.label_accent), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    AccentChoice(selected = s.accent, onSelect = vm::setAccent)
                    ValueRow(
                        title = stringResource(R.string.settings_wallpaper_title),
                        value = stringResource(R.string.settings_wallpaper_hint),
                        onClick = vm::openWallpaperDialog,
                    )
                    Text(stringResource(R.string.label_background), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    WallpaperChoice(vm, s)
                }
            }
            SettingsPage.SCREEN -> {
                SettingsSection(null) {
                    ToggleRow(
                        title = stringResource(R.string.settings_hide_status_bar),
                        subtitle = stringResource(R.string.settings_hide_status_bar_hint),
                        checked = s.hideStatusBar,
                        onChange = vm::setHideStatusBar,
                    )
                    ToggleRow(
                        title = stringResource(R.string.settings_show_clock),
                        checked = s.showClock,
                        onChange = vm::setShowClock,
                    )
                    if (s.showClock) {
                        SegmentedChoice(
                            options = listOf(stringResource(R.string.clock_top), stringResource(R.string.clock_bottom)),
                            selected = if (s.clockY < 0.5f) 0 else 1,
                            onSelect = { vm.setClockY(if (it == 0) 0.02f else 1f) },
                        )
                        Text(stringResource(R.string.settings_clock_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SettingsPage.GESTURES -> {
                SettingsSection(null) {
                    ToggleRow(
                        title = stringResource(R.string.gesture_double_tap_title),
                        subtitle = stringResource(R.string.gesture_double_tap_hint),
                        checked = s.doubleTapLock,
                        onChange = vm::setDoubleTapLock,
                    )
                    ToggleRow(
                        title = stringResource(R.string.gesture_bottom_title),
                        subtitle = stringResource(R.string.gesture_bottom_hint),
                        checked = s.bottomSwipeRecents,
                        onChange = vm::setBottomSwipeRecents,
                    )
                    ToggleRow(
                        title = stringResource(R.string.gesture_swipe_title),
                        subtitle = stringResource(R.string.gesture_swipe_hint),
                        checked = s.swipeDownNotifications,
                        onChange = vm::setSwipeDownNotifications,
                    )
                    ValueRow(
                        title = stringResource(R.string.gesture_a11y_title),
                        value = stringResource(if (a11yActive) R.string.gesture_a11y_on else R.string.gesture_a11y_off),
                        onClick = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
            }
            SettingsPage.APPS -> {
                SettingsSection(null) {
                    ValueRow(
                        title = stringResource(R.string.settings_apps_title),
                        value = stringResource(R.string.settings_apps_hint),
                        onClick = { vm.open(Screen.APPS) },
                    )
                    val openListenerSettings = {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    ToggleRow(
                        title = stringResource(R.string.settings_show_badges),
                        subtitle = stringResource(R.string.settings_show_badges_hint),
                        checked = s.showBadges,
                        onChange = { on ->
                            vm.setShowBadges(on)
                            // Android pide el acceso a notificaciones en sus propios ajustes.
                            if (on && !listenerEnabled) openListenerSettings()
                        },
                    )
                    if (s.showBadges && !listenerEnabled) {
                        ValueRow(
                            title = stringResource(R.string.settings_show_badges_needs_access),
                            value = stringResource(R.string.settings_show_badges_needs_access_hint),
                            onClick = openListenerSettings,
                        )
                    }
                    ToggleRow(
                        title = stringResource(R.string.settings_show_recents),
                        subtitle = stringResource(R.string.settings_show_recents_hint),
                        checked = s.showRecents,
                        onChange = vm::setShowRecents,
                    )
                }
            }
            SettingsPage.PERMISSIONS -> {
                SettingsSection(null) {
                    PermissionRow(
                        title = stringResource(R.string.perm_default_launcher),
                        description = stringResource(R.string.perm_default_launcher_hint),
                        granted = isDefault,
                        onClick = { requestDefaultLauncher(context, roleLauncher) },
                    )
                    PermissionRow(
                        title = stringResource(R.string.perm_accessibility),
                        description = stringResource(R.string.perm_accessibility_hint),
                        granted = a11yActive,
                        onClick = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                    PermissionRow(
                        title = stringResource(R.string.perm_notifications),
                        description = stringResource(R.string.perm_notifications_hint),
                        granted = listenerEnabled,
                        onClick = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                    // Android 13+ bloquea estas opciones en apps instaladas desde un archivo hasta que se permiten aquí.
                    ValueRow(
                        title = stringResource(R.string.perm_restricted),
                        value = stringResource(R.string.perm_restricted_hint),
                        onClick = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(android.net.Uri.fromParts("package", context.packageName, null))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
            }
            SettingsPage.SYSTEM -> {
                SettingsSection(null) {
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                    val languages = listOf(LanguageStore.SYSTEM, LanguageStore.SPANISH, LanguageStore.ENGLISH)
                    var language by remember { mutableStateOf(LanguageStore.get(context)) }
                    SegmentedChoice(
                        options = listOf(stringResource(R.string.language_system), "Español", "English"),
                        selected = languages.indexOf(language).coerceAtLeast(0),
                        onSelect = {
                            val tag = languages[it]
                            if (tag != language) {
                                language = tag
                                LanguageStore.set(context, tag)
                                context.findActivity()?.recreate() // vuelve a cargar los textos en el idioma elegido
                            }
                        },
                    )
                    ValueRow(
                        title = stringResource(R.string.backup_export_title),
                        value = stringResource(R.string.backup_export_hint),
                        onClick = { exportLauncher.launch("areca-backup.json") },
                    )
                    ValueRow(
                        title = stringResource(R.string.backup_import_title),
                        value = stringResource(R.string.backup_import_hint),
                        onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                    )
                }
            }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.reset_icons_title), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.reset_icons_body), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { vm.resetIcons(); confirmReset = false }) {
                    Text(stringResource(R.string.reset_icons_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showPacks) {
        AlertDialog(
            onDismissRequest = { showPacks = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            confirmButton = { TextButton(onClick = { showPacks = false }) { Text(stringResource(R.string.settings_close)) } },
            title = { Text(stringResource(R.string.settings_icon_pack), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PackOption(stringResource(R.string.settings_icon_pack_default), s.iconPack.isEmpty()) {
                        vm.setIconPack("")
                        showPacks = false
                    }
                    packs.forEach { pack ->
                        PackOption(pack.label, s.iconPack == pack.packageName) {
                            vm.setIconPack(pack.packageName)
                            showPacks = false
                        }
                    }
                    if (packs.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_icon_pack_none_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun PackOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

private const val MIN_SIZE = 48
private const val MAX_SIZE = 160
private const val SIZE_STEP = 4
private const val MAX_SPACING = 96
private const val SPACING_STEP = 4
private const val MAX_COLUMNS = 8
private const val SCREEN_SIDE_DP = 8

/** Mayor valor de la rejilla (min + n·step) que no pasa de [limit] ni de [cap]; nunca menor que un paso sobre [min]. */
private fun steppedMax(min: Int, cap: Int, step: Int, limit: Int): Int =
    (min + (limit.coerceIn(min, cap) - min) / step * step).coerceAtLeast(min + step)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Subvistas de Ajustes. */
enum class SettingsPage(val titleRes: Int, val showsPreview: Boolean = false) {
    MAIN(R.string.settings_title),
    BUBBLES(R.string.settings_section_bubbles, showsPreview = true),
    LOOK(R.string.settings_section_look, showsPreview = true),
    SCREEN(R.string.settings_section_screen),
    GESTURES(R.string.settings_section_gestures),
    APPS(R.string.settings_section_apps),
    PERMISSIONS(R.string.settings_section_permissions),
    SYSTEM(R.string.settings_section_system),
}

/** Lista de categorías: cada una abre su subvista; Permisos avisa cuántos faltan. */
@Composable
private fun SettingsMain(page: (SettingsPage) -> Unit, permissionsMissing: Int, vm: HomeViewModel) {
    CategoryRow(stringResource(R.string.settings_section_bubbles), stringResource(R.string.cat_bubbles_hint)) { page(SettingsPage.BUBBLES) }
    CategoryRow(stringResource(R.string.settings_section_look), stringResource(R.string.cat_look_hint)) { page(SettingsPage.LOOK) }
    CategoryRow(stringResource(R.string.settings_section_screen), stringResource(R.string.cat_screen_hint)) { page(SettingsPage.SCREEN) }
    CategoryRow(stringResource(R.string.settings_section_gestures), stringResource(R.string.cat_gestures_hint)) { page(SettingsPage.GESTURES) }
    CategoryRow(stringResource(R.string.settings_section_apps), stringResource(R.string.cat_apps_hint)) { page(SettingsPage.APPS) }
    CategoryRow(
        stringResource(R.string.settings_section_permissions),
        if (permissionsMissing == 0) stringResource(R.string.cat_permissions_ok) else stringResource(R.string.cat_permissions_missing, permissionsMissing),
        highlight = permissionsMissing > 0,
    ) { page(SettingsPage.PERMISSIONS) }
    CategoryRow(stringResource(R.string.settings_section_system), stringResource(R.string.cat_system_hint)) { page(SettingsPage.SYSTEM) }
    CategoryRow(stringResource(R.string.settings_about_title), "${BuildConfig.VERSION_NAME} · ${BuildConfig.CODENAME}") { vm.open(Screen.ABOUT) }
}
