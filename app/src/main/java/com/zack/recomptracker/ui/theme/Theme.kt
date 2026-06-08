package com.zack.recomptracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/** Provides the current [AppAccent] to the entire composition tree. */
val LocalAppAccent = compositionLocalOf { AppAccent() }

private fun accentedColorScheme(accent: AppAccent) = darkColorScheme(
    primary              = accent.accent,
    onPrimary            = Color.White,
    primaryContainer     = accent.tintedSurface,
    onPrimaryContainer   = accent.accentLight,
    secondary            = accent.accentLight,
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
fun RecompTrackerTheme(
    accentTheme: AccentTheme = AccentTheme.VIOLET,
    content: @Composable () -> Unit,
) {
    val appAccent = remember(accentTheme) { AppAccent(accentTheme) }
    CompositionLocalProvider(LocalAppAccent provides appAccent) {
        MaterialTheme(
            colorScheme = remember(accentTheme) { accentedColorScheme(appAccent) },
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
