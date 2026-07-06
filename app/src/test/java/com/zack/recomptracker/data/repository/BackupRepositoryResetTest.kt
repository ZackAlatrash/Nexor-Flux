package com.zack.recomptracker.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.PlanPreferencesSource
import com.zack.recomptracker.data.rebalance.FakeRebalanceStore
import com.zack.recomptracker.domain.rebalance.RebalanceIntensity
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Light Robolectric I/O test of [BackupRepository.resetEverything]'s rebalance-state clearing. A
 * full reset only *conditionally* writes a plan-version row, so the rebalance coordinator's
 * cancel-on-plan-edit hook is not guaranteed to fire — the reset itself must clear any active plan,
 * history, and sticky mode, or a live rebalance would keep running against the freshly reset plan.
 *
 * Runs the REAL reset path (in-memory Room database + real [PlanRepository] over an in-memory
 * [PlanPreferencesSource]) against the shared [FakeRebalanceStore]. Mirrors
 * [com.zack.recomptracker.data.coach.CoachInboxRepositoryTest]'s Robolectric shape — stub
 * [Application] because the real one initialises the Android keystore, which isn't available under
 * Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class BackupRepositoryResetTest {

    /** In-memory [PlanPreferencesSource] (the [PlanRepositorySaveTest] pattern) — no DataStore I/O. */
    private class FakePreferences(initial: PlanPreferences = PlanPreferences()) : PlanPreferencesSource {
        private val state = MutableStateFlow(initial)
        override val preferences: Flow<PlanPreferences> = state
        override suspend fun save(preferences: PlanPreferences) { state.value = preferences }
        override suspend fun resetDefaults() { state.value = PlanPreferences() }
    }

    private class FixedDateProvider(private val date: LocalDate) : DateProvider {
        override fun today(): LocalDate = date
    }

    private lateinit var database: RecompDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RecompDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun plan(id: String, status: RebalanceStatus) = RebalancePlan(
        id = id, triggerDateIso = "2026-07-01", startDateIso = "2026-07-06",
        endDateIso = "2026-07-10", lengthDays = 5, mode = RebalanceMode.EAT_LESS,
        baseCalories = 2500, dailyCalorieReduction = 200, extraDailySteps = 0,
        baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 1000,
        status = status, createdAtIso = "2026-07-05T09:00:00Z",
        decidedAtIso = "2026-07-05T09:00:00Z",
        // Non-default intensity/partial so this proves the reset clears them too, not just leaves a
        // stale non-default plan object behind under a passing top-level equality check.
        intensity = RebalanceIntensity.FULL, partial = true,
    )

    @Test
    fun `reset everything clears rebalance state`() = runTest {
        val store = FakeRebalanceStore(
            seed = RebalanceState(
                active = plan("active", RebalanceStatus.ACTIVE),
                history = listOf(plan("declined", RebalanceStatus.DECLINED)),
                mode = RebalanceMode.MOVE_MORE,
            ),
        )
        val repository = BackupRepository(
            database = database,
            planRepository = PlanRepository(
                appPreferences = FakePreferences(),
                planVersionDao = database.planVersionDao(),
                dateProvider = FixedDateProvider(LocalDate.of(2026, 7, 8)),
            ),
            rebalanceStore = store,
        )

        repository.resetEverything()

        assertEquals(
            "active plan, history, and sticky mode all reset to the empty default",
            RebalanceState(),
            store.current(),
        )
    }
}
