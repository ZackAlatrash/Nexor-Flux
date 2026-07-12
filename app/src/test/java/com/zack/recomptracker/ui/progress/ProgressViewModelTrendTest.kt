package com.zack.recomptracker.ui.progress

import com.zack.recomptracker.ai.StubInsightCoordinator
import com.zack.recomptracker.core.time.FakeDateProvider
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.rebalance.FakeRebalanceStore
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.plan.PlanHistory
import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.trend.TrendCalculator
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * P1-12: the Trends screen must compute weight/waist trends by ELAPSED DAYS (date-based regression,
 * the same TrendCalculator the Dashboard uses), not by point count. Two weigh-ins 27 days apart used
 * to be treated as 1/7 of a week apart, inflating the per-week slope ~7×.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTrendTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 7, 11)
    private val provider = FakeDateProvider(today)

    private lateinit var logRepo: LogRepository
    private lateinit var planRepo: PlanRepository
    private lateinit var profileStore: UserProfilePreferencesStore
    private lateinit var sessionRepo: WorkoutSessionRepository
    private lateinit var exerciseRepo: ExerciseLibraryRepository

    private val versions = listOf(
        PlanVersion(PlanHistory.BASELINE_DATE, PlanTargets(2550, 165, 300, 70, 2400, 2600)),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        logRepo = mock()
        planRepo = mock()
        profileStore = mock()
        sessionRepo = mock()
        exerciseRepo = mock()
        // Two weigh-ins 27 days apart within the 28-day window: 80.0 kg → 79.0 kg.
        whenever(logRepo.observeDailyLogs()).thenReturn(
            flowOf(
                listOf(
                    DailyLogEntity(date = today.minusDays(27).toString(), bodyWeightKg = 80.0),
                    DailyLogEntity(date = today.toString(), bodyWeightKg = 79.0),
                ),
            ),
        )
        whenever(logRepo.observeMealEntries()).thenReturn(flowOf(emptyList()))
        whenever(logRepo.observePerformances()).thenReturn(flowOf(emptyList()))
        whenever(planRepo.observeVersions()).thenReturn(flowOf(versions))
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))
        whenever(profileStore.preferences).thenReturn(flowOf(UserProfilePreferences()))
        whenever(sessionRepo.observeCompletedSessions()).thenReturn(flowOf(emptyList()))
        whenever(exerciseRepo.observeAll()).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm() = ProgressViewModel(
        logRepository = logRepo,
        planRepository = planRepo,
        dateProvider = provider,
        adherenceCalculator = AdherenceCalculator(),
        aiInsightCoordinator = StubInsightCoordinator(flowOf(false), scope = CoroutineScope(dispatcher)),
        userProfileStore = profileStore,
        workoutSessionRepository = sessionRepo,
        exerciseLibraryRepository = exerciseRepo,
        rebalanceStore = FakeRebalanceStore(RebalanceState()),
        trendCalculator = TrendCalculator(),
        computeDispatcher = dispatcher,
    )

    @Test
    fun `weight trend is elapsed-day based, not inflated by point count`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        // Correct date-based slope: (79 - 80) / 27 days * 7 = -0.259 kg/wk.
        // The old point-count math gave (79 - 80) / ((2-1)/7) = -7.0 kg/wk.
        val trend = vm.uiState.value.insightContext?.weightTrendKgPerWeek
        assertEquals(-0.259, trend!!, 0.01)
        assertTrue("trend must not be inflated ~7x", kotlin.math.abs(trend) < 1.0)

        // The chart label reflects the same corrected value: down arrow, and NOT the inflated ~7.
        val label = vm.uiState.value.weight.trendLabel
        assertTrue("label was $label", label.contains("↓"))
        assertFalse("label must not show the inflated ~7: $label", label.contains("7"))
    }
}
