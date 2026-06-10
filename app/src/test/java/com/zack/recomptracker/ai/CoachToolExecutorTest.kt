package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
        whenever(logRepo.getDay(fixedDate)).thenReturn(dayLog)
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("get_today_summary", emptyMap())

        assertTrue("Should contain date", result.contains("2026-06-05"))
        assertTrue("Should contain meal name", result.contains("Chicken Rice"))
    }

    @Test
    fun `get_today_summary tags planned meals and keeps totals eaten-only`() = runTest {
        val eaten = MealEntryEntity(
            date = fixedDate.toString(), mealType = "Lunch", name = "Chicken Rice",
            calories = 500, proteinG = 40.0, carbsG = 60.0, fatG = 8.0, planned = false,
        )
        val plan = MealEntryEntity(
            date = fixedDate.toString(), mealType = "Dinner", name = "Salmon Bowl",
            calories = 600, proteinG = 45.0, carbsG = 40.0, fatG = 25.0, planned = true,
        )
        val dayLog = DayLog(
            date = fixedDate,
            dailyLog = null,
            meals = listOf(eaten, plan),
            totals = MacroTotals(calories = 500, proteinG = 40.0, carbsG = 60.0, fatG = 8.0),
            plannedTotals = MacroTotals(calories = 600, proteinG = 45.0, carbsG = 40.0, fatG = 25.0),
        )
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getDay(fixedDate)).thenReturn(dayLog)
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("get_today_summary", emptyMap())

        // Both meals listed, each carrying its planned flag.
        assertTrue("Plan meal tagged planned", result.contains(""""name":"Salmon Bowl""""))
        assertTrue("Eaten meal tagged not planned", result.contains(""""planned":false"""))
        assertTrue("Plan meal tagged planned", result.contains(""""planned":true"""))
        // Eaten totals exclude the plan; planned_totals carry it separately.
        assertTrue("totals are eaten-only", result.contains(""""totals":{"calories":500"""))
        assertTrue("planned_totals present", result.contains(""""planned_totals":{"calories":600"""))
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

    // ── search_food_library ────────────────────────────────────────────────────

    private fun food(name: String, calories: Int = 400) =
        SavedFoodEntity(name = name, servingName = "1 serving", calories = calories,
            proteinG = 30.0, carbsG = 50.0, fatG = 10.0)

    @Test
    fun `search_food_library returns match on exact name case-insensitive`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(food("Chicken Rice", 450)))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("search_food_library", mapOf("query" to "chicken rice"))

        assertTrue("Should find match", result.contains("Chicken Rice"))
        assertTrue("Should include calories", result.contains("450"))
        assertFalse("Should not be empty result", result.contains(""""count":0"""))
    }

    @Test
    fun `search_food_library returns match on partial query`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(food("Chicken Rice Bowl")))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("search_food_library", mapOf("query" to "chicken rice"))

        assertTrue("Should find partial match", result.contains("Chicken Rice Bowl"))
    }

    @Test
    fun `search_food_library scales macros when grams parameter is provided`() = runTest {
        val ketchup = SavedFoodEntity(
            name = "Ketchup", servingName = "1 tbsp",
            calories = 15, proteinG = 0.2, carbsG = 3.0, fatG = 0.0,
            householdServingGrams = 15.0,
        )
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(ketchup))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        // 1g out of 15g serving → macros should be 1/15 of saved values
        val result = executor.execute("search_food_library", mapOf("query" to "ketchup", "grams" to "1"))

        // 15 kcal × (1/15) = 1 kcal
        assertTrue("Calories should be scaled to 1", result.contains("\"calories\":1"))
        assertTrue("Serving label should reflect requested grams", result.contains("1g"))
    }

    @Test
    fun `search_food_library returns per-serving macros when no grams specified`() = runTest {
        val ketchup = SavedFoodEntity(
            name = "Ketchup", servingName = "1 tbsp",
            calories = 15, proteinG = 0.2, carbsG = 3.0, fatG = 0.0,
            householdServingGrams = 15.0,
        )
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(ketchup))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("search_food_library", mapOf("query" to "ketchup"))

        assertTrue("Should return full serving calories", result.contains("\"calories\":15"))
        assertTrue("Should include serving label", result.contains("1 tbsp"))
    }

    @Test
    fun `search_food_library returns empty when no match`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(food("Oatmeal")))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("search_food_library", mapOf("query" to "pizza"))

        assertTrue("Should return empty results", result.contains(""""count":0"""))
    }

    @Test
    fun `search_food_library ranks exact match above partial`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(
            food("Chicken Rice Bowl"),   // partial match
            food("Chicken Rice", 500),  // exact match — should come first
        ))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("search_food_library", mapOf("query" to "Chicken Rice"))

        val exactIdx = result.indexOf("\"Chicken Rice\"")
        val partialIdx = result.indexOf("Chicken Rice Bowl")
        assertTrue("Exact match should appear before partial", exactIdx < partialIdx)
    }

    // ── log_meal ───────────────────────────────────────────────────────────────

    @Test
    fun `log_meal returns success JSON mentioning food name`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(emptyList())
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenReturn(1L)

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
        whenever(logRepo.getSavedFoods()).thenReturn(emptyList())
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenReturn(1L)

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_meal", mapOf("name" to "Rice", "calories" to "400"))

        assertTrue("Should succeed", result.contains("\"success\":true"))
        assertTrue("Should echo name", result.contains("Rice"))
        assertTrue("Should echo calories", result.contains("400"))
    }

    @Test
    fun `log_meal assigns entry to matching slot when slot name equals meal_type`() = runTest {
        var capturedSlotId: Long? = -1L
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        val lunchSlot = MealSlotEntity(id = 42L, name = "Lunch", sortOrder = 1)
        whenever(logRepo.getSavedFoods()).thenReturn(emptyList())
        whenever(logRepo.getSlots()).thenReturn(listOf(lunchSlot))
        whenever(logRepo.addMealToSlot(any(), any())).thenAnswer { inv ->
            capturedSlotId = inv.getArgument(1)
            1L
        }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        executor.execute("log_meal", mapOf("name" to "Pasta", "calories" to "600", "meal_type" to "Lunch"))

        assertTrue("Should assign to Lunch slot", capturedSlotId == 42L)
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
    fun `log_meal without calories returns error JSON when food not in library`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(emptyList())

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_meal", mapOf("name" to "Banana"))

        assertTrue("Should contain error key", result.contains("\"error\""))
    }

    @Test
    fun `log_metric weight_kg saves weight and returns success`() = runTest {
        var saved: DailyMetricsInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getDay(fixedDate)).thenReturn(emptyDayLog())
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
        whenever(logRepo.getDay(fixedDate)).thenReturn(emptyDayLog())
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
        whenever(logRepo.getDay(fixedDate)).thenReturn(emptyDayLog())
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
        // getWeekMacros returns only dates that have entries; absent = not logged
        val macroMap = mapOf(
            LocalDate.of(2026, 5, 30) to MacroTotals(calories = 2400),
            LocalDate.of(2026, 5, 31) to MacroTotals(calories = 2300),
            // 2026-06-01 absent → not logged
            LocalDate.of(2026, 6, 2) to MacroTotals(calories = 2550),
            LocalDate.of(2026, 6, 3) to MacroTotals(calories = 2200),
            LocalDate.of(2026, 6, 4) to MacroTotals(calories = 2600),
            LocalDate.of(2026, 6, 5) to MacroTotals(calories = 2450),
        )
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getWeekMacros(start, today)).thenReturn(macroMap)

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
        whenever(logRepo.getDay(pastDate)).thenReturn(dayLog)

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("get_today_summary", mapOf("date" to "2026-06-04"))

        assertTrue("Should use the given date", result.contains("2026-06-04"))
    }

    // ── update_calorie_target validation ───────────────────────────────────────

    @Test
    fun `update_calorie_target rejects value below 500`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("update_calorie_target", mapOf("target_calories" to "400"))

        assertTrue("Should contain error", result.contains("\"error\""))
        assertTrue("Should mention lower bound", result.contains("500"))
    }

    @Test
    fun `update_calorie_target rejects value above 6000`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("update_calorie_target", mapOf("target_calories" to "7000"))

        assertTrue("Should contain error", result.contains("\"error\""))
        assertTrue("Should mention upper bound", result.contains("6000"))
    }

    @Test
    fun `update_calorie_target without target_calories returns error`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("update_calorie_target", emptyMap())

        assertTrue("Should contain error", result.contains("\"error\""))
    }

    // ── log_metric range validation ────────────────────────────────────────────

    @Test
    fun `log_metric energy_score above 10 returns error`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_metric", mapOf("metric" to "energy_score", "value" to "11"))

        assertTrue("Should contain error", result.contains("\"error\""))
        assertTrue("Should mention valid range", result.contains("between 1 and 10"))
    }

    @Test
    fun `log_metric weight_kg below 20 returns error`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("log_metric", mapOf("metric" to "weight_kg", "value" to "5.0"))

        assertTrue("Should contain error", result.contains("\"error\""))
        assertTrue("Should mention valid range", result.contains("between 20 and 300"))
    }

    // ── log_meal optional fields ───────────────────────────────────────────────

    @Test
    fun `log_meal with meal_type Breakfast saves as Breakfast`() = runTest {
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(emptyList())
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        executor.execute("log_meal", mapOf("name" to "Eggs", "calories" to "200", "meal_type" to "Breakfast"))

        assertTrue("Should save as Breakfast", captured?.mealType == "Breakfast")
    }

    @Test
    fun `log_meal with future date plans the meal`() = runTest {
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(emptyList())
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider) // today = 2026-06-05
        val result = executor.execute(
            "log_meal",
            mapOf("name" to "Oatmeal", "calories" to "300", "date" to "2026-06-07"),
        )

        assertTrue("Entry should be planned", captured?.planned == true)
        assertEquals(LocalDate.of(2026, 6, 7), captured?.date)
        assertTrue("Result mentions planned", result.contains("\"planned\""))
    }

    @Test
    fun `log_meal today is eaten not planned`() = runTest {
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(emptyList())
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        executor.execute("log_meal", mapOf("name" to "Eggs", "calories" to "200"))

        assertFalse("Entry should not be planned", captured?.planned == true)
        assertEquals(fixedDate, captured?.date)
    }

    @Test
    fun `log_meal with protein_g saves correct protein when food not in library`() = runTest {
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(emptyList())
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        executor.execute("log_meal", mapOf("name" to "Custom Food", "calories" to "300", "protein_g" to "40.0"))

        assertTrue("Should save protein from model args", captured?.proteinG == 40.0)
    }

    @Test
    fun `log_meal with invalid meal_type falls back to Snack`() = runTest {
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(emptyList())
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        executor.execute("log_meal", mapOf("name" to "Oatmeal", "calories" to "300", "meal_type" to "Elevenses"))

        assertTrue("Should fall back to Snack", captured?.mealType == "Snack")
    }

    // ── log_meal library lookup ────────────────────────────────────────────────

    @Test
    fun `log_meal uses library macros when food found by name`() = runTest {
        val ketchup = SavedFoodEntity(
            name = "Ketchup", servingName = "1 tbsp",
            calories = 15, proteinG = 0.2, carbsG = 3.0, fatG = 0.0,
            householdServingGrams = 15.0,
        )
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(ketchup))
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        // Model passes a wrong calorie estimate — tool should override with library value
        executor.execute("log_meal", mapOf("name" to "ketchup", "calories" to "999"))

        assertTrue("Should use library name (capitalized)", captured?.name == "Ketchup")
        assertTrue("Should use library calories (15)", captured?.calories == 15)
        assertTrue("Should use library protein", captured?.proteinG == 0.2)
        assertTrue("Should use library carbs", captured?.carbsG == 3.0)
    }

    @Test
    fun `log_meal scales library macros when grams provided`() = runTest {
        val ketchup = SavedFoodEntity(
            name = "Ketchup", servingName = "1 tbsp",
            calories = 15, proteinG = 0.2, carbsG = 3.0, fatG = 0.0,
            householdServingGrams = 15.0,
        )
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(ketchup))
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        // 1g of 15g serving → 1/15 of macros
        executor.execute("log_meal", mapOf("name" to "ketchup", "calories" to "1", "grams" to "1"))

        assertTrue("Calories should be scaled to 1", captured?.calories == 1)
        assertTrue("Name should be from library", captured?.name == "Ketchup")
    }

    @Test
    fun `log_meal falls back to model macros when food not in library`() = runTest {
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(emptyList())
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        executor.execute("log_meal", mapOf("name" to "Mystery Food", "calories" to "500", "protein_g" to "30.0"))

        assertTrue("Should use model calories as fallback", captured?.calories == 500)
        assertTrue("Should use model protein as fallback", captured?.proteinG == 30.0)
    }
}
