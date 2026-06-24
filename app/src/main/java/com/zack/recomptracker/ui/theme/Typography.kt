package com.zack.recomptracker.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Named typography scale for the whole app. Screens MUST use these instead of inline
 * fontSize/fontWeight so text stays consistent.
 *
 * Color is intentionally NOT set here — it is theme-dependent (dark/light) and must be
 * passed at the call site, e.g. `Text(..., style = AppType.cardTitle, color = appColors.textPrimary)`.
 */
object AppType {
    // ── Screen headers ───────────────────────────────────────────────────────
    /** Tier-1 big display header for top-level tab destinations. */
    val screenTitle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
    /** Tier-2 compact header for pushed sub-screens. */
    val screenTitleCompact = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    /** Header subtitle / supporting line under a title. */
    val screenSubtitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)

    // ── Sections & rows ──────────────────────────────────────────────────────
    /** Uppercase section label (matches the existing SectionLabel values). */
    val sectionLabel = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.14.sp)
    /** Primary title inside a card / list row. */
    val cardTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    /** Secondary / supporting text inside a card / list row. */
    val cardSubtitle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    /** Default body copy. */
    val body = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    /** Small inline label. */
    val label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    /** Tiny uppercase caption for tiles / stats. */
    val metaLabel = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)

    // ── Data display (emphasis preserved) ────────────────────────────────────
    /** Largest hero number, e.g. onboarding result calorie. */
    val displayHero = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.0).sp)
    /** Hero metric number, e.g. Body metric / Dashboard calorie. */
    val displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
    /** Tile / summary big number. */
    val statValue = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    /** Compact stat value. */
    val statValueSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold)
}
