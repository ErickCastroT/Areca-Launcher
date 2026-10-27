package com.areca.launcher.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import com.areca.launcher.R
import androidx.compose.ui.unit.dp

/** Título de grupo: sin cajas ni mayúsculas, solo espacio y peso tipográfico. */
@Composable
fun SettingsSection(title: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = if (title == null) 8.dp else 32.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(top = if (title == null) 0.dp else 8.dp)) { content() }
    }
}

/** Fila de la lista de categorías: nombre, una línea que dice qué hay dentro y una flecha. */
@Composable
fun CategoryRow(title: String, hint: String, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Nombre a la izquierda, valor actual a la derecha, y el control debajo. */
@Composable
fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = MaterialTheme.colorScheme.onPrimary,
                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

/** Selector de una opción entre pocas, a lo ancho; la elegida lleva el acento. */
@Composable
fun SegmentedChoice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer,
                uncheckedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

/** Fila que lleva a otra pantalla o diálogo; el valor actual va debajo del título. */
@Composable
fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ShapeSwatch(shape: Shape, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .then(if (selected) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground), shape) else Modifier)
            .clickable(onClick = onClick),
    )
}

/**
 * Vista previa y controles de Ajustes. En pantallas anchas (tablet u horizontal) van lado a lado,
 * con la vista previa fija a la izquierda; en teléfono vertical, la vista previa arriba.
 */
@Composable
fun ColumnScope.SettingsPanes(
    wide: Boolean,
    preview: (@Composable (Modifier) -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrolling = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 40.dp)
    if (preview == null) {
        // Sin vista previa: el contenido ocupa el ancho (centrado y acotado en pantallas anchas).
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).widthIn(max = 640.dp).then(scrolling)) { content() }
        }
    } else if (wide) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(0.4f).fillMaxHeight().padding(start = 16.dp, end = 8.dp)) { preview(Modifier.fillMaxWidth()) }
            Column(Modifier.weight(0.6f).fillMaxHeight().then(scrolling).widthIn(max = 640.dp)) { content() }
        }
    } else {
        preview(Modifier.padding(horizontal = 16.dp))
        Column(Modifier.weight(1f).then(scrolling)) { content() }
    }
}

/** Un permiso con su estado (activo o falta) a la derecha; tocar la fila lleva a donde se concede. */
@Composable
fun PermissionRow(title: String, description: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(if (granted) R.string.perm_granted else R.string.perm_missing),
            style = MaterialTheme.typography.labelLarge,
            color = if (granted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.clip(CircleShape)
                .background(if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
