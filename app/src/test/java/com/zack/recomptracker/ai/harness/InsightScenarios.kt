package com.zack.recomptracker.ai.harness

import com.zack.recomptracker.ai.InsightPromptBuilder
import com.zack.recomptracker.ai.ProgressInsightContext
import com.zack.recomptracker.ai.RecoveryInsightContext

/**
 * A realistic persona's data for one or more cards. Nulls mean "this card doesn't apply".
 *
 * Only the two remaining LLM-generated cards (Progress Trend, Recovery Readiness) produce a
 * prompt. NOISE_DEFUSER is deterministic (rendered without an LLM call), so it carries no prompt
 * and is not part of this prompt-iteration harness. (Weekly Pattern moved into the briefing.)
 */
data class InsightScenario(
    val name: String,
    val progress: ProgressInsightContext? = null,
    val recovery: RecoveryInsightContext? = null,
) {
    /** (cardLabel, renderedPrompt) for each present card. */
    fun cards(): List<Pair<String, String>> {
        val b = InsightPromptBuilder()
        val out = ArrayList<Pair<String, String>>()
        progress?.let { out += "Progress Trend" to b.buildProgressTrendPrompt(it) }
        recovery?.let { out += "Recovery Readiness" to b.buildRecoveryReadinessPrompt(it) }
        return out
    }
}

object InsightScenarios {

    val ALL: List<InsightScenario> = listOf(
        InsightScenario(
            name = "On-track fat loss",
            progress = ProgressInsightContext(28, -0.38, -0.20, 0.4, 92.0, 6, 6, priorWeightTrendKgPerWeek = -0.36),
        ),
        InsightScenario(
            name = "Plateau (flat weight, high adherence)",
            progress = ProgressInsightContext(28, -0.02, 0.0, 0.1, 90.0, 6, 6, priorWeightTrendKgPerWeek = -0.05),
        ),
        InsightScenario(
            name = "Fast loss with recovery dropping",
            progress = ProgressInsightContext(28, -0.85, -0.4, -0.2, 95.0, 6, 6, priorWeightTrendKgPerWeek = -0.60),
            recovery = RecoveryInsightContext(
                sleepHours = 5.5, energyScore = 4, hungerScore = 7, sorenessScore = 7, trained = true,
                avgSleepHours = 7.2, avgEnergyScore = 6,
            ),
        ),
        InsightScenario(
            name = "Surplus with waist creeping up",
            progress = ProgressInsightContext(28, 0.35, 0.4, 0.5, 80.0, 6, 6, priorWeightTrendKgPerWeek = 0.20),
        ),
        InsightScenario(
            name = "Lean-mass gain (weight up, waist stable, lifts up)",
            progress = ProgressInsightContext(28, 0.25, 0.0, 0.6, 88.0, 6, 6, priorWeightTrendKgPerWeek = 0.22),
        ),
        InsightScenario(
            name = "Poor recovery + trained today",
            recovery = RecoveryInsightContext(
                sleepHours = 5.0, energyScore = 3, hungerScore = 7, sorenessScore = 8, trained = true,
                avgSleepHours = 7.4, avgEnergyScore = 6,
            ),
        ),
        InsightScenario(
            name = "Good recovery, rest day",
            recovery = RecoveryInsightContext(
                sleepHours = 8.2, energyScore = 8, hungerScore = 4, sorenessScore = 2, trained = false,
                avgSleepHours = 7.5, avgEnergyScore = 6,
            ),
        ),
        InsightScenario(
            name = "Recomposition (flat weight, waist down, lifts up)",
            progress = ProgressInsightContext(28, -0.05, -0.3, 0.4, 89.0, 6, 6, priorWeightTrendKgPerWeek = -0.04),
            recovery = RecoveryInsightContext(
                sleepHours = 7.6, energyScore = 7, hungerScore = 5, sorenessScore = 3, trained = true,
                avgSleepHours = 7.5, avgEnergyScore = 6,
            ),
        ),
    )
}
