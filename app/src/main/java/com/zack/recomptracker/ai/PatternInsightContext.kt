package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.insight.InsightFact

/** Carries one computed [InsightFact] for the cloud model to phrase. */
data class PatternInsightContext(val fact: InsightFact) {
    val hasSufficientData: Boolean get() = true
    fun key(): String = fact.statement
}
