package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CoachToolExecutorTest {

    // ── fixed date ─────────────────────────────────────────────────────────────

    private val fixedDate: LocalDate = LocalDate.of(2026, 6, 5)

    private val fixedDateProvider = object : DateProvider {
        override fun today(): LocalDate = fixedDate
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun emptyDayLog(date: LocalDate = fixedDate): DayLog =
        DayLog(date = date, dailyLog = null, meals = emptyList(), totals = MacroTotals())

    // ── tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `get_today_summary returns JSON with date and meal name`() = runTest {
        val meal = MealEntryEntity(
            date = fixedDate.toString(),
            mealType = "Lunch",
            name = "Chicken Rice",
            calories = 500,
            proteinG = 40.0,
            carbsG = 60.0,
            fatG = 8.0,
        )
        val dayLog = DayLog(
            date = fixedDate,
            dailyLog = null,
            meals = listOf(meal),
            totals = MacroTotals(calories = 500, proteinG = 40.0, carbsG = 60.0, fatG = 8.0),
        )

        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.observeDay(fixedDate)).thenReturn(flowOf(dayLog))
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("get_today_summary", emptyMap())

        assertTrue("Should contain date", result.contains("2026-06-05"))
        assertTrue("Should contain meal name", result.contains("Chicken Rice"))
    }

    @Test
    fun `get_plan returns JSON with target calories and protein`() = runTest {
        val prefs = PlanPreferences(targetCalories = 2550, targetProteinG = 165)

        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(planRepo.preferences).thenReturn(flowOf(prefs))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("get_plan", emptyMap())

        assertTrue("Should contain target calories", result.contains("2550"))
        assertTrue("Should contain target protein", result.contains("165"))
    }

    @Test
    fun `unknown tool returns JSON with error key`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("unknown_tool", emptyMap())

        assertTrue("Should contain error key", result.contains("\"error\""))
        assertTrue("Should mention tool name", result.contains("unknown_tool"))
    }

    @Test
    fun `log_meal returns success JSON mentioning food name`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.observeDay(fixedDate)).thenReturn(flowOf(emptyDayLog()))
        whenever(logRepo.addMeal(any())).thenReturn(1L)

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute(
            "log_meal",
            mapOf("name" to "Oatmeal", "calories" to "350", "protein_g" to "12.0"),
        )

        assertTrue("Should be success", result.contains("true"))
        assertTrue("Should mention food name", result.contains("Oatmeal"))
    }

    @Test
    fun `update_calorie_target returns success JSON mentioning new target`() = runTest {
        val initialPrefs = PlanPreferences(targetCalories = 2550)
        var savedPrefs: PlanPreferences? = null

        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(planRepo.preferences).thenReturn(flowOf(initialPrefs))
        whenever(planRepo.save(any())).thenAnswer { invocation ->
            savedPrefs = invocation.getArgument(0)
            Unit
        }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("update_calorie_target", mapOf("target_calories" to "2700"))

        assertTrue("Should be success", result.contains("true"))
        assertTrue("Should mention new target", result.contains("2700"))
        assertFalse("Old target should have been replaced", savedPrefs?.targetCalories == 2550)
    }
}
