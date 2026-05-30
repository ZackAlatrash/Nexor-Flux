package com.zack.recomptracker.data

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
import com.zack.recomptracker.data.repository.LogRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogRepositorySyncTest {

    private val date = LocalDate.of(2026, 5, 29)

    private fun buildRepository(dao: FakeDailyLogDao) = LogRepository(
        dailyLogDao = dao,
        mealEntryDao = NoopMealEntryDao,
        savedFoodDao = NoopSavedFoodDao,
        savedMealDao = NoopSavedMealDao,
        performanceDao = NoopPerformanceDao,
        weeklyReviewDao = NoopWeeklyReviewDao,
        mealSlotDao = NoopMealSlotDao,
    )

    @Test
    fun `sync writes all fields when no log exists for today`() = runTest {
        val dao = FakeDailyLogDao()
        val repo = buildRepository(dao)
        val result = HealthConnectReadResult(steps = 8_000, weightKg = 75.5, sleepHours = 7.5)

        repo.applyHealthConnectSync(date, result)

        val saved = dao.logs[date.toString()]!!
        assertEquals(8_000, saved.steps)
        assertEquals(75.5, saved.bodyWeightKg)
        assertEquals(7.5, saved.sleepHours)
    }

    @Test
    fun `sync does not overwrite fields already set by the user`() = runTest {
        val dao = FakeDailyLogDao()
        dao.logs[date.toString()] = DailyLogEntity(
            date = date.toString(),
            steps = 10_000,
            bodyWeightKg = 80.0,
            sleepHours = null,
        )
        val repo = buildRepository(dao)
        val result = HealthConnectReadResult(steps = 5_000, weightKg = 77.0, sleepHours = 6.0)

        repo.applyHealthConnectSync(date, result)

        val saved = dao.logs[date.toString()]!!
        assertEquals(10_000, saved.steps)        // kept original
        assertEquals(80.0, saved.bodyWeightKg)   // kept original
        assertEquals(6.0, saved.sleepHours)      // filled from HC (was null)
    }

    @Test
    fun `sync does nothing when result has all nulls`() = runTest {
        val dao = FakeDailyLogDao()
        val repo = buildRepository(dao)
        val result = HealthConnectReadResult()

        repo.applyHealthConnectSync(date, result)

        // Early return fires — no DB read or write happens.
        assertNull(dao.logs[date.toString()])
    }

    @Test
    fun `sync does not call upsert when nothing would change`() = runTest {
        val dao = FakeDailyLogDao()
        dao.logs[date.toString()] = DailyLogEntity(
            date = date.toString(),
            steps = 9_000,
            bodyWeightKg = 78.0,
            sleepHours = 8.0,
        )
        val repo = buildRepository(dao)
        val result = HealthConnectReadResult(steps = 1_000, weightKg = 60.0, sleepHours = 5.0)

        val upsertCountBefore = dao.upsertCount
        repo.applyHealthConnectSync(date, result)

        assertEquals(upsertCountBefore, dao.upsertCount) // nothing changed, no write
    }

    @Test
    fun `sync preserves non-HC fields on existing log`() = runTest {
        val dao = FakeDailyLogDao()
        val repo = buildRepository(dao)
        dao.logs[date.toString()] = DailyLogEntity(
            date = date.toString(),
            trained = true,
            notes = "leg day",
            sleepHours = null,
        )

        repo.applyHealthConnectSync(date, HealthConnectReadResult(sleepHours = 7.0))

        val saved = dao.logs[date.toString()]!!
        assertEquals(true, saved.trained)
        assertEquals("leg day", saved.notes)
        assertEquals(7.0, saved.sleepHours)
    }
}

// ── Fake DAO ────────────────────────────────────────────────────────────────

private class FakeDailyLogDao : DailyLogDao {
    val logs = mutableMapOf<String, DailyLogEntity>()
    var upsertCount = 0

    override suspend fun getByDate(date: String): DailyLogEntity? = logs[date]
    override suspend fun upsert(log: DailyLogEntity) { logs[log.date] = log; upsertCount++ }

    override fun observeByDate(date: String): Flow<DailyLogEntity?> = flow { emit(logs[date]) }
    override fun observeAll(): Flow<List<DailyLogEntity>> = flow { emit(logs.values.toList()) }
    override suspend fun getAll(): List<DailyLogEntity> = logs.values.toList()
    override fun observeBetween(startDate: String, endDate: String): Flow<List<DailyLogEntity>> =
        flow { emit(emptyList()) }
    override suspend fun insertAll(logs: List<DailyLogEntity>) = logs.forEach { this.logs[it.date] = it }
    override suspend fun deleteAll() = this.logs.clear()
}

// ── Noop stubs for DAOs not under test ──────────────────────────────────────

private object NoopMealEntryDao : MealEntryDao {
    override fun observeForDate(date: String): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override suspend fun getForDate(date: String): List<MealEntryEntity> = emptyList()
    override fun observeBetween(s: String, e: String): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override fun observeAll(): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override fun observeFoodLibraryEntries(): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<MealEntryEntity> = emptyList()
    override suspend fun insert(entry: MealEntryEntity): Long = 0L
    override suspend fun insertAll(entries: List<MealEntryEntity>) = Unit
    override suspend fun update(entry: MealEntryEntity) = Unit
    override suspend fun delete(entry: MealEntryEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteForDate(date: String) = Unit
    override suspend fun deleteAll() = Unit
    override suspend fun clearSlotId(slotId: Long) = Unit
    override suspend fun getById(id: Long): MealEntryEntity? = null
}

private object NoopSavedFoodDao : SavedFoodDao {
    override fun observeAll(): Flow<List<SavedFoodEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<SavedFoodEntity> = emptyList()
    override suspend fun insert(food: SavedFoodEntity): Long = 0L
    override suspend fun insertAll(foods: List<SavedFoodEntity>) = Unit
    override suspend fun update(food: SavedFoodEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopSavedMealDao : SavedMealDao {
    override fun observeAll(): Flow<List<SavedMealEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<SavedMealEntity> = emptyList()
    override suspend fun insert(meal: SavedMealEntity): Long = 0L
    override suspend fun insertAll(meals: List<SavedMealEntity>) = Unit
    override suspend fun update(meal: SavedMealEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopPerformanceDao : PerformanceDao {
    override fun observeAll(): Flow<List<LiftPerformanceEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<LiftPerformanceEntity> = emptyList()
    override suspend fun insert(performance: LiftPerformanceEntity): Long = 0L
    override suspend fun insertAll(performances: List<LiftPerformanceEntity>) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopWeeklyReviewDao : WeeklyReviewDao {
    override fun observeAll(): Flow<List<WeeklyReviewEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<WeeklyReviewEntity> = emptyList()
    override suspend fun upsert(review: WeeklyReviewEntity) = Unit
    override suspend fun insertAll(reviews: List<WeeklyReviewEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopMealSlotDao : MealSlotDao {
    override fun observeAll(): Flow<List<MealSlotEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<MealSlotEntity> = emptyList()
    override suspend fun insert(slot: MealSlotEntity): Long = 0L
    override suspend fun update(slot: MealSlotEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun updateSortOrder(id: Long, order: Int) = Unit
}
