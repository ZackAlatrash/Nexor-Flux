package com.zack.recomptracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ── Accent theme presets ───────────────────────────────────────────────────────
enum class AccentTheme(
    val displayName: String,
    val accent: Color,
    val accentLight: Color,
    val accentLighter: Color,
    val accentDark: Color,
) {
    VIOLET("Violet", Color(0xFF8B5CF6), Color(0xFFa78bfa), Color(0xFFc4b5fd), Color(0xFF7c3aed)),
    BLUE(  "Blue",   Color(0xFF3B82F6), Color(0xFF60A5FA), Color(0xFF93C5FD), Color(0xFF1D4ED8)),
    EMERALD("Emerald", Color(0xFF10B981), Color(0xFF34D399), Color(0xFF6EE7B7), Color(0xFF059669)),
    ROSE(  "Rose",   Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFFFDA4AF), Color(0xFFBE123C)),
    AMBER( "Amber",  Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFFCD34D), Color(0xFFB45309)),
}

/**
 * Runtime accent token bag. Provided via [LocalAppAccent] inside [RecompTrackerTheme].
 * All composables that paint accent-coloured pixels read from this instead of
 * the old static Violet500 / Violet400 / Violet300 constants.
 */
data class AppAccent(val theme: AccentTheme = AccentTheme.VIOLET) {
    val accent: Color       = theme.accent
    val accentLight: Color  = theme.accentLight
    val accentLighter: Color = theme.accentLighter
    val accentDark: Color   = theme.accentDark
    // Derived opacities
    val tintedSurface: Color = accent.copy(alpha = 0.08f)
    val tintedBorder: Color  = accent.copy(alpha = 0.22f)
    val backgroundTint: Color = accent.copy(alpha = 0.06f)
}

// Violet palette — kept for source compatibility; prefer LocalAppAccent.current.*
val Violet500 = Color(0xFF8B5CF6)
val Violet400 = Color(0xFFa78bfa)
val Violet300 = Color(0xFFc4b5fd)

// App background scale
val BgDeep = Color(0xFF0D0818)
val BgMid  = Color(0xFF0F0B1C)
val BgDark = Color(0xFF090A12)

// Card surfaces — rgba values converted to ARGB
val CardSurface     = Color(0x0AFFFFFF)  // rgba(255,255,255,0.04)
val CardBorder      = Color(0x12FFFFFF)  // rgba(255,255,255,0.07)
val TintedSurface = Color(0x148B5CF6)  // rgba(139,92,246,0.08) — used by TintedCard (AI features)
val TintedBorder  = Color(0x388B5CF6)  // rgba(139,92,246,0.22)

// Text alpha variants
val TextMuted     = Color(0x47FFFFFF)  // rgba(255,255,255,0.28)
val TextVeryMuted = Color(0x38FFFFFF)  // rgba(255,255,255,0.22)
val TextFaint     = Color(0x40FFFFFF)  // rgba(255,255,255,0.25)
val TextDim       = Color(0x66FFFFFF)  // rgba(255,255,255,0.40)

// Semantic
val ErrorRed = Color(0xFFfb7185)

// Navigation pill
val NavPillBg          = Color(0xE60E091A)  // rgba(14,9,26,0.90)
val NavLogStart        = Color(0xFF9f75f7)
val NavLogEnd          = Color(0xFF7c3aed)
val NavActiveIndicator = Color(0x2E8B5CF6)  // rgba(139,92,246,0.18)

// ── Corner radius scale ────────────────────────────────────────────────────────
val CornerSmall = 10.dp   // inputs, step buttons, small controls
val CornerCard  = 16.dp   // all content cards — Neutral, Frosted, Tinted
val CornerChip  = 20.dp   // badges, pill indicators, tag chips
val CornerPill  = 100.dp  // Liquid Glass elements only (nav, buttons, modals)

// ── M3 Frosted Blur surface tokens ────────────────────────────────────────────
// rgba(18,10,32, 0.62) — dark frosted fill
val FrostedSurface         = Color(0x9E120A20)
// rgba(255,255,255, 0.13) — hairline white border
val FrostedBorder          = Color(0x21FFFFFF)
// rgba(22,14,38, 0.82) — used on API < 31 where blur is unavailable
val FrostedSurfaceFallback = Color(0xD1160E26)
