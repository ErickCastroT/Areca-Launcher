package com.areca.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areca.launcher.R
import com.areca.launcher.data.DEFAULT_ICON_ZOOM
import com.areca.launcher.ui.settings.SliderRow
import com.areca.launcher.data.zoomFor
import com.areca.launcher.data.AppInfo
import com.areca.launcher.ui.theme.toShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cambiar el icono de una app: elegir uno de un paquete de iconos, una imagen de la galería o restablecer. */
@Composable
fun IconPickerDialog(vm: HomeViewModel, target: AppInfo) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val packs = remember { vm.iconPacks() }
    var selectedPack by remember {
        mutableStateOf(settings.iconPack.ifEmpty { packs.firstOrNull()?.packageName.orEmpty() })
    }
    var query by remember { mutableStateOf("") }
    val icons by produceState(emptyList<String>(), selectedPack) {
        value = withContext(Dispatchers.IO) { vm.packIcons(selectedPack) }
    }
    val filtered = remember(icons, query) {
        if (query.isBlank()) icons else icons.filter { it.contains(query, ignoreCase = true) }
    }
    val shape = remember(settings.iconShape) { settings.iconShape.toShape() }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.setIconFromImage(target, uri)
            vm.closeIconEditor()
        }
    }

    Dialog(onDismissRequest = vm::closeIconEditor, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().displayCutoutPadding().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.icon_picker_title, target.label),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::closeIconEditor) { Text(stringResource(R.string.settings_close)) }
                }
                // Encuadre de este icono: la vista previa muestra el efecto al instante.
                val zoom = settings.zoomFor(target.key)
                val custom = target.key in settings.iconZooms
                val previewIcon by produceState<ImageBitmap?>(vm.iconLoader.cached(target, settings.iconPack, settings.iconOverrides[target.key]), target, settings.iconPack, settings.iconOverrides[target.key]) {
                    value = withContext(Dispatchers.IO) { vm.iconLoader.load(target, settings.iconPack, settings.iconOverrides[target.key]) }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(72.dp).clip(shape).background(Color.White.copy(alpha = 0.12f))) {
                        previewIcon?.let { IconImage(it, null, zoom, 72.dp * settings.iconPaddingPercent / 100) }
                    }
                    Column(Modifier.weight(1f)) {
                        SliderRow(
                            label = stringResource(R.string.label_zoom_this_icon),
                            valueText = stringResource(R.string.value_percent, zoom),
                            value = zoom.toFloat(),
                            range = MIN_ICON_ZOOM.toFloat()..MAX_ICON_ZOOM.toFloat(),
                            steps = (MAX_ICON_ZOOM - MIN_ICON_ZOOM) / 5 - 1,
                            onChange = { vm.setAppIconZoom(target, it.toInt()) },
                        )
                        if (custom) TextButton(onClick = { vm.setAppIconZoom(target, null) }) {
                            Text(stringResource(R.string.zoom_use_global, settings.iconZoomPercent))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { gallery.launch("image/*") }) { Text(stringResource(R.string.icon_picker_gallery)) }
                    TextButton(onClick = {
                        vm.setIconOverride(target, null)
                        vm.closeIconEditor()
                    }) { Text(stringResource(R.string.icon_picker_reset)) }
                }
                if (packs.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_icon_pack_none_found),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        packs.forEach { pack ->
                            FilterChip(
                                selected = pack.packageName == selectedPack,
                                onClick = { selectedPack = pack.packageName },
                                label = { Text(pack.label) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.apps_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(72.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it }) { name ->
                            val override = "pack:$selectedPack:$name"
                            val bitmap by produceState<ImageBitmap?>(null, override) {
                                value = withContext(Dispatchers.IO) { vm.iconLoader.loadOverride(override) }
                            }
                            Box(
                                Modifier.size(72.dp).clip(shape).background(Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        vm.setIconOverride(target, override)
                                        vm.closeIconEditor()
                                    },
                            ) {
                                bitmap?.let { IconImage(it, name, DEFAULT_ICON_ZOOM, 6.dp) }
                            }
                        }
                    }
                }
            }
        }
    }
}
