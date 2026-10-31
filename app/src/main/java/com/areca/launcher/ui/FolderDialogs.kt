package com.areca.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areca.launcher.R
import com.areca.launcher.data.zoomFor
import com.areca.launcher.data.AppInfo
import com.areca.launcher.data.Folder
import com.areca.launcher.ui.apps.AppIcon
import com.areca.launcher.ui.theme.toShape

/** Panel de una carpeta: sus apps a un toque; mantener pulsada una app permite sacarla. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FolderDialog(
    vm: HomeViewModel,
    folder: AppInfo,
    onClose: () -> Unit = vm::closeFolder,
    onRename: () -> Unit = { vm.startRenameFolder(folder.key) },
    onTakeOut: (AppInfo) -> Unit = { vm.removeFromFolder(folder.key, it.key) },
    /** Solo en las carpetas del escritorio: sacar la app también de las favoritas. */
    onRemoveFromFavorites: ((AppInfo) -> Unit)? = { vm.removeFromFolder(folder.key, it.key, keepOnDesktop = false) },
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shape = remember(settings.iconShape) { settings.iconShape.toShape() }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp)) {
                Text(folder.label, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    folder.folderMembers.forEach { app ->
                        var menu by remember(app.key) { mutableStateOf(false) }
                        Box {
                            Column(
                                Modifier.width(88.dp)
                                    .combinedClickable(
                                        onClick = { onClose(); vm.launch(app) },
                                        onLongClick = { menu = true },
                                    )
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIcon(app, settings.iconPack, settings.iconOverrides[app.key], shape, settings.iconPaddingPercent, vm, settings.zoomFor(app.key))
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.folder_take_out)) },
                                    onClick = { menu = false; onTakeOut(app) },
                                )
                                onRemoveFromFavorites?.let { remove ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_remove)) },
                                        onClick = { menu = false; remove(app) },
                                    )
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onRename) { Text(stringResource(R.string.folder_rename)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.settings_close)) }
                }
            }
        }
    }
}

/** Elegir una carpeta existente del escritorio o crear una nueva para [app]. */
@Composable
fun FolderChooserDialog(vm: HomeViewModel, app: AppInfo) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    FolderPicker(
        app = app,
        folders = settings.folders,
        onPick = { vm.addToFolder(it.key, app) },
        onCreate = { vm.createFolder(it, app) },
        onDismiss = vm::dismissFolderChooser,
    )
}

/** Lo mismo para las carpetas del listado de apps. */
@Composable
fun DrawerFolderChooserDialog(vm: HomeViewModel, app: AppInfo) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    FolderPicker(
        app = app,
        folders = settings.drawerFolders,
        onPick = { vm.addToDrawerFolder(it.key, app) },
        onCreate = { vm.createDrawerFolder(it, app) },
        onDismiss = vm::dismissDrawerFolderChooser,
    )
}

@Composable
private fun FolderPicker(app: AppInfo, folders: List<Folder>, onPick: (Folder) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(folders.isEmpty()) }
    var name by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.folder_default_name)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.folder_add_title, app.label), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                if (creating) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(MAX_FOLDER_NAME) },
                        label = { Text(stringResource(R.string.folder_name_label)) },
                        placeholder = { Text(defaultName) },
                        singleLine = true,
                    )
                } else {
                    folders.forEach { folder -> FolderRow(folder.name, folder.apps.size) { onPick(folder); onDismiss() } }
                    Text(
                        stringResource(R.string.folder_new),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = { onCreate(name.ifBlank { defaultName }); onDismiss() }) {
                Text(stringResource(R.string.folder_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun FolderRow(name: String, count: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.folder_apps_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun RenameFolderDialog(vm: HomeViewModel, folderKey: String) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val current = settings.folders.firstOrNull { it.key == folderKey }?.name ?: return
    NameDialog(current, onSave = { vm.renameFolder(folderKey, it) }, onDismiss = vm::dismissRenameFolder)
}

@Composable
fun RenameDrawerFolderDialog(vm: HomeViewModel, folderKey: String) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val current = settings.drawerFolders.firstOrNull { it.key == folderKey }?.name ?: return
    NameDialog(current, onSave = { vm.renameDrawerFolder(folderKey, it) }, onDismiss = vm::dismissRenameDrawerFolder)
}

@Composable
private fun NameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.folder_rename), style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_FOLDER_NAME) },
                label = { Text(stringResource(R.string.folder_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name); onDismiss() }) { Text(stringResource(R.string.folder_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val MAX_FOLDER_NAME = 24
