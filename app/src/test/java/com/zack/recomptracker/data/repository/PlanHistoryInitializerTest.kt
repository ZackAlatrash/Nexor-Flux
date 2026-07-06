package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.PlanVersionDao
import com.zack.recomptracker.data.local.entity.PlanVersionEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.plan.PlanHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanHistoryInitializerTest {
    private class FakeDao(initial: List<PlanVersionEntity> = emptyList()) : PlanVersionDao {
        val rows = initial.toMutableList()
        override fun observeAll(): Flow<List<PlanVersionEntity>> = flowOf(rows)
        override suspend fun getAll(): List<PlanVersionEntity> = rows
        override suspend fun count(): Int = rows.size
        override suspend fun upsert(version: PlanVersionEntity) {
            rows.removeAll { it.effectiveFrom == version.effectiveFrom }
            rows.add(version)
        }
        override suspend fun deleteAll() = rows.clear()
    }

    @Test fun `seeds one baseline from current prefs when empty`() = runTest {
        val dao = FakeDao()
        val init = PlanHistoryInitializer(dao) { PlanPreferences(targetCalories = 2345) }
        init.seedIfEmpty()
        assertEquals(1, dao.rows.size)
        assertEquals(PlanHistory.BASELINE_DATE.toString(), dao.rows.first().effectiveFrom)
        assertEquals(2345, dao.rows.first().targetCalories)
    }

    @Test fun `is idempotent — does nothing when already seeded`() = runTest {
        val dao = FakeDao(
            listOf(
                PlanVersionEntity(
                    effectiveFrom = "2026-01-01", targetCalories = 2000, targetProteinG = 1,
                    targetCarbsG = 1, targetFatG = 1, calorieZoneLowerBound = 1,
                    calorieZoneUpperBound = 1, createdAt = "x",
                ),
            ),
        )
        val init = PlanHistoryInitializer(dao) { PlanPreferences(targetCalories = 9999) }
        init.seedIfEmpty()
        assertEquals(1, dao.rows.size)
        assertEquals(2000, dao.rows.first().targetCalories)
    }
}
