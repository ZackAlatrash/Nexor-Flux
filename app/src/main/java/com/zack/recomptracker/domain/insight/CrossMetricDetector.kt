package com.zack.recomptracker.domain.insight

import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Detects a cross-domain link between protein adherence and hunger: do protein-hit days run
 * meaningfully lower on hunger than protein-miss days? Pure; no Android deps. Correlation is
 * suggestive only — the card phrases it as a tendency, never a proven cause.
 */
object CrossMetricDetector {

    private const val PROTEIN_HIT_FRACTION = 0.9   // >= 90% of target counts as "hit"
    private const val MIN_DAYS_PER_GROUP = 2
    private const val MIN_HUNGER_GAP = 2.0          // points on the 1-10 scale

    fun detectProteinHungerLink(
        days: List<DayNutrition>,
        hungerByDate: Map<LocalDate, Int>,
        proteinTargetG: Int,
    ): InsightFact? {
        if (proteinTargetG <= 0) return null
        val threshold = proteinTargetG * PROTEIN_HIT_FRACTION
        val hit = ArrayList<Int>()
        val miss = ArrayList<Int>()
        for (day in days) {
            if (!day.logged) continue
            val hunger = hungerByDate[day.date] ?: continue
            if (day.proteinG >= threshold) hit += hunger else miss += hunger
        }
        if (hit.size < MIN_DAYS_PER_GROUP || miss.size < MIN_DAYS_PER_GROUP) return null
        val hitAvg = hit.average()
        val missAvg = miss.average()
        if (missAvg - hitAvg < MIN_HUNGER_GAP) return null   // only surface the helpful direction

        val hitR = hitAvg.roundToInt()
        val missR = missAvg.roundToInt()
        // Priority scales with the gap; keep it in the detector range (~15-40).
        val priority = (15 + (missAvg - hitAvg) * 5).roundToInt().coerceAtMost(40)
        val statement =
            "On days you hit your protein target, your hunger averages $hitR/10 versus $missR/10 on days you miss it."
        return InsightFact(InsightFactType.CROSS_METRIC, priority, statement)
    }
}
