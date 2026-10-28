package com.areca.launcher.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areca.launcher.R
import com.areca.launcher.ui.IconImage
import com.areca.launcher.data.zoomFor
import com.areca.launcher.data.DEFAULT_ICON_ZOOM
import com.areca.launcher.data.isFavorite
import com.areca.launcher.data.AppInfo
import com.areca.launcher.ui.HomeViewModel
import com.areca.launcher.ui.theme.toShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lista completa de apps: marcar favoritas y ocultar/mostrar. */
@Composable
fun AppsScreen(vm: HomeViewModel) {
    val apps by vm.allApps.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().displayCutoutPadding().imePadding(),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 24.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::goHome) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineMedium)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(filtered, key = { it.key }) { app ->
                val hidden = app.key in settings.hidden
                Row(
                    Modifier.fillMaxWidth().alpha(if (hidden) 0.5f else 1f)
                        .clickable { vm.toggleFavorite(app) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, settings.iconPack, settings.iconOverrides[app.key], remember(settings.iconShape) { settings.iconShape.toShape() }, settings.iconPaddingPercent, vm, settings.zoomFor(app.key))
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        if (hidden) {
                            Text(
                                stringResource(R.string.apps_hidden_badge),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    TextButton(onClick = { vm.toggleHidden(app) }) {
                        Text(stringResource(if (hidden) R.string.apps_show else R.string.apps_hide))
                    }
                    Checkbox(checked = settings.isFavorite(app.key), onCheckedChange = { vm.toggleFavorite(app) })
                }
            }
        }
        ArecaSearchField(
            value = query,
            onChange = { query = it },
            placeholder = stringResource(R.string.apps_search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
internal fun AppIcon(app: AppInfo, iconPack: String, iconOverride: String?, shape: Shape, paddingPercent: Int, vm: HomeViewModel, zoomPercent: Int = DEFAULT_ICON_ZOOM, contentDescription: String? = null) {
    val icon by produceState<ImageBitmap?>(vm.iconLoader.cached(app, iconPack, iconOverride, compact = true), app, iconPack, iconOverride, vm.iconLoader.styleKey) {
        value = withContext(Dispatchers.IO) { vm.iconLoader.load(app, iconPack, iconOverride, compact = true) }
    }
    icon?.let {
        Box(Modifier.size(44.dp).clip(shape).background(Color.White.copy(alpha = 0.12f))) {
            IconImage(it, contentDescription ?: app.label, zoomPercent, 44.dp * paddingPercent / 100)
        }
    } ?: androidx.compose.foundation.layout.Spacer(Modifier.size(44.dp))
}
