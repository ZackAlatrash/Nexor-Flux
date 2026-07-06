package com.zack.recomptracker.ui.dashboard

import com.zack.recomptracker.ai.StubInsightCoordinator
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.rebalance.FakeRebalanceStore
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.adjustment.AdjustmentEngine
import com.zack.recomptracker.domain.plan.PlanHistory
import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import com.zack.recomptracker.domain.trend.TrendCalculator
import java.time.LocalDate
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The Dashboard's today ring + "Rebalance · Day X of Y" data reflect the EFFECTIVE (rebalance-reduced)
 * target on an active plan day, and are behaviour-neutral with an empty rebalance state (spec §6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelRebalanceTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 6, 30)
    private lateinit var logRepo: LogRepository
    private lateinit var planRepo: PlanRepository
    private lateinit var profileStore: UserProfilePreferencesStore

    private val dateProvider = object : DateProvider {
        override fun today(): LocalDate = today
    }

    // Baseline plan version covering all test dates: 2550 kcal, zone 2400..2600.
    private val versions = listOf(
        PlanVersion(PlanHistory.BASELINE_DATE, PlanTargets(2550, 165, 300, 70, 2400, 2600)),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        logRepo = mock()
        planRepo = mock()
        profileStore = mock()
        whenever(logRepo.observeDailyLogs()).thenReturn(flowOf(emptyList()))
        whenever(logRepo.observeMealEntriesSince(any())).thenReturn(flowOf(emptyList()))
        whenever(logRepo.observePerformances()).thenReturn(flowOf(emptyList()))
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))
        whenever(planRepo.observeVersions()).thenReturn(flowOf(versions))
        whenever(profileStore.preferences).thenReturn(flowOf(UserProfilePreferences()))
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm(state: RebalanceState) = DashboardViewModel(
        logRepository = logRepo,
        planRepository = planRepo,
        dateProvider = dateProvider,
        trendCalculator = TrendCalculator(),
        adherenceCalculator = AdherenceCalculator(),
        adjustmentEngine = AdjustmentEngine(),
        aiInsightCoordinator = StubInsightCoordinator(flowOf(false), scope = backgroundScopeStub()),
        userProfileStore = profileStore,
        rebalanceStore = FakeRebalanceStore(state),
        computeDispatcher = dispatcher,
    )

    // The stub coordinator only needs a scope to collect its enabled flow; the test dispatcher's
    // scope is fine since we never assert on insight state here.
    private fun backgroundScopeStub() = kotlinx.coroutines.CoroutineScope(dispatcher)

    private fun activePlanCoveringToday(r: Int) = RebalancePlan(
        id = "p",
        triggerDateIso = today.minusDays(2).toString(),
        startDateIso = today.minusDays(1).toString(), // day 1 = yesterday
        endDateIso = today.plusDays(1).toString(),    // 3-day window; today = day 2
        lengthDays = 3,
        mode = RebalanceMode.EAT_LESS,
        baseCalories = 2550,
        dailyCalorieReduction = r,
        extraDailySteps = 0,
        baseStepGoal = null,
        recentAvgSteps = null,
        surplusKcal = 600,
        recoveredKcal = 600,
        status = RebalanceStatus.ACTIVE,
        createdAtIso = "2026-06-28T09:00:00Z",
    )

    @Test
    fun `ring target reflects reduced calories on an active plan day`() = runTest {
        val vm = buildVm(RebalanceState(active = activePlanCoveringToday(r = 250)))
        advanceUntilIdle()

        val prefs = vm.uiState.value.preferences
        // Effective today = 2550 − 250 = 2300, zone re-centred to 2200..2400.
        assertEquals(2300, prefs.targetCalories)
        assertEquals(2200, prefs.calorieZoneLowerBound)
        assertEquals(2400, prefs.calorieZoneUpperBound)
        // Day-X info populated: today is day 2 of 3.
        val chip = vm.uiState.value.rebalanceToday
        assertNotNull(chip)
        assertEquals(2, chip!!.dayX)
        assertEquals(3, chip.ofY)
    }

    @Test
    fun `empty rebalance state leaves the ring target at base and chip null`() = runTest {
        val vm = buildVm(RebalanceState())
        advanceUntilIdle()

        val prefs = vm.uiState.value.preferences
        assertEquals(2550, prefs.targetCalories)
        assertEquals(2400, prefs.calorieZoneLowerBound)
        assertEquals(2600, prefs.calorieZoneUpperBound)
        assertNull(vm.uiState.value.rebalanceToday)
    }
}
