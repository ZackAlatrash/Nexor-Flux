package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CoachToolExecutorSuggestMealsTest {
    private val fixedDate = LocalDate.of(2026, 6, 5)
    private val dateProvider = object : DateProvider { override fun today() = fixedDate }

    private fun food(name: String, cal: Int, p: Double, c: Double, f: Double) =
        SavedFoodEntity(name = name, servingName = "serving", calories = cal, proteinG = p, carbsG = c, fatG = f,
            householdServingGrams = 100.0)

    private fun executor(log: LogRepository, plan: PlanRepository) =
        CoachToolExecutor(logRepository = log, planRepository = plan, dateProvider = dateProvider)

    @Test fun `suggest_meals returns focus protein and a suggestion when protein is short`() = runTest {
        val log = mock<LogRepository>()
        val plan = mock<PlanRepository>()
        whenever(plan.preferences).thenReturn(flowOf(PlanPreferences(targetCalories = 2000, targetProteinG = 150, targetCarbsG = 200, targetFatG = 60)))
        whenever(log.getDay(fixedDate)).thenReturn(
            DayLog(fixedDate, null, emptyList(), MacroTotals(calories = 1000, proteinG = 50.0, carbsG = 120.0, fatG = 30.0)),
        )
        whenever(log.getSavedFoods()).thenReturn(listOf(food("Chicken", 165, 31.0, 0.0, 4.0)))

        val json = executor(log, plan).execute("suggest_meals", emptyMap())
        assertTrue(json.contains("\"focus\":\"protein\""))
        assertTrue(json.contains("Chicken"))
        assertTrue(json.contains("remaining"))
    }

    @Test fun `suggest_meals computes remaining against the rebalance-effective target (P2-5)`() = runTest {
        val log = mock<LogRepository>()
        val plan = mock<PlanRepository>()
        // Base 2000 kcal; an active 3-day rebalance covering today reduces it by 300 → 1700 effective.
        whenever(plan.preferences).thenReturn(flowOf(PlanPreferences(targetCalories = 2000, targetProteinG = 150, targetCarbsG = 200, targetFatG = 60)))
        whenever(log.getDay(fixedDate)).thenReturn(
            DayLog(fixedDate, null, emptyList(), MacroTotals(calories = 1000, proteinG = 50.0, carbsG = 120.0, fatG = 30.0)),
        )
        whenever(log.getSavedFoods()).thenReturn(listOf(food("Chicken", 165, 31.0, 0.0, 4.0)))
        val rebalance = RebalancePlan(
            id = "p", triggerDateIso = fixedDate.minusDays(1).toString(),
            startDateIso = fixedDate.toString(), endDateIso = fixedDate.plusDays(2).toString(),
            lengthDays = 3, mode = RebalanceMode.EAT_LESS, baseCalories = 2000, dailyCalorieReduction = 300,
            extraDailySteps = 0, baseStepGoal = null, recentAvgSteps = null, surplusKcal = 600, recoveredKcal = 600,
            status = RebalanceStatus.ACTIVE, createdAtIso = "2026-06-04T09:00:00Z",
        )
        val executor = CoachToolExecutor(
            logRepository = log, planRepository = plan, dateProvider = dateProvider,
            rebalanceState = { RebalanceState(active = rebalance) },
        )

        val json = executor.execute("suggest_meals", emptyMap())
        // Remaining must be 1700 − 1000 = 700 (effective), NOT 2000 − 1000 = 1000 (base plan).
        assertTrue("expected effective remaining 700, was: $json", json.contains("\"remaining\":{\"calories\":700"))
    }

    @Test fun `suggest_meals reports library_thin when the library is empty`() = runTest {
        val log = mock<LogRepository>()
        val plan = mock<PlanRepository>()
        whenever(plan.preferences).thenReturn(flowOf(PlanPreferences(targetCalories = 2000, targetProteinG = 150, targetCarbsG = 200, targetFatG = 60)))
        whenever(log.getDay(fixedDate)).thenReturn(DayLog(fixedDate, null, emptyList(), MacroTotals(calories = 1000, proteinG = 50.0, carbsG = 120.0, fatG = 30.0)))
        whenever(log.getSavedFoods()).thenReturn(emptyList())

        val json = executor(log, plan).execute("suggest_meals", emptyMap())
        assertTrue(json.contains("\"library_thin\":true"))
    }
}
