package com.zack.recomptracker.domain.insight

/** Runs all pattern detectors and returns the single highest-priority fact, or null. */
object InsightEngine {

    fun detectTopFact(days: List<DayNutrition>, targets: NutritionTargets): InsightFact? {
        val facts = listOfNotNull(
            detectDerailmentDay(days, targets),
            detectWeakestMacro(days, targets),
            detectWeekdayWeekend(days, targets),
            detectStreak(days, targets),
        )
        // Highest priority wins; ties broken by a fixed type rank.
        return facts.maxWithOrNull(compareBy({ it.priority }, { typeRank(it.type) }))
    }

    private fun typeRank(type: InsightFactType): Int = when (type) {
        InsightFactType.DERAILMENT_DAY -> 3
        InsightFactType.WEAKEST_MACRO -> 2
        InsightFactType.WEEKDAY_WEEKEND -> 1
        InsightFactType.STREAK -> 0
    }
}
