package com.zack.recomptracker.data

import com.zack.recomptracker.data.local.dao.DailyLogDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.repository.markDayTrained
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkDayTrainedTest {
    private fun fakeDao(store: MutableMap<String, DailyLogEntity>) = object : DailyLogDao {
        override fun observeByDate(date: String): Flow<DailyLogEntity?> = flow { emit(store[date]) }
        override fun observeAll(): Flow<List<DailyLogEntity>> = flow { emit(store.values.toList()) }
        override fun observeBetween(s: String, e: String): Flow<List<DailyLogEntity>> = flow { emit(emptyList()) }
        override suspend fun getByDate(date: String): DailyLogEntity? = store[date]
        override suspend fun getAll(): List<DailyLogEntity> = store.values.toList()
        override suspend fun upsert(log: DailyLogEntity) { store[log.date] = log }
        override suspend fun insertAll(logs: List<DailyLogEntity>) { logs.forEach { store[it.date] = it } }
        override suspend fun deleteAll() { store.clear() }
        override suspend fun insertEmptyIfAbsent(date: String) { store.getOrPut(date) { DailyLogEntity(date = date) } }
        override suspend fun updateBodyWeightKg(date: String, v: Double) { store[date]?.let { store[date] = it.copy(bodyWeightKg = v) } }
        override suspend fun updateWaistCm(date: String, v: Double) { store[date]?.let { store[date] = it.copy(waistCm = v) } }
        override suspend fun updateSleepHours(date: String, v: Double) { store[date]?.let { store[date] = it.copy(sleepHours = v) } }
        override suspend fun updateEnergyScore(date: String, v: Int) { store[date]?.let { store[date] = it.copy(energyScore = v) } }
        override suspend fun updateHungerScore(date: String, v: Int) { store[date]?.let { store[date] = it.copy(hungerScore = v) } }
        override suspend fun updateSorenessScore(date: String, v: Int) { store[date]?.let { store[date] = it.copy(sorenessScore = v) } }
    }

    @Test fun createsRowWhenAbsent() = runTest {
        val store = mutableMapOf<String, DailyLogEntity>()
        markDayTrained(fakeDao(store), "2026-06-22")
        assertTrue(store["2026-06-22"]!!.trained)
    }

    @Test fun preservesOtherMetrics() = runTest {
        val store = mutableMapOf(
            "2026-06-22" to DailyLogEntity(date = "2026-06-22", steps = 9000, bodyWeightKg = 80.0),
        )
        markDayTrained(fakeDao(store), "2026-06-22")
        val log = store["2026-06-22"]!!
        assertTrue(log.trained)
        assertEquals(9000, log.steps)
        assertEquals(80.0, log.bodyWeightKg!!, 0.001)
    }
}
