package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.insight.InsightFact

/**
 * Carries one detected cross-domain link (e.g. protein hit-rate ↔ hunger) for the model to
 * phrase as a hedged "you tend to…" observation plus one small suggestion. The correlation is
 * computed in the domain layer ([com.zack.recomptracker.domain.insight.InsightEngine]); this only
 * wraps the resulting [InsightFact], exactly like [PatternInsightContext].
 */
data class CrossMetricContext(val fact: InsightFact) {
    val hasSufficientData: Boolean get() = true

    fun key(): String = fact.statement
}
