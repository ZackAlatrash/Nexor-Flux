package com.zack.recomptracker.ui.component

/** Structural card variant, chosen per surface by insight importance. */
enum class InsightCardVariant { HERO, STANDARD, PILL }

/** Coarse confidence label derived from a fact's priority. */
enum class ConfidenceLevel { MEDIUM, HIGH }

/**
 * Derives a confidence label from [InsightFact.priority]. Real fired-fact priorities run ~10–45
 * (see PatternDetectors): >= 30 is a strong signal, below that is moderate. Returns null only when
 * there is no fact (priority <= 0), so a fired insight always carries a badge.
 */
fun confidenceFrom(priority: Int): ConfidenceLevel? = when {
    priority <= 0 -> null
    priority >= 30 -> ConfidenceLevel.HIGH
    else -> ConfidenceLevel.MEDIUM
}
