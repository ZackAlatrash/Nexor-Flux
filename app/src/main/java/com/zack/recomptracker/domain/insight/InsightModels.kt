package com.zack.recomptracker.domain.insight

import java.time.LocalDate

/** One calendar day's eaten nutrition. [logged] is false for days with no eaten intake. */
data class DayNutrition(
    val date: LocalDate,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val logged: Boolean,
)

/** Plan targets the detectors compare against. */
data class NutritionTargets(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val calorieZoneLower: Int,
    val calorieZoneUpper: Int,
)

enum class InsightFactType { DERAILMENT_DAY, WEAKEST_MACRO, WEEKDAY_WEEKEND, STREAK, CROSS_METRIC }

/** A computed, non-obvious fact. [statement] carries the numbers and is the LLM input + dedup key. */
data class InsightFact(
    val type: InsightFactType,
    val priority: Int,
    val statement: String,
)
