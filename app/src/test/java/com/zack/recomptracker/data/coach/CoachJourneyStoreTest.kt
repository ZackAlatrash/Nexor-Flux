package com.zack.recomptracker.data.coach

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.domain.coach.CoachAction
import com.zack.recomptracker.domain.coach.FiredSignalRecord
import com.zack.recomptracker.domain.coach.CoachActionType
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.CoachSurface
import com.zack.recomptracker.domain.coach.Confidence
import com.zack.recomptracker.domain.coach.SignalCategory
import com.zack.recomptracker.domain.coach.SignalFacts
import com.zack.recomptracker.domain.coach.SignalKind
import com.zack.recomptracker.domain.coach.SignalRationale
import com.zack.recomptracker.domain.coach.SignalTier
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Light I/O test: the thin DataStore wiring in [CoachJourneyStore], backed by a fresh temp-dir
 * DataStore per test (no shared state). The value logic is covered exhaustively (pure) in
 * [CoachJourneySerializationTest]; here we only confirm persistence survives a round-trip. Mirrors
 * the temp-DataStore setup used across `data/coach` tests.
 */
class CoachJourneyStoreTest {

    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: CoachJourneyStore
    private val today = LocalDate.of(2026, 7, 1)

    private class FixedDateProvider(var day: LocalDate) : DateProvider {
        override fun today(): LocalDate = day
    }

    private lateinit var dateProvider: FixedDateProvider

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("coach_journey_test").toFile()
        dataStore = PreferenceDataStoreFactory.create { File(tempDir, "journey.preferences_pb") }
        dateProvider = FixedDateProvider(today)
        store = CoachJourneyStore(dataStore, dateProvider)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun signal(
        kind: SignalKind = SignalKind.TRAINING_PLATEAU,
        dedupKey: String = "TRAINING_PLATEAU|bench",
    ) = CoachSignal(
        kind = kind,
        tier = SignalTier.P2,
        category = SignalCategory.TRAINING,
        severity = 40,
        facts = SignalFacts(mapOf("e1rmTrend" to "flat")),
        verdict = "Bench has stalled.",
        action = CoachAction(CoachActionType.OPEN_TRAINING, "See lift"),
        rationale = SignalRationale(confidence = Confidence.MEDIUM),
        dedupKey = dedupKey,
        surface = CoachSurface.WEEKLY,
        fallbackText = "Bench e1RM flat for 3 weeks.",
    )

    @Test
    fun `fired history is empty initially`() = runTest {
        assertEquals(emptyList<FiredSignalRecord>(), store.firedHistory())
    }

    @Test
    fun `recordFiredSignal persists and round-trips`() = runTest {
        store.recordFiredSignal(signal(), weekSignature = "2026-W27|HOLD")
        val history = store.firedHistory()
        assertEquals(1, history.size)
        val record = history.single()
        assertEquals(SignalKind.TRAINING_PLATEAU, record.kind)
        assertEquals("TRAINING_PLATEAU|bench", record.dedupKey)
        assertEquals("2026-W27|HOLD", record.weekSignature)
        assertEquals(today.toString(), record.dateIso)
    }

    @Test
    fun `recordWeeklyVerdict persists and is idempotent per week signature`() = runTest {
        store.recordWeeklyVerdict("2026-W27|HOLD", "2026-07-05", "Hold calories.")
        store.recordWeeklyVerdict("2026-W27|HOLD", "2026-07-05", "Hold calories.")
        assertEquals(1, store.weeklyVerdicts().size)
    }

    @Test
    fun `hasRecurred reflects fired-then-gap-then-fired across recorded weeks`() = runTest {
        dateProvider.day = LocalDate.of(2026, 6, 9)
        store.recordFiredSignal(signal(), "2026-W24|x")
        dateProvider.day = LocalDate.of(2026, 6, 16)
        store.recordFiredSignal(signal(kind = SignalKind.LOW_ADHERENCE, dedupKey = "LOW_ADHERENCE|x"), "2026-W25|x")
        dateProvider.day = LocalDate.of(2026, 6, 23)
        store.recordFiredSignal(signal(), "2026-W26|x")

        assertTrue(store.hasRecurred(SignalKind.TRAINING_PLATEAU))
        assertFalse(store.hasRecurred(SignalKind.LOW_ADHERENCE))
    }

    @Test
    fun `journeyNarrative is empty below threshold and populated above it`() = runTest {
        assertEquals("", store.journeyNarrative())

        store.recordWeeklyVerdict("2026-W25|x", "2026-06-21", "Hold calories.")
        store.recordWeeklyVerdict("2026-W26|x", "2026-06-28", "Cut 100 kcal.")
        store.recordWeeklyVerdict("2026-W27|x", "2026-07-05", "Hold calories.")

        assertTrue(store.journeyNarrative().isNotBlank())
    }
}
