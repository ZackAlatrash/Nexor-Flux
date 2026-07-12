package com.zack.recomptracker.ui.today

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.FakeDateProvider
import com.zack.recomptracker.data.rebalance.FakeRebalanceStore
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.toPlanTargets
import com.zack.recomptracker.domain.rebalance.RebalanceState
import java.time.LocalDate
import java.time.LocalTime
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
 * P1-10: the Food Log's "today" (isToday/isFuture/isPast labels, week strip, stale-plan window) must
 * follow the calendar day. A ViewModel that froze it at creation mislabeled planned meals and pinned
 * the week strip after midnight — these assert it advances when [FakeDateProvider] rolls over.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodLogViewModelReactiveTodayTest {

    private val dispatcher = StandardTestDispatcher()
    private val day1 = LocalDate.of(2026, 7, 11)
    private val day2 = LocalDate.of(2026, 7, 12)
    private val provider = FakeDateProvider(day1)

    private lateinit var logRepo: LogRepository
    private lateinit var planRepo: PlanRepository

    private fun emptyDayLog(date: LocalDate) =
        DayLog(date = date, dailyLog = null, meals = emptyList(), totals = MacroTotals())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        logRepo = mock()
        planRepo = mock()
        whenever(logRepo.observeDay(any())).thenReturn(flowOf(emptyDayLog(day1)))
        whenever(logRepo.observeSlots()).thenReturn(flowOf(emptyList()))
        whenever(logRepo.observeWeekCalories(any(), any())).thenReturn(flowOf(emptyMap()))
        whenever(logRepo.observeStalePlannedCount(any(), any())).thenReturn(flowOf(0))
        whenever(logRepo.observeSavedFoods()).thenReturn(flowOf(emptyList()))
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))
        whenever(planRepo.observeVersions()).thenReturn(flowOf(emptyList()))
        whenever(planRepo.observePlanOn(any())).thenReturn(flowOf(PlanPreferences().toPlanTargets()))
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm() = FoodLogViewModel(
        logRepo,
        planRepo,
        FakeRebalanceStore(RebalanceState()),
        provider,
        computeDispatcher = dispatcher,
    ) { LocalTime.of(15, 0) }

    @Test
    fun `state today and the week window advance when the day rolls`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()
        assertEquals(day1, vm.uiState.value.today)
        verify(logRepo).observeWeekCalories(day1.minusDays(6), day1)

        provider.advanceTo(day2)
        advanceUntilIdle()
        assertEquals(day2, vm.uiState.value.today)
        verify(logRepo).observeWeekCalories(day2.minusDays(6), day2)
    }
}
