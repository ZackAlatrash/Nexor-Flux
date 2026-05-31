package com.zack.recomptracker.ui.body

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BodyEditViewModelTest {

    private val date = LocalDate.of(2026, 5, 29)

    private fun buildVm(
        existing: DailyLogEntity? = null,
        upserted: MutableList<DailyLogEntity> = mutableListOf(),
    ): BodyEditViewModel {
        val repo = LogRepository(
            dailyLogDao = object : BENoopDailyLogDao() {
                override fun observeByDate(d: String): Flow<DailyLogEntity?> =
                    flow { emit(if (d == date.toString()) existing else null) }
                override suspend fun upsert(log: DailyLogEntity) { upserted.add(log) }
            },
            mealEntryDao = BENoopMealEntryDao,
            savedFoodDao = BENoopSavedFoodDao,
            savedMealDao = BENoopSavedMealDao,
            performanceDao = BENoopPerformanceDao,
            weeklyReviewDao = BENoopWeeklyReviewDao,
            mealSlotDao = BENoopMealSlotDao,
        )
        return BodyEditViewModel(repo, SavedStateHandle(mapOf("date" to date.toString())))
    }

    @Test
    fun `initial state has correct date`() = runTest {
        val vm = buildVm()
        assertEquals(date, vm.uiState.first().date)
    }

    @Test
    fun `prefills state from existing log`() = runTest {
        val log = DailyLogEntity(date = date.toString(), bodyWeightKg = 82.4, waistSkinfoldMm = 18.0)
        val vm = buildVm(existing = log)
        val state = vm.uiState.first { it.bodyWeightKg.isNotEmpty() }
        assertEquals("82.4", state.bodyWeightKg)
        assertEquals("18.0", state.waistSkinfoldMm)
    }

    @Test
    fun `saveMetrics writes entity with correct date`() = runTest {
        val upserted = mutableListOf<DailyLogEntity>()
        val vm = buildVm(upserted = upserted)
        vm.onBodyWeightChanged("83.0")
        vm.saveMetrics()
        vm.saved.first()
        assertEquals(1, upserted.size)
        assertEquals(date.toString(), upserted[0].date)
        assertEquals(83.0, upserted[0].bodyWeightKg)
    }

    @Test
    fun `saveMetrics emits saved event`() = runTest {
        val vm = buildVm()
        vm.saveMetrics()
        assertNotNull(vm.saved.first())
    }
}

// ── Noop stubs ───────────────────────────────────────────────────────────────

private abstract class BENoopDailyLogDao : DailyLogDao {
    override fun observeByDate(date: String): Flow<DailyLogEntity?> = flow { emit(null) }
    override fun observeAll(): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override suspend fun getByDate(date: String): DailyLogEntity? = null
    override suspend fun getAll(): List<DailyLogEntity> = emptyList()
    override fun observeBetween(s: String, e: String): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
    override suspend fun upsert(log: DailyLogEntity) = Unit
    override suspend fun insertAll(logs: List<DailyLogEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private object BENoopMealEntryDao : MealEntryDao {
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

private object BENoopSavedFoodDao : SavedFoodDao {
    override fun observeAll(): Flow<List<SavedFoodEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<SavedFoodEntity> = emptyList()
    override suspend fun insert(food: SavedFoodEntity): Long = 0L
    override suspend fun insertAll(foods: List<SavedFoodEntity>) = Unit
    override suspend fun update(food: SavedFoodEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object BENoopSavedMealDao : SavedMealDao {
    override fun observeAll(): Flow<List<SavedMealEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<SavedMealEntity> = emptyList()
    override suspend fun insert(meal: SavedMealEntity): Long = 0L
    override suspend fun insertAll(meals: List<SavedMealEntity>) = Unit
    override suspend fun update(meal: SavedMealEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object BENoopPerformanceDao : PerformanceDao {
    override fun observeAll(): Flow<List<LiftPerformanceEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<LiftPerformanceEntity> = emptyList()
    override suspend fun insert(p: LiftPerformanceEntity): Long = 0L
    override suspend fun insertAll(ps: List<LiftPerformanceEntity>) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun deleteAll() = Unit
}

private object BENoopWeeklyReviewDao : WeeklyReviewDao {
    override fun observeAll(): Flow<List<WeeklyReviewEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<WeeklyReviewEntity> = emptyList()
    override suspend fun upsert(review: WeeklyReviewEntity) = Unit
    override suspend fun insertAll(reviews: List<WeeklyReviewEntity>) = Unit
    override suspend fun deleteAll() = Unit
}

private object BENoopMealSlotDao : MealSlotDao {
    override fun observeAll(): Flow<List<MealSlotEntity>> = flow { emit(emptyList()) }
    override suspend fun getAll(): List<MealSlotEntity> = emptyList()
    override suspend fun insert(slot: MealSlotEntity): Long = 0L
    override suspend fun update(slot: MealSlotEntity) = Unit
    override suspend fun deleteById(id: Long) = Unit
    override suspend fun updateSortOrder(id: Long, order: Int) = Unit
}
