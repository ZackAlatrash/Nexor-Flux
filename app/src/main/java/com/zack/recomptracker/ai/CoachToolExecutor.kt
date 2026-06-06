package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first

class CoachToolExecutor(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
) {

    suspend fun execute(name: String, args: Map<String, String>): String = when (name) {
        "get_today_summary" -> getTodaySummary(args)
        "get_weekly_trends" -> getWeeklyTrends()
        "get_plan" -> getPlan()
        "log_meal" -> logMeal(args)
        "log_metric" -> logMetric(args)
        "update_calorie_target" -> updateCalorieTarget(args)
        else -> """{"error":"unknown tool $name"}"""
    }

    private suspend fun getTodaySummary(args: Map<String, String> = emptyMap()): String {
        val today = args["date"]?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        } ?: dateProvider.today()
        val dayLog = logRepository.observeDay(today).first()
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
        val calorieMap = logRepository.observeWeekCalories(start, today).first()
        val calorieEntries = (0..6).joinToString(separator = ",") { offset ->
            val date = start.plusDays(offset.toLong())
            val cals = calorieMap[date] ?: 0
            """{"date":"$date","calories":$cals}"""
        }
        val totalDays = 7
        val loggedDays = calorieMap.values.count { it > 0 }
        val adherencePercent = (loggedDays.toDouble() / totalDays * 100).toInt()
        return """{"week_start":"$start","week_end":"$today","daily_calories":[$calorieEntries],"adherence_percent":$adherencePercent}"""
    }

    private suspend fun getPlan(): String {
        val prefs = planRepository.preferences.first()
        return """{"target_calories":${prefs.targetCalories},"target_protein_g":${prefs.targetProteinG},"target_carbs_g":${prefs.targetCarbsG},"target_fat_g":${prefs.targetFatG},"calorie_zone_lower":${prefs.calorieZoneLowerBound},"calorie_zone_upper":${prefs.calorieZoneUpperBound}}"""
    }

    private suspend fun logMeal(args: Map<String, String>): String {
        val name = args["name"] ?: return """{"error":"log_meal requires 'name'"}"""
        val calories = args["calories"]?.toIntOrNull()
            ?: return """{"error":"log_meal requires 'calories'"}"""
        val mealType = args["meal_type"]
            ?.takeIf { it in setOf("Breakfast", "Lunch", "Dinner", "Snack") }
            ?: "Snack"
        val proteinG = args["protein_g"]?.toDoubleOrNull() ?: 0.0
        val carbsG = args["carbs_g"]?.toDoubleOrNull() ?: 0.0
        val fatG = args["fat_g"]?.toDoubleOrNull() ?: 0.0
        logRepository.addMeal(
            MealEntryInput(
                date = dateProvider.today(),
                mealType = mealType,
                name = name,
                calories = calories,
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
            ),
        )
        return """{"success":true,"logged":"${name.esc()}","calories":$calories}"""
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
        val existing = logRepository.observeDay(today).first().dailyLog
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
        val newTarget = args["target_calories"]?.toIntOrNull()
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
