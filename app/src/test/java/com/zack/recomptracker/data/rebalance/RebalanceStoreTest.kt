package com.zack.recomptracker.data.rebalance

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalanceSerialization
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Light I/O test for [DataStoreRebalanceStore]: save/current round-trip, [RebalanceState.HISTORY_CAP]
 * enforcement on [DataStoreRebalanceStore.save], state [Flow] emission, and last-evaluated tracking.
 * Backed by a fresh temp-dir DataStore per test (mirrors [CoachExperimentStoreTest]). Pure
 * (de)serialization is covered separately in [RebalanceSerializationTest].
 */
class RebalanceStoreTest {

    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: RebalanceStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("rebalance_store_test").toFile()
        dataStore = PreferenceDataStoreFactory.create { File(tempDir, "rebalance.preferences_pb") }
        store = DataStoreRebalanceStore(dataStore)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun terminalPlan(
        id: String,
        createdAtIso: String,
        status: RebalanceStatus = RebalanceStatus.COMPLETED,
    ) = RebalancePlan(
        id = id,
        triggerDateIso = "2026-06-01",
        startDateIso = "2026-06-03",
        endDateIso = "2026-06-05",
        lengthDays = 3,
        mode = RebalanceMode.BALANCED,
        baseCalories = 2200,
        dailyCalorieReduction = 150,
        extraDailySteps = 0,
        baseStepGoal = null,
        recentAvgSteps = null,
        surplusKcal = 600,
        recoveredKcal = 450,
        status = status,
        createdAtIso = createdAtIso,
        decidedAtIso = null,
        endedReason = "completed",
    )

    @Test
    fun `current is the empty default state initially`() = runTest {
        assertEquals(RebalanceState(), store.current())
        assertEquals(RebalanceState(), store.state.first())
    }

    @Test
    fun `save then current round-trips`() = runTest {
        val plan = terminalPlan(id = "p1", createdAtIso = "2026-06-10T08:00:00")
        val state = RebalanceState(active = null, history = listOf(plan), mode = RebalanceMode.EAT_LESS)
        store.save(state)
        assertEquals(state, store.current())
    }

    @Test
    fun `state flow emits the saved state`() = runTest {
        val plan = terminalPlan(id = "p1", createdAtIso = "2026-06-10T08:00:00")
        val state = RebalanceState(history = listOf(plan))
        store.save(state)
        assertEquals(state, store.state.first())
    }

    @Test
    fun `state flow emits again after a second save`() = runTest {
        val first = RebalanceState(mode = RebalanceMode.EAT_LESS)
        val second = RebalanceState(mode = RebalanceMode.MOVE_MORE)
        store.save(first)
        assertEquals(RebalanceMode.EAT_LESS, store.state.first().mode)
        store.save(second)
        assertEquals(RebalanceMode.MOVE_MORE, store.state.first().mode)
    }

    @Test
    fun `save with 13 terminal history records keeps only the 12 newest by createdAtIso`() = runTest {
        // 13 records with strictly increasing createdAtIso, in scrambled input order.
        val records = (1..13).map { n ->
            terminalPlan(id = "p$n", createdAtIso = "2026-06-%02dT08:00:00".format(n))
        }.shuffled(kotlin.random.Random(42))

        store.save(RebalanceState(history = records))

        val saved = store.current().history
        assertEquals(RebalanceState.HISTORY_CAP, saved.size)
        // The oldest record (p1, 2026-06-01) must have been dropped; the 12 newest (p2..p13) remain.
        val savedIds = saved.map { it.id }.toSet()
        assertEquals((2..13).map { "p$it" }.toSet(), savedIds)
        assertEquals(false, savedIds.contains("p1"))
    }

    @Test
    fun `save with exactly 12 terminal history records keeps all of them`() = runTest {
        val records = (1..12).map { n ->
            terminalPlan(id = "p$n", createdAtIso = "2026-06-%02dT08:00:00".format(n))
        }
        store.save(RebalanceState(history = records))
        assertEquals(12, store.current().history.size)
    }

    @Test
    fun `history cap keeps insertion order on identical createdAtIso ties`() = runTest {
        // The two oldest records share a createdAtIso. sortedBy is a stable sort, so tied records
        // keep their input order and the cap deterministically drops the one that came first.
        val tieFirst = terminalPlan(id = "tie-first", createdAtIso = "2026-06-01T08:00:00")
        val tieSecond = terminalPlan(id = "tie-second", createdAtIso = "2026-06-01T08:00:00")
        val newer = (3..13).map { n ->
            terminalPlan(id = "p$n", createdAtIso = "2026-06-%02dT08:00:00".format(n))
        }

        store.save(RebalanceState(history = listOf(tieFirst, tieSecond) + newer))

        val saved = store.current().history
        assertEquals(RebalanceState.HISTORY_CAP, saved.size)
        assertEquals(listOf("tie-second") + (3..13).map { "p$it" }, saved.map { it.id })
    }

    @Test
    fun `save preserves the active plan alongside a capped history`() = runTest {
        val active = terminalPlan(id = "active", createdAtIso = "2026-07-05T08:00:00", status = RebalanceStatus.ACTIVE)
        val records = (1..13).map { n ->
            terminalPlan(id = "p$n", createdAtIso = "2026-06-%02dT08:00:00".format(n))
        }
        store.save(RebalanceState(active = active, history = records))

        val saved = store.current()
        assertEquals(active, saved.active)
        assertEquals(RebalanceState.HISTORY_CAP, saved.history.size)
    }

    @Test
    fun `lastEvaluated is null initially`() = runTest {
        assertNull(store.lastEvaluated())
    }

    @Test
    fun `markEvaluated then lastEvaluated round-trips`() = runTest {
        val date = LocalDate.of(2026, 7, 5)
        store.markEvaluated(date)
        assertEquals(date, store.lastEvaluated())
    }

    @Test
    fun `markEvaluated overwrites a prior value`() = runTest {
        store.markEvaluated(LocalDate.of(2026, 7, 4))
        store.markEvaluated(LocalDate.of(2026, 7, 5))
        assertEquals(LocalDate.of(2026, 7, 5), store.lastEvaluated())
    }

    @Test
    fun `lastEvaluated returns null on a corrupt stored date`() = runTest {
        // Garbage written straight to the preference key must read back as null, never throw.
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("last_evaluated")] = "not-a-date"
        }
        assertNull(store.lastEvaluated())
    }
}
