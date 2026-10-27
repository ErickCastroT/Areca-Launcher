package com.areca.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.areca.launcher.R
import com.areca.launcher.ui.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

private class WidgetOption(val info: AppWidgetProviderInfo, val label: String, val appLabel: String)

/** Lista los widgets disponibles en el teléfono (agrupados por app) y deja elegir uno. */
@Composable
fun WidgetPickerDialog(vm: HomeViewModel) {
    val context = LocalContext.current
    val options by produceState(emptyList<WidgetOption>()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            vm.widgets.providers()
                .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
                .map { WidgetOption(it, it.loadLabel(pm).toString(), appLabel(pm, it)) }
                .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
        }
    }

    Dialog(onDismissRequest = vm::closeWidgetPicker, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().displayCutoutPadding().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.widgets_pick_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::closeWidgetPicker) { Text(stringResource(R.string.settings_close)) }
                }
                if (options.isEmpty()) {
                    Text(
                        stringResource(R.string.widgets_none_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(options, key = { it.info.provider.flattenToString() }) { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.pickWidget(option.info) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WidgetPreview(option.info)
                            Column(Modifier.padding(start = 16.dp)) {
                                Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    option.appLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo) {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0)
                val scale = min(1f, 400f / drawable.intrinsicWidth.coerceAtLeast(1))
                drawable.toBitmap(
                    (drawable.intrinsicWidth * scale).toInt().coerceAtLeast(1),
                    (drawable.intrinsicHeight * scale).toInt().coerceAtLeast(1),
                ).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        Modifier.size(width = 112.dp, height = 72.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        preview?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp)) }
    }
}

private fun appLabel(pm: PackageManager, info: AppWidgetProviderInfo): String = runCatching {
    pm.getApplicationInfo(info.provider.packageName, 0).loadLabel(pm).toString()
}.getOrDefault(info.provider.packageName)
