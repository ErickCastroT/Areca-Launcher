package com.areca.launcher.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.areca.launcher.data.UpdateChecker
import com.areca.launcher.ui.UpdateUi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.areca.launcher.BuildConfig
import com.areca.launcher.R
import com.areca.launcher.ui.HomeViewModel
import com.areca.launcher.ui.Screen

private const val DEVELOPER_NAME = "erickcastrot"
private const val DEVELOPER_PAGE = "https://github.com/ErickCastroT/Areca-Launcher/issues"

/** Datos de la compilación y del teléfono, para saber qué versión corre y adjuntarlos a un reporte. */
private data class AboutInfo(
    val version: String,
    val codename: String,
    val build: String,
    val packageName: String,
    val android: String,
    val device: String,
)

@Composable
fun AboutScreen(vm: HomeViewModel) {
    val context = LocalContext.current
    val info = remember {
        AboutInfo(
            version = BuildConfig.VERSION_NAME,
            codename = BuildConfig.CODENAME,
            build = context.getString(R.string.about_build_value, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TYPE, BuildConfig.BUILD_DATE),
            packageName = context.packageName,
            android = context.getString(R.string.about_android_value, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
            device = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().displayCutoutPadding()) {
        Row(Modifier.padding(start = 8.dp, end = 24.dp, top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.open(Screen.SETTINGS) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxHeight().widthIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            // La versión como en Ubuntu: número grande y, debajo, el nombre en clave.
            Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.about_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(info.version, style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(top = 4.dp))
                Text(info.codename, style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            SettingsSection(stringResource(R.string.about_section_version)) {
                InfoRow(stringResource(R.string.about_version), info.version)
                InfoRow(stringResource(R.string.about_codename), info.codename)
                InfoRow(stringResource(R.string.about_build), info.build)
                InfoRow(stringResource(R.string.about_package), info.packageName)
            }
            UpdatesSection(vm)
            SettingsSection(stringResource(R.string.about_section_device)) {
                InfoRow(stringResource(R.string.about_android), info.android)
                InfoRow(stringResource(R.string.about_device), info.device)
            }
            SettingsSection(stringResource(R.string.about_section_developer)) {
                InfoRow(stringResource(R.string.about_developer_name), DEVELOPER_NAME)
                InfoRow(
                    stringResource(R.string.about_developer_contact),
                    "github.com/ErickCastroT/Areca-Launcher",
                    Modifier.clickable { openDeveloperPage(context) },
                )
                Text(
                    stringResource(R.string.about_copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            SettingsSection(stringResource(R.string.about_section_dev)) {
                InfoRow(stringResource(R.string.about_stack), stringResource(R.string.about_stack_value))
                InfoRow(stringResource(R.string.about_font), stringResource(R.string.about_font_value))
            }

            FilledTonalButton(
                onClick = { copyInfo(context, info) },
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            ) { Text(stringResource(R.string.about_copy)) }
        }
        }
    }
}

/** Buscar actualizaciones en GitHub: a mano o, si el usuario lo activa, una vez por semana. Areca nunca instala nada. */
@Composable
private fun UpdatesSection(vm: HomeViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ui by vm.updateUi.collectAsStateWithLifecycle()
    val spanish = stringResource(R.string.update_lang_is_es).toBoolean()
    // Una búsqueda de esta sesión manda; si no, lo que dejó la búsqueda semanal.
    val available = (ui as? UpdateUi.Available)
        ?: settings.updateAvailable.takeIf { it.isNotEmpty() && UpdateChecker.isNewer(it, BuildConfig.VERSION_NAME) }
            ?.let { UpdateUi.Available(it, "", settings.updateUrl) }

    SettingsSection(stringResource(R.string.about_section_updates)) {
        val status = when {
            ui == UpdateUi.Checking -> stringResource(R.string.update_checking)
            available != null -> stringResource(R.string.update_available, available.version)
            ui == UpdateUi.UpToDate -> stringResource(R.string.update_up_to_date)
            ui == UpdateUi.Failed -> stringResource(R.string.update_failed)
            else -> null
        }
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodyLarge,
                color = if (available != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        if (available != null && available.notes.isNotEmpty()) {
            Text(available.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (available != null && available.url.isNotEmpty()) {
            FilledTonalButton(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.url))) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.update_open)) }
        }
        FilledTonalButton(
            onClick = { vm.checkForUpdates(spanish) },
            enabled = ui != UpdateUi.Checking,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(stringResource(R.string.update_check)) }
        ToggleRow(
            title = stringResource(R.string.update_auto),
            subtitle = stringResource(R.string.update_auto_hint),
            checked = settings.updateAutoCheck,
            onChange = vm::setUpdateAutoCheck,
        )
        Text(
            stringResource(R.string.update_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun copyInfo(context: Context, info: AboutInfo) {
    val text = """
        Areca Launcher ${info.version} (${info.codename})
        ${info.build}
        ${info.packageName}
        Android ${info.android}
        ${info.device}
    """.trimIndent()
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Areca", text))
    Toast.makeText(context, R.string.about_copied, Toast.LENGTH_SHORT).show()
}

private fun openDeveloperPage(context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_PAGE))) }
}
