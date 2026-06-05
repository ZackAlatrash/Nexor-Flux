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
        val today = dateProvider.today()
        val name = args["name"] ?: return """{"error":"log_meal requires 'name'"}"""
        val calories = args["calories"]?.toIntOrNull()
            ?: return """{"error":"log_meal requires 'calories'"}"""
        logRepository.addMeal(
            MealEntryInput(
                date = today,
                mealType = "Snack",
                name = name,
                calories = calories,
                proteinG = 0.0,
                carbsG = 0.0,
                fatG = 0.0,
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
        val prefs = planRepository.preferences.first()
        planRepository.save(prefs.copy(targetCalories = newTarget))
        return """{"success":true,"new_target_calories":$newTarget}"""
    }

    private fun String.esc() = replace("\"", "\\\"")
}
