package com.zack.recomptracker.ui.today

import com.zack.recomptracker.ai.StubInsightCoordinator
import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.MealEntryEntity
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
import org.mockito.kotlin.verify
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
        whenever(logRepo.observeStalePlannedCount(any(), any())).thenReturn(flowOf(0))
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
    fun `selectDate allows future dates for planning`() = runTest {
        val vm = buildVm()
        val tomorrow = today.plusDays(1)
        whenever(logRepo.observeDay(tomorrow)).thenReturn(flowOf(emptyDayLog(tomorrow)))
        advanceUntilIdle()

        vm.selectDate(tomorrow)
        advanceUntilIdle()

        assertEquals(tomorrow, vm.uiState.value.selectedDate)
        assertTrue(vm.uiState.value.isFuture)
    }

    @Test
    fun `selectDate clamps to 30 days ahead`() = runTest {
        val vm = buildVm()
        val maxFuture = today.plusDays(30)
        whenever(logRepo.observeDay(maxFuture)).thenReturn(flowOf(emptyDayLog(maxFuture)))
        advanceUntilIdle()

        vm.selectDate(today.plusDays(40))
        advanceUntilIdle()

        assertEquals(maxFuture, vm.uiState.value.selectedDate)
    }

    @Test
    fun `selectDate clamps to 30 days back when older date given`() = runTest {
        val vm = buildVm()
        val maxPast = today.minusDays(30)
        whenever(logRepo.observeDay(maxPast)).thenReturn(flowOf(emptyDayLog(maxPast)))
        advanceUntilIdle()

        vm.selectDate(today.minusDays(40))
        advanceUntilIdle()

        assertEquals(maxPast, vm.uiState.value.selectedDate)
    }

    @Test
    fun `plannedTotals and hasPlannedEntries reflect the day log`() = runTest {
        val plannedMeal = MealEntryEntity(
            date = today.toString(), mealType = "x", name = "y",
            calories = 300, proteinG = 0.0, carbsG = 0.0, fatG = 0.0, planned = true,
        )
        val day = DayLog(
            date = today, dailyLog = null, meals = listOf(plannedMeal),
            totals = MacroTotals(), plannedTotals = MacroTotals(calories = 300),
        )
        whenever(logRepo.observeDay(today)).thenReturn(flowOf(day))
        val vm = buildVm()
        advanceUntilIdle()

        assertEquals(300, vm.uiState.value.plannedTotals.calories)
        assertTrue(vm.uiState.value.hasPlannedEntries)
    }

    @Test
    fun `confirmMeal delegates to repository`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        vm.confirmMeal(7L)
        advanceUntilIdle()

        verify(logRepo).setMealPlanned(7L, planned = false)
    }

    @Test
    fun `postponeMeal moves entry to next day as a plan`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        vm.postponeMeal(5L)
        advanceUntilIdle()

        // selected day is today, so next day is in the future → planned
        verify(logRepo).moveMealToDate(5L, today.plusDays(1), planned = true)
    }

    @Test
    fun `confirmAllPlanned delegates for the selected date`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        vm.confirmAllPlanned()
        advanceUntilIdle()

        verify(logRepo).confirmPlannedForDate(today)
    }
}
