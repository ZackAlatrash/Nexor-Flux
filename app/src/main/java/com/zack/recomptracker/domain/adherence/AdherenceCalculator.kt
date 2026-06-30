package com.zack.recomptracker.domain.adherence

import java.time.LocalDate
import kotlin.math.abs

data class NutritionDay(
    val date: LocalDate,
    val calories: Int,
    val targetCalories: Int,
)

class AdherenceCalculator {

    /**
     * Per-day graded closeness to target: 100 - |calories - target| / target * 100,
     * clamped to 0..100. Returns 0 when nothing was logged (calories <= 0). This is the
     * single source of truth for a day's score — every adherence consumer routes through it.
     */
    fun dailyAdherencePercent(calories: Int, targetCalories: Int): Double {
        if (calories <= 0 || targetCalories <= 0) return 0.0
        val delta = abs(calories - targetCalories).toDouble() / targetCalories.toDouble()
        return (100.0 - (delta * 100.0)).coerceIn(0.0, 100.0)
    }

    /**
     * Adherence QUALITY across LOGGED days only, each graded against ITS OWN day target
     * (see [NutritionDay.targetCalories]). Days with no intake are excluded. Returns 0 if there
     * are no logged days.
     */
    fun calculate(days: List<NutritionDay>): Double {
        val logged = days.distinctBy { it.date }.filter { it.calories > 0 && it.targetCalories > 0 }
        if (logged.isEmpty()) return 0.0
        val sum = logged.sumOf { dailyAdherencePercent(it.calories, it.targetCalories) }
        return sum / logged.size.toDouble()
    }

    /**
     * Logging CONSISTENCY: the fraction of expected days that have any intake logged.
     * Separate from adherence quality so a diligent-but-imperfect logger and a non-logger are
     * not conflated. Returns 0 if expectedDays <= 0.
     */
    fun loggingConsistency(days: List<NutritionDay>, expectedDays: Int): Double {
        if (expectedDays <= 0) return 0.0
        val loggedDays = days.distinctBy { it.date }.count { it.calories > 0 }
        return (loggedDays.toDouble() / expectedDays.toDouble()) * 100.0
    }
}
