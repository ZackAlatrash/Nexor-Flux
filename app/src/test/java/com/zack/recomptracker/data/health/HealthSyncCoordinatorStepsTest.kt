package com.zack.recomptracker.data.health

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.PlanPreferencesSource
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real-Room test of the steps-only refresh finalizing *yesterday* after midnight (review P1-1).
 * Every recurring sync path used to read today only, so a day's total froze at the last sync before
 * midnight and was never reconciled — the post-sync evening tail was lost. The fake models Health
 * Connect's real trailing-window read (the last `days` local days ending today), so a `days = 1`
 * request omits yesterday entirely and a `days = 2` request finalizes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class HealthSyncCoordinatorStepsTest {

    private val today = LocalDate.of(2026, 7, 10)
    private val yesterday = today.minusDays(1)

    private class FakeHealthDataSource(private val today: LocalDate) : HealthDataSource {
        var lastDaysRequested: Long = -1
        override suspend fun hasPermissions(): Boolean = true
        override suspend fun readToday(date: LocalDate): HealthConnectReadResult = HealthConnectReadResult()
        override suspend fun readStepsHistory(days: Long): Map<LocalDate, Int> {
            lastDaysRequested = days
            // The last `days` local days ending today; each day a distinct, recognizable total.
            return (0 until days).associate { offset ->
                today.minusDays(offset) to (10_000 - offset.toInt() * 1_000)
            }
        }
    }

    private class FakePreferences : PlanPreferencesSource {
        private val state = MutableStateFlow(PlanPreferences())
        override val preferences: Flow<PlanPreferences> = state
        override suspend fun save(preferences: PlanPreferences) { state.value = preferences }
        override suspend fun resetDefaults() { state.value = PlanPreferences() }
    }

    private class FixedDateProvider(private val date: LocalDate) : DateProvider {
        override fun today(): LocalDate = date
    }

    private lateinit var database: RecompDatabase
    private lateinit var hc: FakeHealthDataSource
    private lateinit var coordinator: HealthSyncCoordinator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RecompDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val logRepository = LogRepository(
            dailyLogDao = database.dailyLogDao(),
            mealEntryDao = database.mealEntryDao(),
            savedFoodDao = database.savedFoodDao(),
            savedMealDao = database.savedMealDao(),
            performanceDao = database.performanceDao(),
            weeklyReviewDao = database.weeklyReviewDao(),
            mealSlotDao = database.mealSlotDao(),
        )
        hc = FakeHealthDataSource(today)
        coordinator = HealthSyncCoordinator(
            hcRepository = hc,
            logRepository = logRepository,
            planRepository = PlanRepository(
                appPreferences = FakePreferences(),
                planVersionDao = database.planVersionDao(),
                dateProvider = FixedDateProvider(today),
            ),
            dateProvider = FixedDateProvider(today),
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `steps sync finalizes yesterday, not just today`() = runTest {
        coordinator.syncTodaySteps()

        val yesterdayLog = database.dailyLogDao().getByDate(yesterday.toString())
        assertNotNull("yesterday's log is written so its total is finalized after midnight", yesterdayLog)
        assertEquals("yesterday's finalized step total", 9_000, yesterdayLog!!.steps)

        val todayLog = database.dailyLogDao().getByDate(today.toString())
        assertEquals("today's step total is still applied", 10_000, todayLog?.steps)

        assertEquals("reads a 2-day window so the post-midnight sync closes yesterday", 2L, hc.lastDaysRequested)
    }

    @Test
    fun `full sync also finalizes yesterday's steps (worker-only path)`() = runTest {
        // A user who enables Health Connect but never foregrounds the app only ever hits the
        // background worker → syncToday. That path must finalize yesterday too, or the worker's
        // pre-midnight total stays frozen forever (the same P1-1 symptom as the foreground path).
        coordinator.syncToday()

        val yesterdayLog = database.dailyLogDao().getByDate(yesterday.toString())
        assertNotNull("full sync finalizes yesterday, not just today", yesterdayLog)
        assertEquals("yesterday's finalized step total", 9_000, yesterdayLog!!.steps)
    }
}
