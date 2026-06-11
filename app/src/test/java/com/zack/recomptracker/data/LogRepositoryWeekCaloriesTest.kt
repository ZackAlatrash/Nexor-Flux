package com.zack.recomptracker.data

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
import com.zack.recomptracker.data.repository.LogRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LogRepositoryWeekCaloriesTest {

    private fun entry(date: String, calories: Int, planned: Boolean = false) = MealEntryEntity(
        id = 0, date = date, mealType = "FOOD_LIBRARY", name = "food",
        calories = calories, proteinG = 0.0, carbsG = 0.0, fatG = 0.0, planned = planned,
    )

    private fun buildRepo(entries: List<MealEntryEntity>): LogRepository {
        val dao = object : MealEntryDao {
            override fun observeForDate(date: String) = flowOf(entries.filter { it.date == date })
            override suspend fun getForDate(date: String) = entries.filter { it.date == date }
            override fun observeBetween(s: String, e: String): Flow<List<MealEntryEntity>> =
                flowOf(entries.filter { it.date in s..e })
            override suspend fun getBetween(startDate: String, endDate: String) =
                entries.filter { it.date in startDate..endDate }
            override fun observeAll() = flowOf(entries)
            override fun observeFoodLibraryEntries() = flowOf(emptyList<MealEntryEntity>())
            override suspend fun getAll() = entries
            override suspend fun insert(entry: MealEntryEntity) = 0L
            override suspend fun insertAll(entries: List<MealEntryEntity>) = Unit
            override suspend fun update(entry: MealEntryEntity) = Unit
            override suspend fun delete(entry: MealEntryEntity) = Unit
            override suspend fun deleteById(id: Long) = Unit
            override suspend fun deleteForDate(date: String) = Unit
            override suspend fun deleteAll() = Unit
            override suspend fun clearSlotId(slotId: Long) = Unit
            override suspend fun deleteBySlotId(slotId: Long) = Unit
            override suspend fun getById(id: Long): MealEntryEntity? = null
            override suspend fun setPlanned(id: Long, planned: Boolean) = Unit
            override suspend fun confirmPlannedForDate(date: String) = Unit
            override suspend fun setDateAndPlanned(id: Long, date: String, planned: Boolean) = Unit
            override fun observeStalePlannedCount(floor: String, date: String) = flowOf(0)
        }
        return LogRepository(
            dailyLogDao = WcNoopDailyLogDao,
            mealEntryDao = dao,
            savedFoodDao = WcNoopSavedFoodDao,
            savedMealDao = WcNoopSavedMealDao,
            performanceDao = WcNoopPerformanceDao,
            weeklyReviewDao = WcNoopWeeklyReviewDao,
            mealSlotDao = WcNoopMealSlotDao,
        )
    }

    @Test
    fun `observeWeekCalories sums calories per date`() = runTest {
        val jun1 = LocalDate.of(2026, 6, 1)
        val jun2 = LocalDate.of(2026, 6, 2)
        val entries = listOf(
            entry("2026-06-01", 500),
            entry("2026-06-01", 700),
            entry("2026-06-02", 400),
        )
        val repo = buildRepo(entries)

        val result = repo.observeWeekCalories(
            start = LocalDate.of(2026, 5, 26),
            end   = LocalDate.of(2026, 6, 1),
        ).first()

        assertEquals(1200, result[jun1])
        assertEquals(null, result[jun2]) // jun2 is outside the range
    }

    @Test
    fun `observeWeekCalories returns empty map when no entries`() = runTest {
        val repo = buildRepo(emptyList())
        val result = repo.observeWeekCalories(
            start = LocalDate.of(2026, 5, 26),
            end   = LocalDate.of(2026, 6, 1),
        ).first()
        assertEquals(emptyMap<LocalDate, Int>(), result)
    }

    @Test
    fun `observeWeekCalories excludes planned entries`() = runTest {
        val jun1 = LocalDate.of(2026, 6, 1)
        val repo = buildRepo(
            listOf(
                entry("2026-06-01", 500),                  // eaten
                entry("2026-06-01", 700, planned = true),  // planned — must not count
            ),
        )
        val result = repo.observeWeekCalories(
            start = LocalDate.of(2026, 5, 26),
            end   = LocalDate.of(2026, 6, 1),
        ).first()
        assertEquals(500, result[jun1])
    }

    @Test
    fun `observeDay splits eaten and planned totals`() = runTest {
        val jun1 = LocalDate.of(2026, 6, 1)
        val repo = buildRepo(
            listOf(
                entry("2026-06-01", 500),                  // eaten
                entry("2026-06-01", 300, planned = true),  // planned
            ),
        )
        val day = repo.observeDay(jun1).first()
        assertEquals(500, day.totals.calories)
        assertEquals(300, day.plannedTotals.calories)
        assertEquals(2, day.meals.size) // both entries are still present for the UI
    }
}

// ── Noop stubs ────────────────────────────────────────────────────────────────

private object WcNoopDailyLogDao : DailyLogDao {
    override fun observeByDate(date: String): Flow<DailyLogEntity?> = flow<DailyLogEntity?> { emit(null) }
    override fun observeAll(): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override fun observeBetween(s: String, e: String): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override suspend fun getByDate(date: String): DailyLogEntity? = null
    override suspend fun getAll(): List<DailyLogEntity> = emptyList()
    override suspend fun upsert(log: DailyLogEntity) = Unit
    override suspend fun insertAll(logs: List<DailyLogEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private object WcNoopSavedFoodDao : SavedFoodDao {
    override fun observeAll(): Flow<List<SavedFoodEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll() = emptyList<SavedFoodEntity>()
    override suspend fun insert(food: SavedFoodEntity) = 0L
    override suspend fun insertAll(foods: List<SavedFoodEntity>) = Unit
    override suspend fun update(food: SavedFoodEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object WcNoopSavedMealDao : SavedMealDao {
    override fun observeAll(): Flow<List<SavedMealEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll() = emptyList<SavedMealEntity>()
    override suspend fun insert(meal: SavedMealEntity) = 0L
    override suspend fun insertAll(meals: List<SavedMealEntity>) = Unit
    override suspend fun update(meal: SavedMealEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object WcNoopPerformanceDao : PerformanceDao {
    override fun observeAll(): Flow<List<LiftPerformanceEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll() = emptyList<LiftPerformanceEntity>()
    override suspend fun insert(p: LiftPerformanceEntity) = 0L
    override suspend fun insertAll(performances: List<LiftPerformanceEntity>) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object WcNoopWeeklyReviewDao : WeeklyReviewDao {
    override fun observeAll(): Flow<List<WeeklyReviewEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll() = emptyList<WeeklyReviewEntity>()
    override suspend fun getByWeekStart(weekStart: String): WeeklyReviewEntity? = null
    override suspend fun upsert(review: WeeklyReviewEntity) = Unit
    override suspend fun insertAll(reviews: List<WeeklyReviewEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private object WcNoopMealSlotDao : MealSlotDao {
    override fun observeAll(): Flow<List<MealSlotEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll() = emptyList<MealSlotEntity>()
    override suspend fun insert(slot: MealSlotEntity) = 0L
    override suspend fun update(slot: MealSlotEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun updateSortOrder(id: Long, order: Int) = Unit
}
