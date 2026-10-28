package com.areca.launcher.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.areca.launcher.R
import com.areca.launcher.data.AccentColor

private fun AccentColor.label() = when (this) {
    AccentColor.DEFAULT -> R.string.accent_default
    AccentColor.GOLD -> R.string.accent_gold
    AccentColor.CORAL -> R.string.accent_coral
    AccentColor.ROSE -> R.string.accent_rose
    AccentColor.VIOLET -> R.string.accent_violet
    AccentColor.BLUE -> R.string.accent_blue
    AccentColor.TEAL -> R.string.accent_teal
    AccentColor.GREEN -> R.string.accent_green
}

/** Círculos de color con su nombre debajo; "Del tema" (degradado de todos) deja el acento del tema. */
@Composable
fun AccentChoice(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    val darkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
    FlowRow(
        Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccentColor.entries.forEach { accent ->
            val isSelected = accent == selected
            val fill = if (accent == AccentColor.DEFAULT) {
                Brush.sweepGradient(AccentColor.entries.drop(1).map { Color(if (darkUi) it.dark else it.light) })
            } else Brush.linearGradient(Color(if (darkUi) accent.dark else accent.light).let { listOf(it, it) })
            Column(
                Modifier.width(80.dp).selectable(isSelected, role = Role.RadioButton) { onSelect(accent) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(fill)
                        .border(
                            if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            CircleShape,
                        ),
                )
                Text(
                    stringResource(accent.label()),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
