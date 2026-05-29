package com.zack.recomptracker.data.repository

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import java.time.LocalDate

data class DayLog(
    val date: LocalDate,
    val dailyLog: DailyLogEntity?,
    val meals: List<MealEntryEntity>,
    val totals: MacroTotals,
)

data class DailyMetricsInput(
    val date: LocalDate,
    val bodyWeightKg: Double?,
    val waistCm: Double?,
    val steps: Int?,
    val sleepHours: Double?,
    val energyScore: Int?,
    val hungerScore: Int?,
    val sorenessScore: Int?,
    val trained: Boolean,
    val notes: String,
)

data class MealEntryInput(
    val date: LocalDate,
    val mealType: String,
    val name: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
