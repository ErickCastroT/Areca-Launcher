package com.areca.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.areca.launcher.R

/** Menú del escritorio: se abre manteniendo pulsado un espacio vacío (ya no hay iconos fijos en pantalla). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMenuSheet(
    onDismiss: () -> Unit,
    onAddWidget: () -> Unit,
    onPickApps: () -> Unit,
    onSettings: () -> Unit,
    onWallpaper: () -> Unit,
) {
    ModalBottomSheet(
        // Abre completa (en horizontal la mitad no alcanza) y el contenido se desplaza si aun así no cabe.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.surfaceVariant) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            MenuEntry(stringResource(R.string.home_menu_add_widget), stringResource(R.string.home_menu_add_widget_hint)) {
                onDismiss()
                onAddWidget()
            }
            MenuEntry(stringResource(R.string.home_menu_pick_apps), stringResource(R.string.home_menu_pick_apps_hint)) {
                onDismiss()
                onPickApps()
            }
            MenuEntry(stringResource(R.string.home_menu_wallpaper), stringResource(R.string.home_menu_wallpaper_hint)) {
                onDismiss()
                onWallpaper()
            }
            MenuEntry(stringResource(R.string.home_menu_settings), stringResource(R.string.home_menu_settings_hint)) {
                onDismiss()
                onSettings()
            }
        }
    }
}

@Composable
private fun MenuEntry(title: String, hint: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
