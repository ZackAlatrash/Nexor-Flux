package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
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
