package com.zack.recomptracker.data.coach

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Light I/O test for [CoachExperimentStore]: single active experiment persists, [start] overwrites,
 * [clear] removes. Backed by a fresh temp-dir DataStore per test (mirrors the other data/coach store
 * tests). Pure (de)serialization is covered separately in [CoachExperimentSerializationTest].
 */
class CoachExperimentStoreTest {

    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: CoachExperimentStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("coach_experiment_test").toFile()
        dataStore = PreferenceDataStoreFactory.create { File(tempDir, "experiment.preferences_pb") }
        store = CoachExperimentStore(dataStore)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun experiment(
        correlationId: String = "PROTEIN_HIT_NEXT_DAY_HUNGER",
        baseline: Double = 4.5,
    ) = CoachExperiment(
        correlationId = correlationId,
        hypothesis = "Hitting protein may curb next-day cravings.",
        trackedMetric = "hunger",
        baselineValue = baseline,
        startDateIso = "2026-07-01",
    )

    @Test
    fun `active is null initially`() = runTest {
        assertNull(store.current())
        assertNull(store.active.first())
    }

    @Test
    fun `start persists and round-trips`() = runTest {
        store.start(experiment())
        val active = store.current()!!
        assertEquals("PROTEIN_HIT_NEXT_DAY_HUNGER", active.correlationId)
        assertEquals("hunger", active.trackedMetric)
        assertEquals(4.5, active.baselineValue, 0.0)
        assertEquals("2026-07-01", active.startDateIso)
        assertEquals(active, store.active.first())
    }

    @Test
    fun `start overwrites any existing active experiment (single active)`() = runTest {
        store.start(experiment(correlationId = "SLEEP_NEXT_DAY_ENERGY", baseline = 6.0))
        store.start(experiment(correlationId = "WEEKEND_CALORIE_SURPLUS", baseline = 2800.0))
        val active = store.current()!!
        assertEquals("WEEKEND_CALORIE_SURPLUS", active.correlationId)
        assertEquals(2800.0, active.baselineValue, 0.0)
    }

    @Test
    fun `clear removes the active experiment`() = runTest {
        store.start(experiment())
        store.clear()
        assertNull(store.current())
        assertNull(store.active.first())
    }
}
