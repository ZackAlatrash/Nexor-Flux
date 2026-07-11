package com.zack.recomptracker.ui.dashboard

import com.zack.recomptracker.ai.StubInsightCoordinator
import com.zack.recomptracker.core.time.FakeDateProvider
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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * P1-10: the Dashboard aggregates and its 28-day meal-query window must follow the calendar day.
 * A ViewModel that froze "today"/"windowStart" at creation kept aggregating the opening day after
 * midnight — these assert both advance when [FakeDateProvider] rolls over.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelReactiveTodayTest {

    private val dispatcher = StandardTestDispatcher()
    private val day1 = LocalDate.of(2026, 7, 11)
    private val day2 = LocalDate.of(2026, 7, 12)
    private val provider = FakeDateProvider(day1)

    private lateinit var logRepo: LogRepository
    private lateinit var planRepo: PlanRepository
    private lateinit var profileStore: UserProfilePreferencesStore

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

    private fun buildVm() = DashboardViewModel(
        logRepository = logRepo,
        planRepository = planRepo,
        dateProvider = provider,
        trendCalculator = TrendCalculator(),
        adherenceCalculator = AdherenceCalculator(),
        adjustmentEngine = AdjustmentEngine(),
        aiInsightCoordinator = StubInsightCoordinator(flowOf(false), scope = CoroutineScope(dispatcher)),
        userProfileStore = profileStore,
        rebalanceStore = FakeRebalanceStore(RebalanceState()),
        computeDispatcher = dispatcher,
    )

    @Test
    fun `meal window and state today advance when the day rolls`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()
        verify(logRepo).observeMealEntriesSince(day1.minusDays(27))
        assertEquals(day1, vm.uiState.value.today)

        provider.advanceTo(day2)
        advanceUntilIdle()
        verify(logRepo).observeMealEntriesSince(day2.minusDays(27))
        assertEquals(day2, vm.uiState.value.today)
    }
}
