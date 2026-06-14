package com.zack.recomptracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/** Provides the current [AppAccent] to the entire composition tree. */
val LocalAppAccent = compositionLocalOf { AppAccent() }

private fun accentedDarkColorScheme(accent: AppAccent) = darkColorScheme(
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

private fun accentedLightColorScheme(accent: AppAccent) = lightColorScheme(
    primary              = accent.accent,
    onPrimary            = Color.White,
    primaryContainer     = accent.tintedSurface,
    onPrimaryContainer   = accent.accentDark,
    secondary            = accent.accentDark,
    onSecondary          = Color.White,
    background           = Color(0xFFF4F2F7),
    onBackground         = Color(0xFF141019),
    surface              = Color(0xFFFBFAFD),
    onSurface            = Color(0xFF141019),
    surfaceVariant       = Color(0xFFE8E5EF),
    onSurfaceVariant     = Color(0x99141019),
    outline              = Color(0x1A000000),
    error                = Color(0xFFD92D45),
    onError              = Color.White,
)

@Composable
fun RecompTrackerTheme(
    accentTheme: AccentTheme = AccentTheme.VIOLET,
    darkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    val appAccent = remember(accentTheme) { AppAccent(accentTheme) }
    val appColors = remember(darkMode) { AppColors.of(darkMode) }
    val colorScheme = remember(accentTheme, darkMode) {
        if (darkMode) accentedDarkColorScheme(appAccent) else accentedLightColorScheme(appAccent)
    }
    CompositionLocalProvider(
        LocalAppAccent provides appAccent,
        LocalAppColors provides appColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
