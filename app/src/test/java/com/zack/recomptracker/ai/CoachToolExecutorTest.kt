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
            mapOf("name" to "Oatmeal", "calories" to "350"),
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

    @Test
    fun `log_meal only requires name and calories, ignores extra keys`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.observeDay(fixedDate)).thenReturn(flowOf(emptyDayLog()))
        whenever(logRepo.addMeal(any())).thenReturn(1L)

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_meal", mapOf("name" to "Rice", "calories" to "400"))

        assertTrue("Should succeed", result.contains("\"success\":true"))
        assertTrue("Should echo name", result.contains("Rice"))
        assertTrue("Should echo calories", result.contains("400"))
    }

    @Test
    fun `log_meal without name returns error JSON`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_meal", mapOf("calories" to "400"))

        assertTrue("Should contain error key", result.contains("\"error\""))
    }

    @Test
    fun `log_meal without calories returns error JSON`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_meal", mapOf("name" to "Banana"))

        assertTrue("Should contain error key", result.contains("\"error\""))
    }

    @Test
    fun `log_metric weight_kg saves weight and returns success`() = runTest {
        var saved: DailyMetricsInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.observeDay(fixedDate)).thenReturn(flowOf(emptyDayLog()))
        whenever(logRepo.saveDailyMetrics(any())).thenAnswer { inv ->
            saved = inv.getArgument(0); Unit
        }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_metric", mapOf("metric" to "weight_kg", "value" to "82.5"))

        assertTrue("Should succeed", result.contains("\"success\":true"))
        assertTrue("Should echo metric", result.contains("weight_kg"))
        assertTrue("Should echo value", result.contains("82.5"))
        assertTrue("Weight should be saved", saved?.bodyWeightKg == 82.5)
    }

    @Test
    fun `log_metric sleep_hours saves sleep and returns success`() = runTest {
        var saved: DailyMetricsInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.observeDay(fixedDate)).thenReturn(flowOf(emptyDayLog()))
        whenever(logRepo.saveDailyMetrics(any())).thenAnswer { inv ->
            saved = inv.getArgument(0); Unit
        }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_metric", mapOf("metric" to "sleep_hours", "value" to "7.5"))

        assertTrue("Should succeed", result.contains("\"success\":true"))
        assertTrue("Sleep should be saved", saved?.sleepHours == 7.5)
    }

    @Test
    fun `log_metric unknown metric returns error JSON`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_metric", mapOf("metric" to "mood", "value" to "9"))

        assertTrue("Should contain error key", result.contains("\"error\""))
        assertTrue("Should mention metric name", result.contains("mood"))
    }

    @Test
    fun `log_metric non-numeric value returns error JSON`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_metric", mapOf("metric" to "weight_kg", "value" to "heavy"))

        assertTrue("Should contain error key", result.contains("\"error\""))
    }

    @Test
    fun `log_metric energy_score rejects fractional values`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_metric", mapOf("metric" to "energy_score", "value" to "8.9"))

        assertTrue("Should contain error key", result.contains("\"error\""))
        assertTrue("Should mention must be whole number", result.contains("whole number"))
    }

    @Test
    fun `log_metric hunger_score rejects fractional values`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_metric", mapOf("metric" to "hunger_score", "value" to "5.5"))

        assertTrue("Should contain error key", result.contains("\"error\""))
    }

    @Test
    fun `log_metric soreness_score rejects fractional values`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_metric", mapOf("metric" to "soreness_score", "value" to "3.2"))

        assertTrue("Should contain error key", result.contains("\"error\""))
    }

    @Test
    fun `log_metric energy_score accepts whole numbers`() = runTest {
        var saved: DailyMetricsInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.observeDay(fixedDate)).thenReturn(flowOf(emptyDayLog()))
        whenever(logRepo.saveDailyMetrics(any())).thenAnswer { inv ->
            saved = inv.getArgument(0); Unit
        }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_metric", mapOf("metric" to "energy_score", "value" to "8.0"))

        assertTrue("Should succeed", result.contains("\"success\":true"))
        assertTrue("Should save as integer", saved?.energyScore == 8)
    }

    @Test
    fun `log_daily_metrics is now an unknown tool`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_daily_metrics", mapOf("weight_kg" to "80.0"))

        assertTrue("Should contain error key", result.contains("\"error\""))
    }

    @Test
    fun `get_weekly_trends returns JSON with week_start, daily_calories and adherence`() = runTest {
        val today = LocalDate.of(2026, 6, 5)
        val start = today.minusDays(6) // 2026-05-30
        val calorieMap = mapOf(
            LocalDate.of(2026, 5, 30) to 2400,
            LocalDate.of(2026, 5, 31) to 2300,
            LocalDate.of(2026, 6, 1) to 0,   // not logged
            LocalDate.of(2026, 6, 2) to 2550,
            LocalDate.of(2026, 6, 3) to 2200,
            LocalDate.of(2026, 6, 4) to 2600,
            LocalDate.of(2026, 6, 5) to 2450,
        )
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.observeWeekCalories(start, today)).thenReturn(flowOf(calorieMap))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("get_weekly_trends", emptyMap())

        assertTrue("Should contain week_start", result.contains("2026-05-30"))
        assertTrue("Should contain week_end", result.contains("2026-06-05"))
        assertTrue("Should contain adherence_percent", result.contains("adherence_percent"))
        // 6 out of 7 days logged → 85%
        assertTrue("Should have correct adherence", result.contains("85"))
    }

    @Test
    fun `get_today_summary with date arg uses that date`() = runTest {
        val pastDate = LocalDate.of(2026, 6, 4)
        val dayLog = emptyDayLog(pastDate)
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.observeDay(pastDate)).thenReturn(flowOf(dayLog))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("get_today_summary", mapOf("date" to "2026-06-04"))

        assertTrue("Should use the given date", result.contains("2026-06-04"))
    }
}
