package com.zack.recomptracker.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Mode-aware semantic color tokens. Provided via [LocalAppColors] inside
 * [RecompTrackerTheme]. The dark set reproduces today's hardcoded values exactly,
 * so dark mode is visually unchanged; the light set flips text dark and surfaces light.
 */
data class AppColors(
    val isDark: Boolean,
    val textPrimary: Color,
    val textMuted: Color,
    val textSecondary: Color,
    val textDim: Color,
    val textFaint: Color,
    val textVeryMuted: Color,
    val frostedSurface: Color,
    val frostedSurfaceFallback: Color,
    val frostedBorder: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val scrim: Color,
    // Liquid/frosted glass surface wash painted over the blurred backdrop in
    // FrostedCard.onDrawSurface. Dark = a darkening veil; Light = a frosting white
    // veil so the card lifts off the (light) blurred background and dark text reads.
    val glassOverlay: Color,
    // Decorative top-edge sheen line on frosted glass.
    val glassShimmer: Color,
    // Neutral (non-accent) liquid glass pill surface — e.g. secondary/step buttons.
    // On light backgrounds a faint white wash is invisible, so the light value is a
    // much more opaque frosted white that reads as a distinct pill.
    val glassPillSurface: Color,
) {
    companion object {
        // Dark = current production values (see DesignTokens.kt).
        val Dark = AppColors(
            isDark                 = true,
            textPrimary            = Color.White,
            textMuted              = Color(0x47FFFFFF),
            textSecondary          = Color(0xB3FFFFFF),
            textDim                = Color(0x66FFFFFF),
            textFaint              = Color(0x40FFFFFF),
            textVeryMuted          = Color(0x38FFFFFF),
            frostedSurface         = Color(0x9E120A20),
            frostedSurfaceFallback = Color(0xD1160E26),
            frostedBorder          = Color(0x21FFFFFF),
            cardSurface            = Color(0x0AFFFFFF),
            cardBorder             = Color(0x12FFFFFF),
            scrim                  = Color(0x8C000000),
            glassOverlay           = Color(0x33000000),
            glassShimmer           = Color(0x33FFFFFF),
            glassPillSurface       = Color(0x24FFFFFF),
        )

        // Light = dark ink on light frosted glass. Alphas mirror the dark set's intent.
        val Light = AppColors(
            isDark                 = false,
            textPrimary            = Color(0xFF141019),
            textMuted              = Color(0x99141019),
            textSecondary          = Color(0xB3141019),
            textDim                = Color(0xBF141019),
            textFaint              = Color(0x80141019),
            textVeryMuted          = Color(0x66141019),
            frostedSurface         = Color(0xCCFFFFFF),
            frostedSurfaceFallback = Color(0xE6FFFFFF),
            frostedBorder          = Color(0x2E000000),
            // Neutral cards/tiles must be FROSTED WHITE (lighter than the colourful blurred
            // background), not a darkening tint — a white wash lifts them off the surface.
            cardSurface            = Color(0x8FFFFFFF),
            cardBorder             = Color(0x24000000),
            // scrim is the full-screen background-IMAGE veil (not a modal dim): a pale
            // veil mutes the bright themed background so dark text + light glass read well.
            scrim                  = Color(0x40FFFFFF),
            // Frost the card white so it lifts off the blurred light background.
            glassOverlay           = Color(0xC4FFFFFF),
            glassShimmer           = Color(0x40FFFFFF),
            glassPillSurface       = Color(0xC4FFFFFF),
        )

        fun of(darkMode: Boolean): AppColors = if (darkMode) Dark else Light
    }
}

/** Provides the current [AppColors] to the composition tree. Defaults to dark. */
val LocalAppColors = compositionLocalOf { AppColors.Dark }
