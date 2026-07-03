package com.zack.recomptracker.data.coach

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.domain.coach.PushEvent
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Light I/O test for the thin DataStore wiring in [PushHistoryStore], backed by a fresh temp-dir
 * DataStore per test (no shared state — mirrors [CoachJourneyStoreTest]). The value logic is covered
 * exhaustively (pure) in [PushHistorySerializationTest]; here we only confirm persistence round-trips
 * and that reads prune the stale tail.
 */
class PushHistoryStoreTest {

    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: PushHistoryStore
    private val today = LocalDate.of(2026, 7, 1)

    private class FixedDateProvider(var day: LocalDate) : DateProvider {
        override fun today(): LocalDate = day
    }

    private lateinit var dateProvider: FixedDateProvider

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("push_history_test").toFile()
        dataStore = PreferenceDataStoreFactory.create { File(tempDir, "push.preferences_pb") }
        dateProvider = FixedDateProvider(today)
        store = PushHistoryStore(dataStore, dateProvider)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `recent pushes is empty initially`() = runTest {
        assertEquals(emptyList<PushEvent>(), store.recentPushes())
    }

    @Test
    fun `record persists and round-trips`() = runTest {
        val event = PushEvent(timestamp = LocalDateTime.of(2026, 7, 1, 13, 0), isCelebration = true)
        store.record(event)
        assertEquals(listOf(event), store.recentPushes())
    }

    @Test
    fun `two records both persist`() = runTest {
        val a = PushEvent(timestamp = LocalDateTime.of(2026, 6, 28, 9, 0), isCelebration = false)
        val b = PushEvent(timestamp = LocalDateTime.of(2026, 7, 1, 13, 0), isCelebration = false)
        store.record(a)
        store.record(b)
        assertEquals(listOf(a, b), store.recentPushes())
    }

    @Test
    fun `reads prune events older than the retention window`() = runTest {
        // "today" is 2026-07-01; an event from well before the retention horizon must be dropped.
        val stale = PushEvent(
            timestamp = today.minusDays(PushHistorySerialization.RETENTION_DAYS + 5).atTime(13, 0),
            isCelebration = false,
        )
        val fresh = PushEvent(timestamp = today.atTime(13, 0), isCelebration = false)
        store.record(stale)
        store.record(fresh)

        val recent = store.recentPushes()
        assertTrue(recent.contains(fresh))
        assertFalse(recent.contains(stale))
    }
}
