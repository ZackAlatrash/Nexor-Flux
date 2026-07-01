package com.zack.recomptracker.domain.food

import kotlin.math.floor
import kotlin.math.roundToInt

enum class SuggestionFocus { PROTEIN, CARBS, CALORIES, NONE }

data class SuggestMacros(val calories: Int, val proteinG: Double, val carbsG: Double, val fatG: Double)

/** A library food; macros are for one [servingLabel] serving. */
data class SuggestionFood(
    val name: String,
    val servingLabel: String,
    val gramsPerServing: Double?,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

data class MealSuggestion(
    val name: String, val amountLabel: String,
    val calories: Int, val proteinG: Double, val carbsG: Double, val fatG: Double,
)

data class ComboItem(val name: String, val amountLabel: String)
data class MealCombo(
    val items: List<ComboItem>,
    val calories: Int, val proteinG: Double, val carbsG: Double, val fatG: Double,
)

data class SuggestionResult(
    val remaining: SuggestMacros,
    val focus: SuggestionFocus,
    val suggestions: List<MealSuggestion>,
    val combos: List<MealCombo>,
    val libraryThin: Boolean,
)

/** Pure meal-suggestion engine. Deterministic; every number here, none from the LLM. */
object MealSuggester {
    private const val PROTEIN_MET = 0.85
    private const val MIN_PROTEIN_GAP = 5.0
    private const val MIN_CARB_GAP = 10.0
    private const val MIN_CAL_GAP = 100
    private const val FILL_FRACTION = 0.5
    private const val MAX_SUGGESTIONS = 5

    fun suggest(remaining: SuggestMacros, proteinMetRatio: Double, library: List<SuggestionFood>): SuggestionResult {
        val focus = when {
            proteinMetRatio < PROTEIN_MET && remaining.proteinG >= MIN_PROTEIN_GAP -> SuggestionFocus.PROTEIN
            remaining.carbsG >= MIN_CARB_GAP && remaining.calories > 0 -> SuggestionFocus.CARBS
            remaining.calories >= MIN_CAL_GAP -> SuggestionFocus.CALORIES
            else -> SuggestionFocus.NONE
        }
        if (focus == SuggestionFocus.NONE) {
            return SuggestionResult(remaining, focus, emptyList(), emptyList(), library.isEmpty())
        }
        fun perServingFocus(f: SuggestionFood): Double = when (focus) {
            SuggestionFocus.PROTEIN -> f.proteinG
            SuggestionFocus.CARBS -> f.carbsG
            else -> f.calories.toDouble()
        }
        val remainingFocus: Double = when (focus) {
            SuggestionFocus.PROTEIN -> remaining.proteinG
            SuggestionFocus.CARBS -> remaining.carbsG
            else -> remaining.calories.toDouble()
        }
        val ranked = library
            .filter { perServingFocus(it) > 0.0 && it.calories > 0 }
            .sortedByDescending { perServingFocus(it) / it.calories.toDouble() }
        val suggestions = ranked.mapNotNull { portion(it, perServingFocus(it), remainingFocus, remaining.calories) }
            .take(MAX_SUGGESTIONS)

        val combos = if (focus == SuggestionFocus.PROTEIN && remaining.carbsG >= MIN_CARB_GAP)
            buildCombo(library, remaining) else emptyList()

        return SuggestionResult(remaining, focus, suggestions, combos, libraryThin = suggestions.isEmpty())
    }

    /** Portion [food] to ~half of [remainingFocus] of its focus macro, capped to fit [remainingCalories]. */
    private fun portion(food: SuggestionFood, perServingFocus: Double, remainingFocus: Double, remainingCalories: Int): MealSuggestion? {
        if (perServingFocus <= 0.0 || food.calories <= 0) return null
        var servings = (FILL_FRACTION * remainingFocus) / perServingFocus
        if (remainingCalories > 0) servings = minOf(servings, remainingCalories.toDouble() / food.calories)
        // Floor to the nearest 0.5 (never round UP past the calorie cap), with a 0.5 minimum.
        servings = floor(servings * 2.0) / 2.0
        if (servings < 0.5) servings = 0.5
        val calories = (food.calories * servings).roundToInt()
        // If even the 0.5 minimum blows the remaining calories, this food doesn't fit — drop it.
        if (remainingCalories > 0 && calories > remainingCalories) return null
        return MealSuggestion(
            name = food.name,
            amountLabel = amountLabel(food, servings),
            calories = calories,
            proteinG = round1(food.proteinG * servings),
            carbsG = round1(food.carbsG * servings),
            fatG = round1(food.fatG * servings),
        )
    }

    private fun amountLabel(food: SuggestionFood, servings: Double): String {
        val grams = food.gramsPerServing
        return if (grams != null && grams > 0) "≈${(grams * servings).roundToInt()} g"
        else "≈${tidy(servings)} × ${food.servingLabel}"
    }

    private fun buildCombo(library: List<SuggestionFood>, remaining: SuggestMacros): List<MealCombo> {
        val protein = library.filter { it.proteinG > 0 && it.calories > 0 }
            .maxByOrNull { it.proteinG / it.calories.toDouble() } ?: return emptyList()
        val carb = library.filter { it.carbsG > 0 && it.calories > 0 && it.name != protein.name }
            .maxByOrNull { it.carbsG / it.calories.toDouble() } ?: return emptyList()
        val pPick = portion(protein, protein.proteinG, remaining.proteinG, remaining.calories) ?: return emptyList()
        val calLeft = (remaining.calories - pPick.calories).coerceAtLeast(0)
        val cPick = portion(carb, carb.carbsG, remaining.carbsG, calLeft) ?: return emptyList()
        return listOf(
            MealCombo(
                items = listOf(ComboItem(pPick.name, pPick.amountLabel), ComboItem(cPick.name, cPick.amountLabel)),
                calories = pPick.calories + cPick.calories,
                proteinG = round1(pPick.proteinG + cPick.proteinG),
                carbsG = round1(pPick.carbsG + cPick.carbsG),
                fatG = round1(pPick.fatG + cPick.fatG),
            ),
        )
    }

    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
    private fun tidy(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
