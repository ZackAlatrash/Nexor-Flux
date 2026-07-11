package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.remote.WebResult
import com.zack.recomptracker.data.remote.WebSearchProvider
import com.zack.recomptracker.data.remote.WebSearchResult
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.toPlanTargets
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
    fun `search_food_library scales macros to requested grams on a per-100g basis`() = runTest {
        // Saved-food macros are stored PER 100 g; the household serving (15 g) must NOT affect gram scaling.
        val ketchup = SavedFoodEntity(
            name = "Ketchup", servingName = "1 tbsp",
            calories = 100, proteinG = 1.2, carbsG = 25.0, fatG = 0.1,
            householdServingGrams = 15.0,
        )
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(ketchup))

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        // 15 g of a 100 kcal/100g food → 100 × 15/100 = 15 kcal.
        val result = executor.execute("search_food_library", mapOf("query" to "ketchup", "grams" to "15"))

        assertTrue("Calories should be 15% of the per-100g value", result.contains("\"calories\":15"))
        assertTrue("Serving label should reflect requested grams", result.contains("15g"))
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
    fun `log_meal falls back to the first slot when no slot name matches the meal_type`() = runTest {
        var capturedSlotId: Long? = -1L
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        // Default slots have no "snack"; the model asks to log a snack.
        val meal1 = MealSlotEntity(id = 7L, name = "Meal 1", sortOrder = 0)
        val dinner = MealSlotEntity(id = 9L, name = "Dinner", sortOrder = 2)
        whenever(logRepo.getSavedFoods()).thenReturn(emptyList())
        whenever(logRepo.getSlots()).thenReturn(listOf(meal1, dinner))
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv ->
            capturedSlotId = inv.getArgument(1)
            1L
        }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        executor.execute("log_meal", mapOf("name" to "Trail mix", "calories" to "300", "meal_type" to "snack"))

        // No slot named "snack" → falls back to the first slot (Meal 1), never a null slot (P1-22).
        assertTrue("Should fall back to the first slot, not null", capturedSlotId == 7L)
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
    fun `get_weekly_trends returns graded adherence and days_logged`() = runTest {
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
        val prefs = PlanPreferences(targetCalories = 2550)
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getWeekMacros(start, today)).thenReturn(macroMap)
        whenever(planRepo.preferences).thenReturn(flowOf(prefs))
        whenever(planRepo.targetsByDate(any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val dates = inv.arguments[0] as List<LocalDate>
            dates.associateWith { prefs.toPlanTargets() }
        }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("get_weekly_trends", emptyMap())

        assertTrue("Should contain week_start", result.contains("2026-05-30"))
        assertTrue("Should contain week_end", result.contains("2026-06-05"))
        // 6 of 7 days logged, surfaced as its own signal
        assertTrue("Should report days_logged", result.contains("\"days_logged\":6"))
        // Graded quality across the 6 logged days averages ~94 (not the old 6/7 = 85 count)
        assertTrue("Should report graded adherence ~94", result.contains("\"adherence_percent\":94"))
    }

    @Test
    fun `get_weekly_trends does not score an over-target day as fully adherent`() = runTest {
        val today = LocalDate.of(2026, 6, 5)
        val start = today.minusDays(6)
        // Single logged day, far over target → graded score must be well below 100.
        val macroMap = mapOf(LocalDate.of(2026, 6, 5) to MacroTotals(calories = 4000))
        val prefs = PlanPreferences(targetCalories = 2550)
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getWeekMacros(start, today)).thenReturn(macroMap)
        whenever(planRepo.preferences).thenReturn(flowOf(prefs))
        whenever(planRepo.targetsByDate(any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val dates = inv.arguments[0] as List<LocalDate>
            dates.associateWith { prefs.toPlanTargets() }
        }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val result = executor.execute("get_weekly_trends", emptyMap())

        assertFalse("Over-target day must not be 100% adherent", result.contains("\"adherence_percent\":100"))
        assertTrue("Should still report one logged day", result.contains("\"days_logged\":1"))
    }

    @Test
    fun `get_weekly_trends grades against the effective target during an active rebalance`() = runTest {
        val today = LocalDate.of(2026, 6, 5)
        val start = today.minusDays(6)
        // One logged day today, eaten exactly at the REDUCED target (2300) — 100% only if the tool
        // resolves the effective target; against the base 2550 it would score below 100.
        val macroMap = mapOf(today to MacroTotals(calories = 2300))
        val prefs = PlanPreferences(targetCalories = 2550)
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getWeekMacros(start, today)).thenReturn(macroMap)
        whenever(planRepo.preferences).thenReturn(flowOf(prefs))
        whenever(planRepo.targetsByDate(any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val dates = inv.arguments[0] as List<LocalDate>
            dates.associateWith { prefs.toPlanTargets() }
        }
        // 3-day rebalance covering today (−250 kcal → effective 2300).
        val plan = com.zack.recomptracker.domain.rebalance.RebalancePlan(
            id = "p",
            triggerDateIso = today.minusDays(2).toString(),
            startDateIso = today.minusDays(2).toString(),
            endDateIso = today.toString(),
            lengthDays = 3,
            mode = com.zack.recomptracker.domain.rebalance.RebalanceMode.EAT_LESS,
            baseCalories = 2550,
            dailyCalorieReduction = 250,
            extraDailySteps = 0,
            baseStepGoal = null,
            recentAvgSteps = null,
            surplusKcal = 600,
            recoveredKcal = 600,
            status = com.zack.recomptracker.domain.rebalance.RebalanceStatus.ACTIVE,
            createdAtIso = "2026-06-01T09:00:00Z",
        )
        val executor = CoachToolExecutor(
            logRepo, planRepo, fixedDateProvider,
            rebalanceState = {
                com.zack.recomptracker.domain.rebalance.RebalanceState(active = plan)
            },
        )
        val result = executor.execute("get_weekly_trends", emptyMap())

        assertTrue("eaten at the reduced target is fully adherent", result.contains("\"adherence_percent\":100"))
        assertTrue("still one logged day", result.contains("\"days_logged\":1"))
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
    fun `log_meal with no grams logs the household serving with amount and macros on one basis`() = runTest {
        // 15 kcal per 100 g, household serving 15 g. Logging with no grams must scale the per-100g
        // macros to the 15 g serving AND display 15 g — one basis, so the entry is self-consistent.
        // The old code scaled macros at 100 g (15 kcal) while displaying 15 g (review P1-8).
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
        // Model passes a wrong calorie estimate — tool should override with the library value.
        executor.execute("log_meal", mapOf("name" to "ketchup", "calories" to "999"))

        assertTrue("Should use library name (capitalized)", captured?.name == "Ketchup")
        assertTrue("Amount is the 15 g household serving", captured?.amountGrams == 15.0)
        assertTrue("Calories = 15/100g × 15/100 = 2, matching the 15 g amount", captured?.calories == 2)
        // Protein scales on the SAME basis as the amount (0.2/100g × 15/100 ≈ 0.03).
        assertTrue("Protein scaled to the 15 g serving", (captured?.proteinG ?: -1.0) in 0.029..0.031)
    }

    @Test
    fun `log_meal scales library macros to requested grams on a per-100g basis`() = runTest {
        // Saved-food macros are stored PER 100 g; the household serving (15 g) must NOT affect gram scaling.
        val ketchup = SavedFoodEntity(
            name = "Ketchup", servingName = "1 tbsp",
            calories = 100, proteinG = 1.2, carbsG = 25.0, fatG = 0.1,
            householdServingGrams = 15.0,
        )
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(ketchup))
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        // 30 g of a 100 kcal/100g food → 100 × 30/100 = 30 kcal.
        executor.execute("log_meal", mapOf("name" to "ketchup", "grams" to "30"))

        assertTrue("Calories should be per-100g scaled (100 × 30/100 = 30)", captured?.calories == 30)
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

    @Test
    fun `log_meal ignores a loose score-0 library match and keeps the model macros`() = runTest {
        // "bar protein" — both words appear in "Chocolate Protein Bar XL" but not as a substring, so
        // it scores 0. Too loose to silently override the food/macros the user confirmed (review P1-9).
        val libFood = SavedFoodEntity(
            name = "Chocolate Protein Bar XL", servingName = "1 bar",
            calories = 400, proteinG = 20.0, carbsG = 40.0, fatG = 12.0, householdServingGrams = 60.0,
        )
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(libFood))
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }

        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        executor.execute("log_meal", mapOf("name" to "bar protein", "calories" to "250"))

        assertTrue("A score-0 match must NOT override the model's name", captured?.name == "bar protein")
        assertTrue("Model calories used, not the library's", captured?.calories == 250)
    }

    @Test
    fun `describeLoggedMeal matches what log_meal writes for a library food`() = runTest {
        val ketchup = SavedFoodEntity(
            name = "Ketchup", servingName = "1 tbsp",
            calories = 100, proteinG = 1.2, carbsG = 25.0, fatG = 0.1, householdServingGrams = 15.0,
        )
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(ketchup))
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }
        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        // Model passes the wrong name case and a wrong calorie estimate.
        val args = mapOf("name" to "ketchup", "grams" to "30", "meal_type" to "Lunch", "calories" to "999")

        val description = executor.describeLoggedMeal(args)
        executor.execute("log_meal", args)

        // The dialog describes the RESOLVED food + macros the executor will write, not the model args.
        assertTrue("names the resolved library food", description.contains("Ketchup"))
        assertTrue("shows the executed calories (30 g × 100/100)", description.contains("30 kcal"))
        assertTrue("does not leak the model's wrong 999", !description.contains("999"))
        assertTrue("says logged to today", description.contains("to today's food log"))
        // …and execution agrees with what the dialog said.
        assertTrue(captured?.name == "Ketchup")
        assertTrue(captured?.calories == 30)
    }

    @Test
    fun `describeLoggedMeal and log_meal agree that a future date plans the meal`() = runTest {
        val ketchup = SavedFoodEntity(
            name = "Ketchup", servingName = "1 tbsp",
            calories = 100, proteinG = 1.2, carbsG = 25.0, fatG = 0.1, householdServingGrams = 15.0,
        )
        var captured: MealEntryInput? = null
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        whenever(logRepo.getSavedFoods()).thenReturn(listOf(ketchup))
        whenever(logRepo.getSlots()).thenReturn(emptyList())
        whenever(logRepo.addMealToSlot(any(), anyOrNull())).thenAnswer { inv -> captured = inv.getArgument(0); 1L }
        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider)
        val tomorrow = fixedDate.plusDays(1).toString()
        val args = mapOf("name" to "ketchup", "grams" to "30", "date" to tomorrow)

        val description = executor.describeLoggedMeal(args)
        executor.execute("log_meal", args)

        assertTrue("dialog says the meal is planned, with the date", description.contains("planned for $tomorrow"))
        assertTrue("dialog does NOT claim today's food log", !description.contains("to today's food log"))
        assertTrue("executor actually plans it", captured?.planned == true)
    }

    // ── search_web ─────────────────────────────────────────────────────────────

    @Test
    fun `search_web returns capped JSON from the provider`() = runTest {
        val logRepo = mock<LogRepository>()
        val planRepo = mock<PlanRepository>()
        val provider = object : WebSearchProvider {
            override suspend fun search(query: String): WebSearchResult =
                WebSearchResult(answer = "Big Mac is ~563 kcal.", results = listOf(
                    WebResult("Big Mac", "https://example.com/bigmac", "563 calories"),
                ))
        }
        val executor = CoachToolExecutor(logRepo, planRepo, fixedDateProvider, provider)
        val result = executor.execute("search_web", mapOf("query" to "big mac calories"))
        assertTrue("has answer", result.contains("563 kcal"))
        assertTrue("has source url", result.contains("https://example.com/bigmac"))
    }

    @Test
    fun `search_web is unavailable when no provider is configured`() = runTest {
        val executor = CoachToolExecutor(mock(), mock(), fixedDateProvider) // provider defaults to null
        val result = executor.execute("search_web", mapOf("query" to "big mac calories"))
        assertEquals("""{"error":"web search unavailable"}""", result)
    }

    @Test
    fun `search_web is unavailable when the provider returns null`() = runTest {
        val provider = object : WebSearchProvider {
            override suspend fun search(query: String): WebSearchResult? = null
        }
        val executor = CoachToolExecutor(mock(), mock(), fixedDateProvider, provider)
        val result = executor.execute("search_web", mapOf("query" to "x"))
        assertEquals("""{"error":"web search unavailable"}""", result)
    }

    @Test
    fun `search_web with a blank query returns a requires-query error`() = runTest {
        val provider = object : WebSearchProvider {
            override suspend fun search(query: String): WebSearchResult? = null
        }
        val executor = CoachToolExecutor(mock(), mock(), fixedDateProvider, provider)
        val result = executor.execute("search_web", mapOf("query" to "   "))
        assertEquals("""{"error":"search_web requires 'query'"}""", result)
    }
}
