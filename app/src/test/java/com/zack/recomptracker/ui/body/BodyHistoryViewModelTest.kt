package com.zack.recomptracker.ui.body

import com.zack.recomptracker.core.time.DateProvider
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyHistoryViewModelTest {

    private val today = LocalDate.of(2026, 5, 31)

    private fun buildVm(logs: List<DailyLogEntity>, todayDate: LocalDate = today): BodyHistoryViewModel {
        val logsFlow = MutableStateFlow(logs)
        val repo = LogRepository(
            dailyLogDao = object : NoopDailyLogDao() {
                override fun observeAll(): Flow<List<DailyLogEntity>> = logsFlow
            },
            mealEntryDao = NoopMealEntryDao,
            savedFoodDao = NoopSavedFoodDao,
            savedMealDao = NoopSavedMealDao,
            performanceDao = NoopPerformanceDao,
            weeklyReviewDao = NoopWeeklyReviewDao,
            mealSlotDao = NoopMealSlotDao,
        )
        val dateProvider = object : DateProvider { override fun today() = todayDate }
        return BodyHistoryViewModel(repo, dateProvider)
    }

    @Test
    fun `logged entry appears as Logged item`() = runTest {
        val log = DailyLogEntity(date = today.toString(), bodyWeightKg = 82.4)
        val first = buildVm(listOf(log)).items.first().first()
        assertTrue(first is BodyHistoryItem.Logged)
        assertEquals(today, (first as BodyHistoryItem.Logged).date)
    }

    @Test
    fun `missing day appears as Missing item`() = runTest {
        val first = buildVm(emptyList()).items.first().first()
        assertTrue(first is BodyHistoryItem.Missing)
        assertEquals(today, (first as BodyHistoryItem.Missing).date)
    }

    @Test
    fun `items are sorted newest first`() = runTest {
        val logs = listOf(
            DailyLogEntity(date = today.minusDays(2).toString()),
            DailyLogEntity(date = today.toString()),
        )
        val items = buildVm(logs).items.first()
        assertEquals(today, items.first().itemDate())
        assertTrue(items[1].itemDate() < items[0].itemDate())
    }

    @Test
    fun `includes entry earlier than 90 days`() = runTest {
        val old = today.minusDays(120)
        val items = buildVm(listOf(DailyLogEntity(date = old.toString()))).items.first()
        assertTrue(items.any { it.itemDate() == old })
    }

    private fun BodyHistoryItem.itemDate() = when (this) {
        is BodyHistoryItem.Logged -> date
        is BodyHistoryItem.Missing -> date
    }
}

// ── Noop stubs ───────────────────────────────────────────────────────────────

private abstract class NoopDailyLogDao : DailyLogDao {
    override fun observeByDate(date: String): Flow<DailyLogEntity?> = flow { emit(null) }
    override fun observeAll(): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override suspend fun getByDate(date: String): DailyLogEntity? = null
    override suspend fun getAll(): List<DailyLogEntity> = emptyList()
    override fun observeBetween(s: String, e: String): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override suspend fun upsert(log: DailyLogEntity) = Unit
    override suspend fun insertAll(logs: List<DailyLogEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private object NoopMealEntryDao : MealEntryDao {
    override fun observeForDate(date: String): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override suspend fun getForDate(date: String): List<MealEntryEntity> = emptyList()
    override fun observeBetween(s: String, e: String): Flow<List<MealEntryEntity>> = flow { emit(emptyList()) }
    override suspend fun getBetween(startDate: String, endDate: String): List<MealEntryEntity> = emptyList()
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
    override suspend fun deleteBySlotId(slotId: Long) = Unit
    override suspend fun getById(id: Long): MealEntryEntity? = null
    override suspend fun setPlanned(id: Long, planned: Boolean) = Unit
    override suspend fun confirmPlannedForDate(date: String) = Unit
    override suspend fun setDateAndPlanned(id: Long, date: String, planned: Boolean) = Unit
    override fun observeStalePlannedCount(floor: String, date: String): Flow<Int> = flow { emit(0) }
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
    override suspend fun insert(p: LiftPerformanceEntity): Long = 0L
    override suspend fun insertAll(ps: List<LiftPerformanceEntity>) = Unit
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
