package com.zack.recomptracker.domain.food

import kotlin.math.roundToInt

/**
 * Deterministic "meal impact preview" for the food-logging amount sheet.
 *
 * Pure arithmetic — ZERO AI. Given today's already-eaten totals, the user's plan targets, and
 * the macros of the item they're about to log, it projects where TODAY's plan lands *after*
 * logging (post-log % per macro) and produces one short, transparent adjust hint. It lets the
 * user see the consequence of the pending item at the decision moment, before they commit.
 *
 * No Android imports — fully unit-testable.
 */
object MealImpact {

    /** A macro pushed past this fraction of its target is flagged "over" and earns a trim hint. */
    const val OVER_THRESHOLD: Double = 1.05

    /** Below this post-log protein fraction we nudge toward protein instead. */
    const val PROTEIN_GAP_THRESHOLD: Double = 0.85

    /** Post-log projection for one macro. [percent] is the whole-number % of target (0 = no target). */
    data class MacroImpact(
        val percent: Int,
        val over: Boolean,
    )

    data class Result(
        val calories: MacroImpact,
        val protein: MacroImpact,
        val carbs: MacroImpact,
        val hint: String,
    )

    /**
     * Builds the impact preview. Returns null (⇒ hide the strip) when there are no usable
     * targets — the gate the caller relies on for "targets absent". The caller is responsible
     * for the "not today" gate before calling this.
     *
     * @param eatenCalories/eatenProtein/eatenCarbs  today's eaten (non-planned) totals so far
     * @param targetCalories/targetProtein/targetCarbs  plan targets
     * @param addCalories/addProtein/addCarbs  the pending item's macros (already scaled to the amount)
     */
    fun compute(
        eatenCalories: Int,
        eatenProtein: Double,
        eatenCarbs: Double,
        targetCalories: Int,
        targetProtein: Int,
        targetCarbs: Int,
        addCalories: Int,
        addProtein: Double,
        addCarbs: Double,
    ): Result? {
        // Gate: without at least one positive target there's nothing meaningful to project.
        if (targetCalories <= 0 && targetProtein <= 0 && targetCarbs <= 0) return null

        val calories = macroImpact(eatenCalories + addCalories.toDouble(), targetCalories.toDouble())
        val protein = macroImpact(eatenProtein + addProtein, targetProtein.toDouble())
        val carbs = macroImpact(eatenCarbs + addCarbs, targetCarbs.toDouble())

        val hint = buildHint(calories, protein, carbs, targetProtein)
        return Result(calories = calories, protein = protein, carbs = carbs, hint = hint)
    }

    private fun macroImpact(postLog: Double, target: Double): MacroImpact {
        if (target <= 0.0) return MacroImpact(percent = 0, over = false)
        val fraction = postLog / target
        return MacroImpact(
            percent = (fraction * 100.0).roundToInt(),
            over = fraction > OVER_THRESHOLD,
        )
    }

    /**
     * Deterministic hint rule (simple, transparent, ≤1 short sentence):
     * 1. If a macro lands over ~105% of target → trim that macro (calories first, then carbs).
     * 2. Else if protein is still a big gap (< 85% of target) → nudge protein.
     * 3. Else → a neutral "fits your day" line.
     */
    private fun buildHint(
        calories: MacroImpact,
        protein: MacroImpact,
        carbs: MacroImpact,
        targetProtein: Int,
    ): String = when {
        calories.over -> "Over your calorie target — trim to stay in range."
        carbs.over -> "Puts carbs over target — trim carbs to stay in range."
        targetProtein > 0 && protein.percent < (PROTEIN_GAP_THRESHOLD * 100).toInt() ->
            "Still short on protein — add a protein source next."
        else -> "Fits your day."
    }
}
