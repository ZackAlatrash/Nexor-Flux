package com.zack.recomptracker.ui.today

import com.zack.recomptracker.ai.RestOfDayInsightContext
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.preferences.PlanPreferences

/**
 * Pure mapper: builds a [RestOfDayInsightContext] from today's running totals, the plan target,
 * and the number of meals logged so far. `mealsLoggedCount` of 0 makes the context insufficient
 * so the tap-to-reveal button stays hidden.
 */
fun buildRestOfDayInsightContext(
    totals: MacroTotals,
    target: PlanPreferences,
    mealsLoggedCount: Int,
): RestOfDayInsightContext = RestOfDayInsightContext(
    caloriesConsumed = totals.calories,
    targetCalories = target.targetCalories,
    calorieZoneLowerBound = target.calorieZoneLowerBound,
    calorieZoneUpperBound = target.calorieZoneUpperBound,
    proteinConsumedG = totals.proteinG,
    proteinTargetG = target.targetProteinG,
    mealsLoggedCount = mealsLoggedCount,
)
