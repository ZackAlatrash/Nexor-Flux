package com.zack.recomptracker.domain.insight

/** Runs all pattern detectors and returns the single highest-priority fact, or null. */
object InsightEngine {

    fun detectTopFact(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? =
        detectTopFacts(days, targets, n = 1).firstOrNull()

    /**
     * The top [n] pattern facts, highest-priority first (ties broken by a fixed type rank), with at
     * most one fact per [InsightFactType]. Pure and deterministic — used by the Weekly Pattern
     * Spotlight in the briefing (Phase 2D), which shows the top two facts the single-fact card
     * previously discarded.
     */
    fun detectTopFacts(days: List<DayNutrition>, targets: NutritionTargets, n: Int = 2): List<InsightFact> {
        val facts = listOfNotNull(
            detectDerailmentDay(days, targets),
            detectWeakestMacro(days, targets),
            detectWeekdayWeekend(days, targets),
            detectStreak(days, targets),
        )
        return facts
            .sortedWith(compareByDescending<InsightFact> { it.priority }.thenByDescending { typeRank(it.type) })
            .take(n.coerceAtLeast(0))
    }

    private fun typeRank(type: InsightFactType): Int = when (type) {
        InsightFactType.DERAILMENT_DAY -> 3
        InsightFactType.WEAKEST_MACRO -> 2
        InsightFactType.WEEKDAY_WEEKEND -> 1
        InsightFactType.STREAK -> 0
    }
}
