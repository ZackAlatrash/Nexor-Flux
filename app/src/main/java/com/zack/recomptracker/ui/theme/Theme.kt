package com.zack.recomptracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppDarkColors = darkColorScheme(
    primary              = Color(0xFF8B5CF6),
    onPrimary            = Color.White,
    primaryContainer     = Color(0x208B5CF6),
    onPrimaryContainer   = Color(0xFFa78bfa),
    secondary            = Color(0xFFa78bfa),
    onSecondary          = Color.White,
    background           = Color(0xFF0D0818),
    onBackground         = Color.White,
    surface              = Color(0xFF0F0B1C),
    onSurface            = Color.White,
    surfaceVariant       = Color(0xFF1A1527),
    onSurfaceVariant     = Color(0x47FFFFFF),
    outline              = Color(0x12FFFFFF),
    error                = Color(0xFFfb7185),
    onError              = Color.White,
)

@Composable
fun RecompTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppDarkColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
