package com.zack.recomptracker.ui.component

/** Structural card variant, chosen per surface by insight importance. */
enum class InsightCardVariant { HERO, STANDARD, PILL }

/** Coarse confidence label derived from a fact's priority. */
enum class ConfidenceLevel { MEDIUM, HIGH }

/**
 * Derives a confidence label from InsightFact.priority. Higher priority = stronger signal.
 * Returns null for non-positive priorities (no badge shown).
 */
fun confidenceFrom(priority: Int): ConfidenceLevel? = when {
    priority <= 0 -> null
    priority >= 3 -> ConfidenceLevel.HIGH
    else -> ConfidenceLevel.MEDIUM
}
