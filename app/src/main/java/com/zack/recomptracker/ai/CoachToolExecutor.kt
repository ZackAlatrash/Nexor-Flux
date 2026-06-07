package com.zack.recomptracker.ai

import android.util.Log
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.domain.food.MealEntryTypes
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class CoachToolExecutor(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
) {

    suspend fun execute(name: String, args: Map<String, String>): String = when (name) {
        "get_today_summary" -> getTodaySummary(args)
        "get_weekly_trends" -> getWeeklyTrends()
        "search_food_library" -> searchFoodLibrary(args)
        "log_meal" -> logMeal(args)
        "log_metric" -> logMetric(args)
        "update_calorie_target" -> updateCalorieTarget(args)
        else -> """{"error":"unknown tool $name"}"""
    }

    private suspend fun getTodaySummary(args: Map<String, String> = emptyMap()): String {
        val today = args["date"]?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        } ?: dateProvider.today()
        // Use getDay (direct suspend DAO query) instead of observeDay(...).first() — the
        // combine-backed Flow requires both inner flows to emit before producing its first
        // value, which can silently return 0 meals when no daily_log row exists for today.
        val dayLog = logRepository.getDay(today)
        Log.d("RecompCoach", "get_today_summary: date=$today meals=${dayLog.meals.size} cals=${dayLog.totals.calories}")
        val mealsJson = dayLog.meals.joinToString(separator = ",") { meal ->
            """{"name":"${meal.name.esc()}","calories":${meal.calories},"protein_g":${meal.proteinG},"carbs_g":${meal.carbsG},"fat_g":${meal.fatG},"meal_type":"${meal.mealType.esc()}"}"""
        }
        val log = dayLog.dailyLog
        val dailyLogJson = if (log != null) {
            """{"weight_kg":${log.bodyWeightKg},"waist_cm":${log.waistCm},"steps":${log.steps},"sleep_hours":${log.sleepHours},"energy_score":${log.energyScore},"hunger_score":${log.hungerScore},"soreness_score":${log.sorenessScore},"trained":${log.trained},"notes":"${log.notes.esc()}"}"""
        } else {
            "null"
        }
        return """{"date":"$today","meals":[$mealsJson],"totals":{"calories":${dayLog.totals.calories},"protein_g":${dayLog.totals.proteinG},"carbs_g":${dayLog.totals.carbsG},"fat_g":${dayLog.totals.fatG}},"daily_log":$dailyLogJson}"""
    }

    private suspend fun getWeeklyTrends(): String {
        val today = dateProvider.today()
        val start = today.minusDays(6)
        val macroMap = logRepository.getWeekMacros(start, today)
        val dailyEntries = (0..6).joinToString(separator = ",") { offset ->
            val date = start.plusDays(offset.toLong())
            val m = macroMap[date]
            val cals = m?.calories ?: 0
            val prot = m?.proteinG ?: 0.0
            val carbs = m?.carbsG ?: 0.0
            val fat = m?.fatG ?: 0.0
            """{"date":"$date","calories":$cals,"protein_g":$prot,"carbs_g":$carbs,"fat_g":$fat}"""
        }
        val totalDays = 7
        val loggedDays = macroMap.values.count { it.calories > 0 }
        val adherencePercent = (loggedDays.toDouble() / totalDays * 100).toInt()
        return """{"week_start":"$start","week_end":"$today","daily_macros":[$dailyEntries],"adherence_percent":$adherencePercent}"""
    }

    private suspend fun searchFoodLibrary(args: Map<String, String>): String {
        val query = args["query"]?.trim()
            ?: return """{"error":"search_food_library requires 'query'"}"""
        if (query.isEmpty()) return """{"results":[],"count":0}"""

        val requestedGrams = args["grams"]?.toDoubleOrNull()
        val matches = scoredFoodMatches(query).take(5)

        if (matches.isEmpty()) return """{"results":[],"count":0}"""

        val resultsJson = matches.joinToString(",") { (food, _) ->
            val servingGrams = food.householdServingGrams
            val scale = if (requestedGrams != null && servingGrams != null && servingGrams > 0) {
                requestedGrams / servingGrams
            } else null
            val calories = if (scale != null) (food.calories * scale).toInt() else food.calories
            val proteinG = if (scale != null) food.proteinG * scale else food.proteinG
            val carbsG   = if (scale != null) food.carbsG   * scale else food.carbsG
            val fatG     = if (scale != null) food.fatG     * scale else food.fatG
            val servingLabel = if (requestedGrams != null) "${requestedGrams.toInt()}g" else food.servingName.esc()
            """{"name":"${food.name.esc()}","calories":$calories,"protein_g":$proteinG,"carbs_g":$carbsG,"fat_g":$fatG,"serving":"$servingLabel"}"""
        }
        return """{"results":[$resultsJson],"count":${matches.size}}"""
    }

    private suspend fun logMeal(args: Map<String, String>): String {
        val name = args["name"] ?: return """{"error":"log_meal requires 'name'"}"""
        val requestedGrams = args["grams"]?.toDoubleOrNull()
        val mealType = args["meal_type"]
            ?.takeIf { it in setOf("Breakfast", "Lunch", "Dinner", "Snack") }
            ?: "Snack"

        // Always check the food library first — use its macros instead of the model's
        // estimates. This ensures the entry matches the saved food exactly, regardless of
        // whether the model called search_food_library beforehand.
        val allFoods = logRepository.getSavedFoods()
        val libraryFood = scoredFoodMatches(name, allFoods).firstOrNull()?.first
        Log.d("RecompCoach", "logMeal: query='$name' grams=$requestedGrams library_hit=${libraryFood?.name} total_foods=${allFoods.size}")
        val finalName: String
        val calories: Int
        val proteinG: Double
        val carbsG: Double
        val fatG: Double

        if (libraryFood != null) {
            val servingGrams = libraryFood.householdServingGrams
            val scale = if (requestedGrams != null && servingGrams != null && servingGrams > 0)
                requestedGrams / servingGrams else 1.0
            finalName = libraryFood.name
            calories = (libraryFood.calories * scale).toInt()
            proteinG = libraryFood.proteinG * scale
            carbsG = libraryFood.carbsG * scale
            fatG = libraryFood.fatG * scale
            Log.d("RecompCoach", "logMeal: using library '$finalName' scale=$scale calories=$calories")
        } else {
            // Food not in library — fall back to model-provided macros.
            finalName = name
            // LiteRT-LM may surface integer JSON values as Double (e.g. "500.0"), so try
            // toIntOrNull first and fall back to toDoubleOrNull().toInt() before giving up.
            calories = args["calories"]?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
                ?: return """{"error":"'${name.esc()}' not found in food library. Please call log_meal again with calories and macros."}"""
            // Guard: model passes 0 as placeholder when it expects library lookup to handle it.
            // Logging 0 calories is always wrong for real food — return an error so the model retries.
            if (calories <= 0) return """{"error":"'${name.esc()}' not found in food library. Please call log_meal again with the actual calories."}"""
            proteinG = args["protein_g"]?.toDoubleOrNull() ?: 0.0
            carbsG = args["carbs_g"]?.toDoubleOrNull() ?: 0.0
            fatG = args["fat_g"]?.toDoubleOrNull() ?: 0.0
        }

        // For library foods: populate all the metadata fields so the entry renders in the
        // food log exactly like a manually-logged library food (shows "Xg ·" prefix and
        // allows amount editing). mealType is set to FOOD_LIBRARY so the entry is tagged
        // correctly; slot assignment still uses the user's mealType arg via matchedSlotId.
        val logGrams = if (libraryFood != null) requestedGrams ?: libraryFood.householdServingGrams else null
        val input = MealEntryInput(
            date = dateProvider.today(),
            mealType = if (libraryFood != null) MealEntryTypes.FOOD_LIBRARY else mealType,
            name = finalName,
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            amountGrams = logGrams,
            basePer100Calories = libraryFood?.calories,
            basePer100ProteinG = libraryFood?.proteinG,
            basePer100CarbsG = libraryFood?.carbsG,
            basePer100FatG = libraryFood?.fatG,
            entryServingName = libraryFood?.householdServingName,
            entryServingGrams = libraryFood?.householdServingGrams,
            loggedByServings = false,
        )
        // Match the meal_type to a named slot (case-insensitive) so the entry appears
        // inside the correct slot card in the food log screen, not just in the totals.
        val matchedSlotId = logRepository.getSlots()
            .firstOrNull { it.name.trim().equals(mealType, ignoreCase = true) }
            ?.id
        logRepository.addMealToSlot(input, matchedSlotId)
        return """{"success":true,"logged":"${finalName.esc()}","calories":$calories}"""
    }

    // Shared food scoring used by both searchFoodLibrary and logMeal.
    //   3 = exact name match (case-insensitive)
    //   2 = name starts with query
    //   1 = name contains query
    //   0 = every query word appears somewhere in name
    //  -1 = no match → excluded
    private suspend fun scoredFoodMatches(query: String): List<Pair<SavedFoodEntity, Int>> =
        scoredFoodMatches(query, logRepository.getSavedFoods())

    private fun scoredFoodMatches(query: String, foods: List<SavedFoodEntity>): List<Pair<SavedFoodEntity, Int>> {
        val queryLower = query.lowercase().trim()
        val queryWords = queryLower.split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun score(name: String): Int {
            val n = name.lowercase()
            return when {
                n == queryLower -> 3
                n.startsWith(queryLower) -> 2
                n.contains(queryLower) -> 1
                queryWords.all { n.contains(it) } -> 0
                else -> -1
            }
        }
        return foods.map { it to score(it.name) }.filter { (_, s) -> s >= 0 }.sortedByDescending { (_, s) -> s }
    }

    private val validMetrics = setOf(
        "weight_kg", "waist_cm", "sleep_hours",
        "energy_score", "hunger_score", "soreness_score",
    )

    private val scoreMetrics = setOf("energy_score", "hunger_score", "soreness_score")

    private suspend fun logMetric(args: Map<String, String>): String {
        val metric = args["metric"] ?: return """{"error":"log_metric requires 'metric'"}"""
        if (metric !in validMetrics) return """{"error":"unknown metric '${metric.esc()}'"}"""
        val value = args["value"]?.toDoubleOrNull()
            ?: return """{"error":"log_metric requires a numeric 'value'"}"""
        if (metric in scoreMetrics && value != kotlin.math.floor(value)) {
            return """{"error":"$metric must be a whole number"}"""
        }
        val rangeError = when (metric) {
            "energy_score", "hunger_score", "soreness_score" ->
                if (value.toInt() !in 1..10) "$metric must be between 1 and 10" else null
            "weight_kg" -> if (value !in 20.0..300.0) "weight_kg must be between 20 and 300" else null
            "waist_cm" -> if (value !in 40.0..200.0) "waist_cm must be between 40 and 200" else null
            "sleep_hours" -> if (value !in 0.0..24.0) "sleep_hours must be between 0 and 24" else null
            else -> null
        }
        if (rangeError != null) return """{"error":"$rangeError"}"""
        val today = dateProvider.today()
        val existing = logRepository.getDay(today).dailyLog
        logRepository.saveDailyMetrics(
            DailyMetricsInput(
                date = today,
                bodyWeightKg = if (metric == "weight_kg") value else existing?.bodyWeightKg,
                waistCm = if (metric == "waist_cm") value else existing?.waistCm,
                waistSkinfoldMm = existing?.waistSkinfoldMm,
                steps = existing?.steps,
                sleepHours = if (metric == "sleep_hours") value else existing?.sleepHours,
                energyScore = if (metric == "energy_score") value.toInt() else existing?.energyScore,
                hungerScore = if (metric == "hunger_score") value.toInt() else existing?.hungerScore,
                sorenessScore = if (metric == "soreness_score") value.toInt() else existing?.sorenessScore,
                trained = existing?.trained ?: false,
                notes = existing?.notes ?: "",
            ),
        )
        return """{"success":true,"metric":"${metric.esc()}","value":$value}"""
    }

    private suspend fun updateCalorieTarget(args: Map<String, String>): String {
        // Same Double-representation guard as logMeal — model may emit e.g. 2200.0.
        val newTarget = args["target_calories"]?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
            ?: return """{"error":"update_calorie_target requires 'target_calories'"}"""
        if (newTarget < 500 || newTarget > 6_000) {
            return """{"error":"target_calories must be between 500 and 6000"}"""
        }
        val prefs = planRepository.preferences.first()
        planRepository.save(prefs.copy(targetCalories = newTarget))
        return """{"success":true,"new_target_calories":$newTarget}"""
    }

    private fun String.esc() = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
