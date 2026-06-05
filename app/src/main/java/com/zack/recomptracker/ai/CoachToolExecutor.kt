package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.flow.first

class CoachToolExecutor(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
) {

    suspend fun execute(name: String, args: Map<String, String>): String = when (name) {
        "get_today_summary" -> getTodaySummary()
        "get_weekly_trends" -> getWeeklyTrends()
        "get_plan" -> getPlan()
        "log_meal" -> logMeal(args)
        "log_daily_metrics" -> logDailyMetrics(args)
        "update_calorie_target" -> updateCalorieTarget(args)
        else -> """{"error":"unknown tool $name"}"""
    }

    private suspend fun getTodaySummary(): String {
        val today = dateProvider.today()
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
        val calories = args["calories"]?.toIntOrNull() ?: 0
        val proteinG = args["protein_g"]?.toDoubleOrNull() ?: 0.0
        val carbsG = args["carbs_g"]?.toDoubleOrNull() ?: 0.0
        val fatG = args["fat_g"]?.toDoubleOrNull() ?: 0.0
        val mealType = args["meal_type"] ?: "Snack"
        logRepository.addMeal(
            MealEntryInput(
                date = today,
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

    private suspend fun logDailyMetrics(args: Map<String, String>): String {
        val today = dateProvider.today()
        val existing = logRepository.observeDay(today).first().dailyLog
        logRepository.saveDailyMetrics(
            DailyMetricsInput(
                date = today,
                bodyWeightKg = args["weight_kg"]?.toDoubleOrNull() ?: existing?.bodyWeightKg,
                waistCm = args["waist_cm"]?.toDoubleOrNull() ?: existing?.waistCm,
                waistSkinfoldMm = args["waist_skinfold_mm"]?.toDoubleOrNull() ?: existing?.waistSkinfoldMm,
                steps = args["steps"]?.toIntOrNull() ?: existing?.steps,
                sleepHours = args["sleep_hours"]?.toDoubleOrNull() ?: existing?.sleepHours,
                energyScore = args["energy_score"]?.toIntOrNull() ?: existing?.energyScore,
                hungerScore = args["hunger_score"]?.toIntOrNull() ?: existing?.hungerScore,
                sorenessScore = args["soreness_score"]?.toIntOrNull() ?: existing?.sorenessScore,
                trained = args["trained"]?.toBooleanStrictOrNull() ?: existing?.trained ?: false,
                notes = args["notes"] ?: existing?.notes ?: "",
            ),
        )
        return """{"success":true,"date":"$today"}"""
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
