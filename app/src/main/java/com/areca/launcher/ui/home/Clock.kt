package com.areca.launcher.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ClockWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - now.second * 1000L - now.nano / 1_000_000L)
        }
    }
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    Column(modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            now.format(DateTimeFormatter.ofPattern(timePattern)),
            style = MaterialTheme.typography.displayLarge,
            color = androidx.compose.ui.graphics.Color.White,
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            style = MaterialTheme.typography.titleMedium,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
        )
    }
}
