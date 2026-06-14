package com.zack.recomptracker.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Mode-aware semantic color tokens. Provided via [LocalAppColors] inside
 * [RecompTrackerTheme]. The dark set reproduces today's hardcoded values exactly,
 * so dark mode is visually unchanged; the light set flips text dark and surfaces light.
 */
data class AppColors(
    val textPrimary: Color,
    val textMuted: Color,
    val textDim: Color,
    val textFaint: Color,
    val textVeryMuted: Color,
    val frostedSurface: Color,
    val frostedSurfaceFallback: Color,
    val frostedBorder: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val scrim: Color,
) {
    companion object {
        // Dark = current production values (see DesignTokens.kt).
        val Dark = AppColors(
            textPrimary            = Color.White,
            textMuted              = Color(0x47FFFFFF),
            textDim                = Color(0x66FFFFFF),
            textFaint              = Color(0x40FFFFFF),
            textVeryMuted          = Color(0x38FFFFFF),
            frostedSurface         = Color(0x9E120A20),
            frostedSurfaceFallback = Color(0xD1160E26),
            frostedBorder          = Color(0x21FFFFFF),
            cardSurface            = Color(0x0AFFFFFF),
            cardBorder             = Color(0x12FFFFFF),
            scrim                  = Color(0x8C000000),
        )

        // Light = dark ink on light frosted glass. Alphas mirror the dark set's intent.
        val Light = AppColors(
            textPrimary            = Color(0xFF141019),
            textMuted              = Color(0x99141019),
            textDim                = Color(0xBF141019),
            textFaint              = Color(0x80141019),
            textVeryMuted          = Color(0x66141019),
            frostedSurface         = Color(0xCCFFFFFF),
            frostedSurfaceFallback = Color(0xE6FFFFFF),
            frostedBorder          = Color(0x1F000000),
            cardSurface            = Color(0x0A000000),
            cardBorder             = Color(0x1A000000),
            // scrim is the full-screen background-IMAGE veil (not a modal dim): a pale
            // veil mutes the bright themed background so dark text + light glass read well.
            scrim                  = Color(0x40FFFFFF),
        )

        fun of(darkMode: Boolean): AppColors = if (darkMode) Dark else Light
    }
}

/** Provides the current [AppColors] to the composition tree. Defaults to dark. */
val LocalAppColors = compositionLocalOf { AppColors.Dark }
