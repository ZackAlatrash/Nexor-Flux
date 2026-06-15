package com.zack.recomptracker.ui.dashboard

import com.zack.recomptracker.ai.PatternInsightContext
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.insight.DayNutrition
import com.zack.recomptracker.domain.insight.InsightEngine
import com.zack.recomptracker.domain.insight.NutritionTargets

/**
 * Pure mapper: runs [InsightEngine] over the per-day window and wraps the top fact
 * in a [PatternInsightContext]. Returns null when no pattern fires (card stays hidden).
 */
fun buildPatternInsightContext(
    days: List<DayNutrition>,
    prefs: PlanPreferences,
): PatternInsightContext? {
    val targets = NutritionTargets(
        calories = prefs.targetCalories,
        proteinG = prefs.targetProteinG,
        carbsG = prefs.targetCarbsG,
        fatG = prefs.targetFatG,
        calorieZoneLower = prefs.calorieZoneLowerBound,
        calorieZoneUpper = prefs.calorieZoneUpperBound,
    )
    val fact = InsightEngine.detectTopFact(days, targets) ?: return null
    return PatternInsightContext(fact)
}
