package com.zack.recomptracker.data.repository

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.data.health.HealthConnectReadResult
import com.zack.recomptracker.domain.food.RecentFoods
import kotlinx.coroutines.flow.map
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
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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
                totals = meals.macroTotals(),
            )
        }
    }

    fun observeDailyLogs(): Flow<List<DailyLogEntity>> = dailyLogDao.observeAll()
    fun observeMealEntries(): Flow<List<MealEntryEntity>> = mealEntryDao.observeAll()
    fun observeSavedFoods(): Flow<List<SavedFoodEntity>> = savedFoodDao.observeAll()
    fun observeSavedMeals(): Flow<List<SavedMealEntity>> = savedMealDao.observeAll()
    fun observePerformances(): Flow<List<LiftPerformanceEntity>> = performanceDao.observeAll()
    fun observeWeeklyReviews(): Flow<List<WeeklyReviewEntity>> = weeklyReviewDao.observeAll()

    suspend fun saveDailyMetrics(input: DailyMetricsInput) {
        dailyLogDao.upsert(
            DailyLogEntity(
                date = input.date.toString(),
                bodyWeightKg = input.bodyWeightKg,
                waistCm = input.waistCm,
                steps = input.steps,
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
        weeklyReviewDao.upsert(review)
    }

    fun observeSlots(): Flow<List<MealSlotEntity>> = mealSlotDao.observeAll()

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
        mealSlotDao.deleteById(id)
        mealEntryDao.clearSlotId(id)
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
        ),
    )

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
        val updated = (existing ?: DailyLogEntity(date = date.toString())).copy(
            steps = existing?.steps ?: result.steps,
            bodyWeightKg = existing?.bodyWeightKg ?: result.weightKg,
            sleepHours = existing?.sleepHours ?: result.sleepHours,
        )
        if (updated != existing) dailyLogDao.upsert(updated)
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
