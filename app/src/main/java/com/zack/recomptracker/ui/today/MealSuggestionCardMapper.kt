package com.zack.recomptracker.ui.today

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.domain.food.MealCombo
import com.zack.recomptracker.domain.food.MealSuggestion
import com.zack.recomptracker.domain.food.MealSuggester
import com.zack.recomptracker.domain.food.SuggestionFocus
import com.zack.recomptracker.domain.food.SuggestionFood
import java.time.LocalTime
import kotlin.math.roundToInt

/** Rendered state for the Food-screen meal-suggestion card. Every number is engine-computed. */
data class MealSuggestionCardState(
    val focus: SuggestionFocus,
    val headline: String,
    val suggestions: List<MealSuggestion>,
    val combo: MealCombo?,
    val libraryThin: Boolean,
    val coachSeed: String,
)

/**
 * Pure mapper: turns today's plan target, eaten totals, library, and how much of the eating day has
 * elapsed into an optional card state. Returns null when the card should not show (too early, or the
 * user is on target). Deterministic — reuses [MealSuggester.suggestForDay]; no Android/Compose.
 */
object MealSuggestionCardMapper {
    const val DAY_FRACTION_GATE = 0.5
    private const val START_HOUR = 8
    private const val END_HOUR = 22

    /** Fraction of the [START_HOUR, END_HOUR] eating window elapsed at [now], clamped to [0,1]. */
    fun eatingDayFraction(now: LocalTime): Double {
        val start = START_HOUR * 3600
        val end = END_HOUR * 3600
        return ((now.toSecondOfDay() - start).toDouble() / (end - start)).coerceIn(0.0, 1.0)
    }

    fun build(
        target: MealSuggester.MacroTargets,
        eaten: MacroTotals,
        library: List<SuggestionFood>,
        fractionOfDayElapsed: Double,
    ): MealSuggestionCardState? {
        if (fractionOfDayElapsed < DAY_FRACTION_GATE) return null
        val r = MealSuggester.suggestForDay(target, eaten, library)
        if (r.focus == SuggestionFocus.NONE) return null
        return MealSuggestionCardState(
            focus = r.focus,
            headline = headline(r.remaining.calories, r.focus, r.remaining.proteinG, r.remaining.carbsG),
            suggestions = r.suggestions.take(3),
            combo = r.combos.firstOrNull(),
            libraryThin = r.libraryThin,
            coachSeed = coachSeed(r.remaining.calories, r.focus, r.remaining.proteinG, r.remaining.carbsG),
        )
    }

    private fun headline(cal: Int, focus: SuggestionFocus, proteinG: Double, carbsG: Double): String = when (focus) {
        SuggestionFocus.PROTEIN -> "≈$cal kcal · ${proteinG.roundToInt()} g protein to go"
        SuggestionFocus.CARBS -> "≈$cal kcal · ${carbsG.roundToInt()} g carbs to go"
        else -> "≈$cal kcal left today"
    }

    private fun coachSeed(cal: Int, focus: SuggestionFocus, proteinG: Double, carbsG: Double): String {
        val gap = when (focus) {
            SuggestionFocus.PROTEIN -> "about $cal kcal and ${proteinG.roundToInt()} g of protein"
            SuggestionFocus.CARBS -> "about $cal kcal and ${carbsG.roundToInt()} g of carbs"
            else -> "about $cal kcal"
        }
        return "The user tapped 'Get meal ideas' on the Food screen. They still need $gap today. " +
            "Call suggest_meals for their exact library fits AND search_web for recipe ideas that fit " +
            "the remaining macros — combine both, respect their memory (diet/allergies/dislikes), give " +
            "2-3 concrete options with amounts, and offer to log the one they pick."
    }
}
