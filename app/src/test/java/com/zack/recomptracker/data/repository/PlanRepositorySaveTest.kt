package com.zack.recomptracker.data.repository

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.dao.PlanVersionDao
import com.zack.recomptracker.data.local.entity.PlanVersionEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.PlanPreferencesSource
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the [PlanRepository.save] choke-point: a history version is recorded for today only
 * when the day-judging targets actually change, upserted by date so same-day edits collapse to
 * one row. Pure JVM — [PlanPreferencesSource] is faked in-memory so no Context/DataStore is needed.
 */
class PlanRepositorySaveTest {

    private class FakePreferences(initial: PlanPreferences = PlanPreferences()) : PlanPreferencesSource {
        private val state = MutableStateFlow(initial)
        override val preferences: Flow<PlanPreferences> = state
        override suspend fun save(preferences: PlanPreferences) { state.value = preferences }
        override suspend fun resetDefaults() { state.value = PlanPreferences() }
    }

    /** Mirrors Room @Upsert by PK (effectiveFrom): a same-day write replaces that day's row. */
    private class FakeDao(initial: List<PlanVersionEntity> = emptyList()) : PlanVersionDao {
        val rows = initial.toMutableList()
        override fun observeAll(): Flow<List<PlanVersionEntity>> =
            kotlinx.coroutines.flow.flowOf(rows)
        override suspend fun getAll(): List<PlanVersionEntity> = rows
        override suspend fun count(): Int = rows.size
        override suspend fun upsert(version: PlanVersionEntity) {
            rows.removeAll { it.effectiveFrom == version.effectiveFrom }
            rows.add(version)
        }
        override suspend fun deleteAll() = rows.clear()
    }

    private class FixedDate(private val date: LocalDate) : DateProvider {
        override fun today(): LocalDate = date
    }

    private val today = LocalDate.parse("2026-06-30")

    private fun repo(
        prefs: FakePreferences = FakePreferences(),
        dao: FakeDao = FakeDao(),
        date: LocalDate = today,
    ) = Triple(prefs, dao, PlanRepository(prefs, dao, FixedDate(date)))

    @Test fun `changing a target records exactly one version effective today`() = runTest {
        val (prefs, dao, repo) = repo()

        repo.save(PlanPreferences().withCalorieTarget(2700))

        assertEquals(1, dao.rows.size)
        val row = dao.rows.first()
        assertEquals(today.toString(), row.effectiveFrom)
        assertEquals(2700, row.targetCalories)
        assertEquals(2600, row.calorieZoneLowerBound)
        assertEquals(2800, row.calorieZoneUpperBound)
        // The preferences themselves were persisted regardless.
        assertEquals(2700, prefs.preferences.first().targetCalories)
    }

    @Test fun `a non-target change records no version`() = runTest {
        val (_, dao, repo) = repo()

        repo.save(PlanPreferences(healthConnectEnabled = true))

        assertTrue("toggling a non-judging field must not create history", dao.rows.isEmpty())
    }

    @Test fun `two target changes on the same day upsert to a single row, final value wins`() = runTest {
        val (_, dao, repo) = repo()

        repo.save(PlanPreferences().withCalorieTarget(2700))
        repo.save(PlanPreferences().withCalorieTarget(2800))

        assertEquals(1, dao.rows.size)
        val row = dao.rows.first()
        assertEquals(today.toString(), row.effectiveFrom)
        assertEquals(2800, row.targetCalories)
    }

    @Test fun `resetDefaults records a default baseline version for today`() = runTest {
        val (_, dao, repo) = repo(prefs = FakePreferences(PlanPreferences().withCalorieTarget(3000)))

        repo.resetDefaults()

        assertEquals(1, dao.rows.size)
        val row = dao.rows.first()
        assertEquals(today.toString(), row.effectiveFrom)
        assertEquals(PlanPreferences().targetCalories, row.targetCalories)
        assertEquals(PlanPreferences().targetProteinG, row.targetProteinG)
    }
}
