package com.zack.recomptracker.data.repository

import androidx.compose.runtime.Immutable
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import java.time.LocalDate

data class DayLog(
    val date: LocalDate,
    val dailyLog: DailyLogEntity?,
    val meals: List<MealEntryEntity>,
    /** Totals of *eaten* entries only (planned entries excluded). */
    val totals: MacroTotals,
    /** Totals of *planned* entries only. Zero on days with no plans. */
    val plannedTotals: MacroTotals = MacroTotals(),
)

data class DailyMetricsInput(
    val date: LocalDate,
    val bodyWeightKg: Double?,
    val waistCm: Double?,
    val waistSkinfoldMm: Double? = null,
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
    val amountGrams: Double? = null,
    val basePer100Calories: Int? = null,
    val basePer100ProteinG: Double? = null,
    val basePer100CarbsG: Double? = null,
    val basePer100FatG: Double? = null,
    val entryServingName: String? = null,
    val entryServingGrams: Double? = null,
    val loggedByServings: Boolean = false,
    /** When true the entry is saved as a plan (not yet eaten). See [MealEntryEntity.planned]. */
    val planned: Boolean = false,
)

@Immutable
data class DayCalorieSummary(val date: LocalDate, val calories: Int)
