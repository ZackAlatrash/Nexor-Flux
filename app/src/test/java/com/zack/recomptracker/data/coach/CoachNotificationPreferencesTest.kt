package com.zack.recomptracker.data.coach

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.nio.file.Files
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the Phase-5 push-preference defaults ("quiet by default", §11) and round-tripping of the
 * three settings through a fresh temp-dir DataStore per test.
 */
class CoachNotificationPreferencesTest {

    private lateinit var tempDir: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: CoachNotificationPreferences

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("coach_notif_test").toFile()
        dataStore = PreferenceDataStoreFactory.create { File(tempDir, "notif.preferences_pb") }
        prefs = CoachNotificationPreferences(dataStore)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `weekly check-in push defaults on`() = runTest {
        assertTrue(prefs.weeklyCheckInPushEnabled.first())
    }

    @Test
    fun `ambient nudges default off`() = runTest {
        assertFalse(prefs.ambientNudgesEnabled.first())
    }

    @Test
    fun `quiet hours default to 22 to 7`() = runTest {
        val quiet = prefs.quietHours()
        assertEquals(LocalTime(22, 0), quiet.start)
        assertEquals(LocalTime(7, 0), quiet.end)
    }

    @Test
    fun `default quiet window suppresses late-night and allows mid-day`() = runTest {
        val quiet = prefs.quietHours()
        assertTrue("23:30 is quiet", quiet.contains(LocalTime(23, 30)))
        assertTrue("06:00 is quiet", quiet.contains(LocalTime(6, 0)))
        assertFalse("13:00 is allowed", quiet.contains(LocalTime(13, 0)))
    }

    @Test
    fun `setters round-trip`() = runTest {
        prefs.setWeeklyCheckInPushEnabled(false)
        prefs.setAmbientNudgesEnabled(true)
        prefs.setQuietHours(startHour = 21, endHour = 8)

        assertFalse(prefs.weeklyCheckInPushEnabled.first())
        assertTrue(prefs.ambientNudgesEnabled.first())
        assertEquals(LocalTime(21, 0), prefs.quietHours().start)
        assertEquals(LocalTime(8, 0), prefs.quietHours().end)
    }
}
