package com.zack.recomptracker.ui.today

import com.zack.recomptracker.ai.StubInsightCoordinator
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class FoodLogViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 6, 4)
    private lateinit var logRepo: LogRepository
    private lateinit var planRepo: PlanRepository

    private val dateProvider = object : DateProvider {
        override fun today() = today
    }

    private fun emptyDayLog(date: LocalDate) = DayLog(
        date = date, dailyLog = null, meals = emptyList(), totals = MacroTotals()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        logRepo = mock()
        planRepo = mock()
        whenever(logRepo.observeDay(any())).thenReturn(flowOf(emptyDayLog(today)))
        whenever(logRepo.observeSlots()).thenReturn(flowOf(emptyList()))
        whenever(logRepo.observeWeekCalories(any(), any())).thenReturn(flowOf(emptyMap()))
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private val aiCoordinator = StubInsightCoordinator(MutableStateFlow(false), CoroutineScope(dispatcher))

    private fun buildVm() = FoodLogViewModel(logRepo, planRepo, dateProvider, aiCoordinator)

    @Test
    fun `initial selectedDate is today`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()
        assertEquals(today, vm.uiState.value.selectedDate)
    }

    @Test
    fun `initial isToday is true`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isToday)
    }

    @Test
    fun `selectDate updates selectedDate`() = runTest {
        val vm = buildVm()
        val yesterday = today.minusDays(1)
        whenever(logRepo.observeDay(yesterday)).thenReturn(flowOf(emptyDayLog(yesterday)))
        advanceUntilIdle()

        vm.selectDate(yesterday)
        advanceUntilIdle()

        assertEquals(yesterday, vm.uiState.value.selectedDate)
        assertFalse(vm.uiState.value.isToday)
    }

    @Test
    fun `selectDate clamps to today when future date given`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        vm.selectDate(today.plusDays(1))
        advanceUntilIdle()

        assertEquals(today, vm.uiState.value.selectedDate)
    }

    @Test
    fun `selectDate clamps to today minus 6 when older date given`() = runTest {
        val vm = buildVm()
        val sixDaysAgo = today.minusDays(6)
        whenever(logRepo.observeDay(sixDaysAgo)).thenReturn(flowOf(emptyDayLog(sixDaysAgo)))
        advanceUntilIdle()

        vm.selectDate(today.minusDays(10))
        advanceUntilIdle()

        assertEquals(sixDaysAgo, vm.uiState.value.selectedDate)
    }
}
