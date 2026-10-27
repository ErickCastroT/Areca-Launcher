package com.areca.launcher.ui

import android.content.pm.ShortcutInfo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.areca.launcher.R
import com.areca.launcher.data.AppInfo

/** Lo que se puede hacer con una app desde su menú (escritorio y listado). */
class AppMenuActions(
    val info: (AppInfo) -> Unit,
    val hide: (AppInfo) -> Unit,
    val editIcon: (AppInfo) -> Unit,
    val uninstall: (AppInfo) -> Unit,
    val loadShortcuts: suspend (AppInfo) -> List<ShortcutInfo>,
    val startShortcut: (ShortcutInfo) -> Unit,
    val addToFolder: (AppInfo) -> Unit,
    val renameFolder: (AppInfo) -> Unit,
    val dissolveFolder: (AppInfo) -> Unit,
)

/** Menú de una app: atajos propios de la app arriba, y debajo las acciones del launcher. */
@Composable
fun AppContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    app: AppInfo,
    actions: AppMenuActions,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    if (app.isFolder) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_rename)) },
                onClick = { onDismiss(); actions.renameFolder(app) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.folder_dissolve), color = MaterialTheme.colorScheme.error) },
                onClick = { onDismiss(); actions.dissolveFolder(app) },
            )
        }
        return
    }
    var shortcuts by remember(app.key) { mutableStateOf<List<ShortcutInfo>>(emptyList()) }
    LaunchedEffect(expanded) {
        if (expanded) shortcuts = actions.loadShortcuts(app)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        shortcuts.forEach { shortcut ->
            DropdownMenuItem(
                text = { Text((shortcut.shortLabel ?: shortcut.longLabel ?: shortcut.id).toString()) },
                onClick = { onDismiss(); actions.startShortcut(shortcut) },
            )
        }
        if (shortcuts.isNotEmpty()) HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(if (isFavorite) R.string.menu_remove else R.string.menu_add_favorite)) },
            onClick = { onDismiss(); onToggleFavorite() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.folder_add)) },
            onClick = { onDismiss(); actions.addToFolder(app) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_change_icon)) },
            onClick = { onDismiss(); actions.editIcon(app) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_hide)) },
            onClick = { onDismiss(); actions.hide(app) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_app_info)) },
            onClick = { onDismiss(); actions.info(app) },
        )
        if (!app.isSystem) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_uninstall), color = MaterialTheme.colorScheme.error) },
                onClick = { onDismiss(); actions.uninstall(app) },
            )
        }
    }
}
