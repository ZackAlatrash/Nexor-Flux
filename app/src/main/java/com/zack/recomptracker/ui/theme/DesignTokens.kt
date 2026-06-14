package com.zack.recomptracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.R

// ── Accent theme presets ───────────────────────────────────────────────────────
enum class AccentTheme(
    val displayName: String,
    val accent: Color,
    val accentLight: Color,
    val accentLighter: Color,
    val accentDark: Color,
    // Readable accent "ink" for text/icons on a LIGHT background. The accent/accentLight/
    // accentLighter shades are tuned to glow on dark surfaces and are too pale on light;
    // this is a deepened accent (~Tailwind 700/800) that meets text contrast on near-white.
    val accentInk: Color,
) {
    VIOLET( "Violet",  Color(0xFF8B5CF6), Color(0xFFa78bfa), Color(0xFFc4b5fd), Color(0xFF7c3aed), Color(0xFF6D28D9)),
    INDIGO( "Indigo",  Color(0xFF6366F1), Color(0xFF818CF8), Color(0xFFA5B4FC), Color(0xFF4338CA), Color(0xFF4338CA)),
    BLUE(   "Blue",    Color(0xFF3B82F6), Color(0xFF60A5FA), Color(0xFF93C5FD), Color(0xFF1D4ED8), Color(0xFF1D4ED8)),
    CYAN(   "Cyan",    Color(0xFF06B6D4), Color(0xFF22D3EE), Color(0xFF67E8F9), Color(0xFF0891B2), Color(0xFF0E7490)),
    EMERALD("Emerald", Color(0xFF10B981), Color(0xFF34D399), Color(0xFF6EE7B7), Color(0xFF059669), Color(0xFF047857)),
    LIME(   "Lime",    Color(0xFF84CC16), Color(0xFFA3E635), Color(0xFFBEF264), Color(0xFF65A30D), Color(0xFF4D7C0F)),
    AMBER(  "Amber",   Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFFCD34D), Color(0xFFB45309), Color(0xFF92400E)),
    ORANGE( "Orange",  Color(0xFFF97316), Color(0xFFFB923C), Color(0xFFFDBA74), Color(0xFFEA580C), Color(0xFFC2410C)),
    ROSE(   "Rose",    Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFFFDA4AF), Color(0xFFBE123C), Color(0xFFBE123C)),
    SLATE(  "Slate",   Color(0xFF64748B), Color(0xFF94A3B8), Color(0xFFCBD5E1), Color(0xFF334155), Color(0xFF334155)),
    SILVER( "Silver",  Color(0xFFCBD5E1), Color(0xFFE2E8F0), Color(0xFFF1F5F9), Color(0xFF94A3B8), Color(0xFF475569)),
}

/**
 * Runtime accent token bag. Provided via [LocalAppAccent] inside [RecompTrackerTheme].
 * All composables that paint accent-coloured pixels read from this instead of
 * the old static Violet500 / Violet400 / Violet300 constants.
 *
 * [darkMode] only affects the ink* properties (accent text/icon colour): in dark mode the
 * bright shades are readable on the dark UI; in light mode they collapse to [AccentTheme.accentInk]
 * so accent-coloured text stays legible on light surfaces. Brand fills (accent, tintedSurface,
 * gradients, glows) are mode-invariant and unchanged.
 */
data class AppAccent(val theme: AccentTheme = AccentTheme.VIOLET, val darkMode: Boolean = true) {
    val accent: Color       = theme.accent
    val accentLight: Color  = theme.accentLight
    val accentLighter: Color = theme.accentLighter
    val accentDark: Color   = theme.accentDark
    // Mode-aware accent ink for text/icons. Dark mode = the original bright shades (so dark
    // mode is unchanged); light mode = the deepened, legible accentInk.
    val inkBase: Color    = if (darkMode) theme.accent       else theme.accentInk
    val inkLight: Color   = if (darkMode) theme.accentLight  else theme.accentInk
    val inkLighter: Color = if (darkMode) theme.accentLighter else theme.accentInk
    // Legible text/icon colour to paint ON TOP OF a solid [accent] fill (buttons, FABs).
    // White reads on saturated/dark accents; pale accents (Silver, Lime, Amber) need dark ink.
    // Independent of mode — depends only on the fill's perceived brightness.
    val onAccent: Color = run {
        val luma = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
        if (luma > 0.62f) Color(0xFF141019) else Color.White
    }
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

/**
 * Per-theme background drawable for the given mode. Resource names follow
 * `bg_<lowercase-theme>_<dark|light>` (see scripts/convert_backgrounds.sh).
 */
fun AccentTheme.backgroundRes(darkMode: Boolean): Int = when (this to darkMode) {
    AccentTheme.VIOLET  to true  -> R.drawable.bg_violet_dark
    AccentTheme.VIOLET  to false -> R.drawable.bg_violet_light
    AccentTheme.INDIGO  to true  -> R.drawable.bg_indigo_dark
    AccentTheme.INDIGO  to false -> R.drawable.bg_indigo_light
    AccentTheme.BLUE    to true  -> R.drawable.bg_blue_dark
    AccentTheme.BLUE    to false -> R.drawable.bg_blue_light
    AccentTheme.CYAN    to true  -> R.drawable.bg_cyan_dark
    AccentTheme.CYAN    to false -> R.drawable.bg_cyan_light
    AccentTheme.EMERALD to true  -> R.drawable.bg_emerald_dark
    AccentTheme.EMERALD to false -> R.drawable.bg_emerald_light
    AccentTheme.LIME    to true  -> R.drawable.bg_lime_dark
    AccentTheme.LIME    to false -> R.drawable.bg_lime_light
    AccentTheme.AMBER   to true  -> R.drawable.bg_amber_dark
    AccentTheme.AMBER   to false -> R.drawable.bg_amber_light
    AccentTheme.ORANGE  to true  -> R.drawable.bg_orange_dark
    AccentTheme.ORANGE  to false -> R.drawable.bg_orange_light
    AccentTheme.ROSE    to true  -> R.drawable.bg_rose_dark
    AccentTheme.ROSE    to false -> R.drawable.bg_rose_light
    AccentTheme.SLATE   to true  -> R.drawable.bg_slate_dark
    AccentTheme.SLATE   to false -> R.drawable.bg_slate_light
    AccentTheme.SILVER  to true  -> R.drawable.bg_silver_dark
    else                         -> R.drawable.bg_silver_light
}
