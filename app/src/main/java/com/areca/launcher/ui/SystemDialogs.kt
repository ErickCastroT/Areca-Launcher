package com.areca.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.areca.launcher.R

/** Cambiar el fondo de pantalla del teléfono: desde la galería (con recorte) o con los fondos del sistema. */
@Composable
fun WallpaperDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onDismiss()
        if (uri != null) cropAndSetWallpaper(context, uri)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.wallpaper_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Entry(stringResource(R.string.wallpaper_from_gallery), stringResource(R.string.wallpaper_from_gallery_hint)) {
                    pick.launch("image/*")
                }
                Entry(stringResource(R.string.wallpaper_system_picker), stringResource(R.string.wallpaper_system_picker_hint)) {
                    onDismiss()
                    openSystemWallpaperPicker(context)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } },
    )
}

@Composable
private fun Entry(title: String, hint: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun cropAndSetWallpaper(context: Context, uri: Uri) {
    runCatching {
        val intent = WallpaperManager.getInstance(context).getCropAndSetWallpaperIntent(uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }.onFailure {
        // Respaldo: cualquier app que sepa "usar como fondo de pantalla".
        runCatching {
            val attach = Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(uri, "image/*")
                .putExtra("mimeType", "image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(attach, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun openSystemWallpaperPicker(context: Context) {
    runCatching {
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Explica por qué se necesita el servicio de accesibilidad y lleva a activarlo. */
@Composable
fun AccessibilityPromptDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.a11y_prompt_title), style = MaterialTheme.typography.titleLarge) },
        text = { Text(stringResource(R.string.a11y_prompt_body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text(stringResource(R.string.a11y_prompt_open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.a11y_prompt_later)) } },
    )
}
