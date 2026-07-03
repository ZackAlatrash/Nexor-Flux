package com.zack.recomptracker.ui.today

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.toPlanTargets
import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.domain.plan.PlanHistory
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
        val chicken = SavedFoodEntity(
            name = "Chicken", servingName = "serving", calories = 165,
            proteinG = 31.0, carbsG = 0.0, fatG = 4.0, householdServingGrams = 100.0,
        )
        whenever(logRepo.observeSavedFoods()).thenReturn(flowOf(listOf(chicken)))
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))
        // No plan history by default → every day resolves to the current preferences.
        whenever(planRepo.observeVersions()).thenReturn(flowOf(emptyList()))
        whenever(planRepo.observePlanOn(any())).thenReturn(flowOf(PlanPreferences().toPlanTargets()))
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm(now: java.time.LocalTime = java.time.LocalTime.of(15, 0)) =
        FoodLogViewModel(logRepo, planRepo, dateProvider, computeDispatcher = dispatcher) { now }

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
    fun `past day target reflects the plan in effect on that day, not current prefs`() = runTest {
        val yesterday = today.minusDays(1)
        // Current prefs differ from the historical plan that was in effect yesterday.
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences(targetCalories = 3000)))
        val historicalPlan = PlanTargets(
            calories = 2000, proteinG = 150, carbsG = 200, fatG = 60,
            zoneLowerBound = 1900, zoneUpperBound = 2100,
        )
        whenever(planRepo.observePlanOn(yesterday)).thenReturn(flowOf(historicalPlan))
        whenever(logRepo.observeDay(yesterday)).thenReturn(flowOf(emptyDayLog(yesterday)))

        val vm = buildVm()
        advanceUntilIdle()
        vm.selectDate(yesterday)
        advanceUntilIdle()

        val target = vm.uiState.value.target
        assertEquals(2000, target.targetCalories)
        assertEquals(150, target.targetProteinG)
        assertEquals(1900, target.calorieZoneLowerBound)
        assertEquals(2100, target.calorieZoneUpperBound)
    }

    @Test
    fun `week strip resolves each day's historical target from plan versions`() = runTest {
        val olderPlan = PlanTargets(
            calories = 2000, proteinG = 150, carbsG = 200, fatG = 60,
            zoneLowerBound = 1900, zoneUpperBound = 2100,
        )
        val newerPlan = PlanTargets(
            calories = 2600, proteinG = 180, carbsG = 260, fatG = 80,
            zoneLowerBound = 2500, zoneUpperBound = 2700,
        )
        // Newer plan takes effect today; earlier week days fall under the older plan.
        val versions = listOf(
            PlanVersion(effectiveFrom = PlanHistory.BASELINE_DATE, targets = olderPlan),
            PlanVersion(effectiveFrom = today, targets = newerPlan),
        )
        whenever(planRepo.observeVersions()).thenReturn(flowOf(versions))
        whenever(logRepo.observeWeekCalories(any(), any())).thenReturn(flowOf(emptyMap()))

        val vm = buildVm()
        advanceUntilIdle()

        val week = vm.uiState.value.weekSummary
        assertEquals(7, week.size)
        val todaySummary = week.first { it.date == today }
        val pastSummary = week.first { it.date == today.minusDays(6) }
        assertEquals(2600, todaySummary.targetCalories)
        assertEquals(2700, todaySummary.zoneUpperBound)
        assertEquals(2000, pastSummary.targetCalories)
        assertEquals(1900, pastSummary.zoneLowerBound)
    }

    @Test
    fun `week strip falls back to current prefs when plan ledger is not yet seeded`() = runTest {
        // Unseeded ledger (empty versions, the default) + non-default current prefs.
        val prefs = PlanPreferences(
            targetCalories = 2400,
            calorieZoneLowerBound = 2300,
            calorieZoneUpperBound = 2500,
        )
        whenever(planRepo.preferences).thenReturn(flowOf(prefs))
        whenever(planRepo.observeVersions()).thenReturn(flowOf(emptyList()))

        val vm = buildVm()
        advanceUntilIdle()

        val week = vm.uiState.value.weekSummary
        assertEquals(7, week.size)
        val todaySummary = week.first { it.date == today }
        // Falls back to current prefs' zone rather than collapsing to 0.
        assertEquals(2400, todaySummary.targetCalories)
        assertEquals(2300, todaySummary.zoneLowerBound)
        assertEquals(2500, todaySummary.zoneUpperBound)
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

    @Test
    fun `shows a protein meal-suggestion card for today past the gate`() = runTest {
        val vm = buildVm() // 15:00
        advanceUntilIdle()
        val card = vm.uiState.value.mealSuggestion
        org.junit.Assert.assertNotNull(card)
        assertEquals(com.zack.recomptracker.domain.food.SuggestionFocus.PROTEIN, card!!.focus)
    }

    @Test
    fun `hides the meal-suggestion card before the day-fraction gate`() = runTest {
        val vm = buildVm(now = java.time.LocalTime.of(9, 0))
        advanceUntilIdle()
        org.junit.Assert.assertNull(vm.uiState.value.mealSuggestion)
    }
}
