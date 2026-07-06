package com.zack.recomptracker.data.repository

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.health.HealthConnectReadResult
import com.zack.recomptracker.data.local.dao.DailyLogDao
import com.zack.recomptracker.data.local.dao.MealEntryDao
import com.zack.recomptracker.data.local.dao.MealSlotDao
import com.zack.recomptracker.data.local.dao.PerformanceDao
import com.zack.recomptracker.data.local.dao.SavedFoodDao
import com.zack.recomptracker.data.local.dao.SavedMealDao
import com.zack.recomptracker.data.local.dao.WeeklyReviewDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import com.zack.recomptracker.domain.food.RecentFoods
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class LogRepository(
    private val dailyLogDao: DailyLogDao,
    private val mealEntryDao: MealEntryDao,
    private val savedFoodDao: SavedFoodDao,
    private val savedMealDao: SavedMealDao,
    private val performanceDao: PerformanceDao,
    private val weeklyReviewDao: WeeklyReviewDao,
    private val mealSlotDao: MealSlotDao,
) {
    fun observeDay(date: LocalDate): Flow<DayLog> {
        val storedDate = date.toString()
        return combine(
            dailyLogDao.observeByDate(storedDate),
            mealEntryDao.observeForDate(storedDate),
        ) { log, meals ->
            DayLog(
                date = date,
                dailyLog = log,
                meals = meals,
                totals = meals.filterNot { it.planned }.macroTotals(),
                plannedTotals = meals.filter { it.planned }.macroTotals(),
            )
        }
    }

    /**
     * One-shot suspend read for a single day — suitable for tool queries that don't need
     * live updates. Avoids the combine(Flow, Flow).first() timing hazard where both inner
     * flows must emit before the combined flow fires its first value.
     */
    suspend fun getDay(date: LocalDate): DayLog {
        val storedDate = date.toString()
        val log = dailyLogDao.getByDate(storedDate)
        val meals = mealEntryDao.getForDate(storedDate)
        return DayLog(
            date = date,
            dailyLog = log,
            meals = meals,
            totals = meals.filterNot { it.planned }.macroTotals(),
            plannedTotals = meals.filter { it.planned }.macroTotals(),
        )
    }

    /**
     * One-shot suspend read for calorie totals across a date range.
     * Same rationale as [getDay] — avoids Flow.first() on a mapped single flow.
     */
    suspend fun getWeekCalories(start: LocalDate, end: LocalDate): Map<LocalDate, Int> {
        val entries = mealEntryDao.getBetween(start.toString(), end.toString())
        return entries
            .filterNot { it.planned }
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.calories } }
    }

    /**
     * One-shot read of the eaten (non-planned) meal-entry COUNT per day across a date range — the
     * weekly-rebalance engine's logged-day signal (a day is "logged" iff it has ≥1 meal entry, spec
     * §5.1). Mirrors [getWeekCalories]'s eaten-only convention: planned entries are excluded, so a day
     * holding only unconfirmed plans reads as unlogged (matching how its surplus is computed).
     */
    suspend fun getWeekMealCounts(start: LocalDate, end: LocalDate): Map<LocalDate, Int> {
        val entries = mealEntryDao.getBetween(start.toString(), end.toString())
        return entries
            .filterNot { it.planned }
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, dayEntries) -> dayEntries.size }
    }

    /**
     * One-shot read of per-day macro totals across a date range — used by the AI coach's
     * get_weekly_trends tool so it can answer protein/carb/fat questions without 7 separate
     * get_today_summary calls.
     */
    suspend fun getWeekMacros(start: LocalDate, end: LocalDate): Map<LocalDate, MacroTotals> {
        val entries = mealEntryDao.getBetween(start.toString(), end.toString())
        return entries
            .filterNot { it.planned }
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, dayEntries) -> dayEntries.macroTotals() }
    }

    fun observeDailyLogs(): Flow<List<DailyLogEntity>> = dailyLogDao.observeAll()
    fun observeMealEntries(): Flow<List<MealEntryEntity>> = mealEntryDao.observeAll()
    fun observeMealEntriesSince(startDate: LocalDate): Flow<List<MealEntryEntity>> =
        mealEntryDao.observeBetween(startDate.toString(), "9999-12-31")
    fun observeSavedFoods(): Flow<List<SavedFoodEntity>> = savedFoodDao.observeAll()
    fun observeSavedMeals(): Flow<List<SavedMealEntity>> = savedMealDao.observeAll()
    fun observePerformances(): Flow<List<LiftPerformanceEntity>> = performanceDao.observeAll()
    fun observeWeeklyReviews(): Flow<List<WeeklyReviewEntity>> = weeklyReviewDao.observeAll()

    suspend fun saveDailyMetrics(input: DailyMetricsInput) {
        val existing = dailyLogDao.getByDate(input.date.toString())
        val steps = resolveSavedSteps(input.stepsEdited, input.steps, existing?.steps, existing?.stepsSource)
        dailyLogDao.upsert(
            DailyLogEntity(
                date = input.date.toString(),
                bodyWeightKg = input.bodyWeightKg,
                waistCm = input.waistCm,
                waistSkinfoldMm = input.waistSkinfoldMm,
                steps = steps.steps,
                stepsSource = steps.source,
                sleepHours = input.sleepHours,
                energyScore = input.energyScore?.coerceIn(1, 10),
                hungerScore = input.hungerScore?.coerceIn(1, 10),
                sorenessScore = input.sorenessScore?.coerceIn(1, 10),
                trained = input.trained,
                notes = input.notes.trim(),
            ),
        )
    }

    suspend fun addMeal(input: MealEntryInput): Long = mealEntryDao.insert(
        MealEntryEntity(
            date = input.date.toString(),
            mealType = input.mealType,
            name = input.name.trim(),
            calories = input.calories.coerceAtLeast(0),
            proteinG = input.proteinG.coerceAtLeast(0.0),
            carbsG = input.carbsG.coerceAtLeast(0.0),
            fatG = input.fatG.coerceAtLeast(0.0),
            planned = input.planned,
        ),
    )

    suspend fun deleteMeal(id: Long) {
        mealEntryDao.deleteById(id)
    }

    suspend fun copyMeals(fromDate: LocalDate, toDate: LocalDate) {
        val sourceMeals = mealEntryDao.getForDate(fromDate.toString())
        mealEntryDao.deleteForDate(toDate.toString())
        mealEntryDao.insertAll(
            sourceMeals.map {
                it.copy(
                    id = 0,
                    date = toDate.toString(),
                )
            },
        )
    }

    suspend fun saveFood(food: SavedFoodEntity): Long {
        return if (food.id == 0L) savedFoodDao.insert(food) else {
            savedFoodDao.update(food)
            food.id
        }
    }

    suspend fun deleteFood(id: Long) {
        savedFoodDao.deleteById(id)
    }

    suspend fun saveMeal(meal: SavedMealEntity): Long {
        return if (meal.id == 0L) savedMealDao.insert(meal) else {
            savedMealDao.update(meal)
            meal.id
        }
    }

    suspend fun deleteSavedMeal(id: Long) {
        savedMealDao.deleteById(id)
    }

    suspend fun addSavedFoodToDate(food: SavedFoodEntity, date: LocalDate, mealType: String) {
        addMeal(
            MealEntryInput(
                date = date,
                mealType = mealType,
                name = food.name,
                calories = food.calories,
                proteinG = food.proteinG,
                carbsG = food.carbsG,
                fatG = food.fatG,
            ),
        )
    }

    suspend fun addSavedMealToDate(meal: SavedMealEntity, date: LocalDate) {
        addMeal(
            MealEntryInput(
                date = date,
                mealType = meal.mealType,
                name = meal.name,
                calories = meal.calories,
                proteinG = meal.proteinG,
                carbsG = meal.carbsG,
                fatG = meal.fatG,
            ),
        )
    }

    suspend fun addPerformance(performance: LiftPerformanceEntity): Long = performanceDao.insert(performance)

    suspend fun deletePerformance(id: Long) {
        performanceDao.deleteById(id)
    }

    suspend fun saveWeeklyReview(review: WeeklyReviewEntity) {
        val existing = weeklyReviewDao.getByWeekStart(review.weekStart)
        val merged = if (existing != null && review.briefingJson == null) {
            review.copy(
                briefingJson = existing.briefingJson,
                briefingSignature = existing.briefingSignature,
                briefingGeneratedAt = existing.briefingGeneratedAt,
            )
        } else {
            review
        }
        weeklyReviewDao.upsert(merged)
    }

    fun observeSlots(): Flow<List<MealSlotEntity>> = mealSlotDao.observeAll()

    suspend fun getSlots(): List<MealSlotEntity> = mealSlotDao.getAll()

    suspend fun getSavedFoods(): List<SavedFoodEntity> = savedFoodDao.getAll()

    suspend fun getMealEntry(id: Long): MealEntryEntity? = mealEntryDao.getById(id)

    suspend fun getMealEntriesForSlot(date: String, slotId: Long?): List<MealEntryEntity> =
        mealEntryDao.getForDate(date).filter { it.slotId == slotId }

    suspend fun addSlot(name: String) {
        val maxOrder = mealSlotDao.getAll().maxOfOrNull { it.sortOrder } ?: -1
        mealSlotDao.insert(MealSlotEntity(name = name.trim(), sortOrder = maxOrder + 1))
    }

    suspend fun renameSlot(id: Long, name: String) {
        mealSlotDao.getAll()
            .firstOrNull { it.id == id }
            ?.let { mealSlotDao.update(it.copy(name = name.trim())) }
    }

    suspend fun deleteSlot(id: Long) {
        mealEntryDao.deleteBySlotId(id)
        mealSlotDao.deleteById(id)
    }

    suspend fun reorderSlots(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            mealSlotDao.updateSortOrder(id, index)
        }
    }

    suspend fun addMealToSlot(
        input: MealEntryInput,
        slotId: Long?,
    ): Long = mealEntryDao.insert(
        MealEntryEntity(
            date = input.date.toString(),
            mealType = input.mealType,
            name = input.name.trim(),
            calories = input.calories.coerceAtLeast(0),
            proteinG = input.proteinG.coerceAtLeast(0.0),
            carbsG = input.carbsG.coerceAtLeast(0.0),
            fatG = input.fatG.coerceAtLeast(0.0),
            slotId = slotId,
            amountGrams = input.amountGrams,
            basePer100Calories = input.basePer100Calories,
            basePer100ProteinG = input.basePer100ProteinG,
            basePer100CarbsG = input.basePer100CarbsG,
            basePer100FatG = input.basePer100FatG,
            entryServingName = input.entryServingName,
            entryServingGrams = input.entryServingGrams,
            loggedByServings = input.loggedByServings,
            planned = input.planned,
        ),
    )

    /** Confirm a single planned entry (planned → eaten), or re-plan it (eaten → planned). */
    suspend fun setMealPlanned(id: Long, planned: Boolean) = mealEntryDao.setPlanned(id, planned)

    /** "Confirm all" — mark every planned entry on [date] as eaten. */
    suspend fun confirmPlannedForDate(date: LocalDate) =
        mealEntryDao.confirmPlannedForDate(date.toString())

    /**
     * Move an entry to [date]. [planned] is recomputed by the caller (which knows "today")
     * so a meal moved to a future day becomes a plan, and one moved to today/past becomes eaten.
     */
    suspend fun moveMealToDate(id: Long, date: LocalDate, planned: Boolean) =
        mealEntryDao.setDateAndPlanned(id, date.toString(), planned)

    /**
     * Live count of unconfirmed plans in `[floor, today)` — drives the reconcile prompt.
     * [floor] bounds the count to the days the user can navigate to, so the nudge never
     * points at an unreachable past day.
     */
    fun observeStalePlannedCount(today: LocalDate, floor: LocalDate): Flow<Int> =
        mealEntryDao.observeStalePlannedCount(floor.toString(), today.toString())

    suspend fun updateMealEntry(entry: MealEntryEntity) {
        mealEntryDao.update(
            entry.copy(
                name = entry.name.trim(),
                calories = entry.calories.coerceAtLeast(0),
                proteinG = entry.proteinG.coerceAtLeast(0.0),
                carbsG = entry.carbsG.coerceAtLeast(0.0),
                fatG = entry.fatG.coerceAtLeast(0.0),
            ),
        )
    }

    fun observeWeekCalories(start: LocalDate, end: LocalDate): Flow<Map<LocalDate, Int>> =
        mealEntryDao.observeBetween(start.toString(), end.toString())
            .map { entries ->
                entries
                    .filterNot { it.planned }
                    .groupBy { LocalDate.parse(it.date) }
                    .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.calories } }
            }

    fun observeRecentFoods(limit: Int = RecentFoods.DEFAULT_LIMIT): Flow<List<MealEntryEntity>> =
        mealEntryDao.observeFoodLibraryEntries().map { RecentFoods.fromEntries(it, limit) }

    suspend fun resetAllLogs() {
        weeklyReviewDao.deleteAll()
        performanceDao.deleteAll()
        mealEntryDao.deleteAll()
        dailyLogDao.deleteAll()
    }

    suspend fun resetEverything() {
        resetAllLogs()
        savedFoodDao.deleteAll()
        savedMealDao.deleteAll()
    }

    suspend fun applyHealthConnectSync(date: LocalDate, result: HealthConnectReadResult) {
        if (result.steps == null && result.weightKg == null && result.sleepHours == null) return
        val existing = dailyLogDao.getByDate(date.toString())
        val base = existing ?: DailyLogEntity(date = date.toString())
        val steps = reconcileSteps(base.steps, base.stepsSource, result.steps)
        val updated = base.copy(
            steps = steps.steps,
            stepsSource = steps.source,
            bodyWeightKg = existing?.bodyWeightKg ?: result.weightKg,
            sleepHours = existing?.sleepHours ?: result.sleepHours,
        )
        if (updated != existing) dailyLogDao.upsert(updated)
    }

    /**
     * Backfills steps for many past days from a Health Connect history read. Each day is reconciled
     * by provenance ([reconcileSteps]) so a manually-entered day is never overwritten. Only changed
     * rows are written.
     */
    suspend fun applyHealthConnectStepsHistory(stepsByDate: Map<LocalDate, Int>) {
        stepsByDate.forEach { (date, steps) ->
            val existing = dailyLogDao.getByDate(date.toString())
            val base = existing ?: DailyLogEntity(date = date.toString())
            val reconciled = reconcileSteps(base.steps, base.stepsSource, steps)
            val updated = base.copy(steps = reconciled.steps, stepsSource = reconciled.source)
            if (updated != existing) dailyLogDao.upsert(updated)
        }
    }
}

fun List<MealEntryEntity>.macroTotals(): MacroTotals = fold(MacroTotals()) { total, meal ->
    total + MacroTotals(
        calories = meal.calories,
        proteinG = meal.proteinG,
        carbsG = meal.carbsG,
        fatG = meal.fatG,
    )
}
