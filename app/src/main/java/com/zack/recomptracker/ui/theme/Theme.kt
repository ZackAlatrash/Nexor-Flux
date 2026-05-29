package com.zack.recomptracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppDarkColors = darkColorScheme(
    primary = Color(0xFF3b82f6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1e3a5f),
    onPrimaryContainer = Color(0xFF3b82f6),
    secondary = Color(0xFF6b7280),
    onSecondary = Color.White,
    background = Color(0xFF0f0f0f),
    onBackground = Color.White,
    surface = Color(0xFF1a1a1a),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color(0xFF9ca3af),
    outline = Color(0xFF374151),
    error = Color(0xFFf87171),
    onError = Color(0xFF3d1515),
)

@Composable
fun RecompTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppDarkColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
