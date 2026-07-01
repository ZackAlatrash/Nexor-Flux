package com.zack.recomptracker.ai

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CoachToolExecutorMealEditTest {

    private val fixedDate = LocalDate.of(2026, 6, 5)
    private val dateProvider = object : DateProvider { override fun today() = fixedDate }

    private fun meal(
        id: Long, name: String, calories: Int = 500, grams: Double? = null,
        protein: Double = 10.0, carbs: Double = 50.0, fat: Double = 20.0,
        basePer100Cal: Int? = null, basePer100P: Double? = null,
        basePer100C: Double? = null, basePer100F: Double? = null,
        date: LocalDate = fixedDate,
    ) = MealEntryEntity(
        id = id, date = date.toString(), mealType = "Snack", name = name,
        calories = calories, proteinG = protein, carbsG = carbs, fatG = fat,
        amountGrams = grams, basePer100Calories = basePer100Cal, basePer100ProteinG = basePer100P,
        basePer100CarbsG = basePer100C, basePer100FatG = basePer100F,
    )

    private fun dayLog(date: LocalDate, meals: List<MealEntryEntity>) =
        DayLog(date = date, dailyLog = null, meals = meals, totals = MacroTotals())

    private fun executor(log: LogRepository) = CoachToolExecutor(
        logRepository = log,
        planRepository = mock<PlanRepository>(),
        dateProvider = dateProvider,
    )

    @Test
    fun `delete_meal resolves a name to its entry and deletes it`() = runTest {
        val log = mock<LogRepository>()
        whenever(log.getDay(fixedDate)).thenReturn(dayLog(fixedDate, listOf(
            meal(1, "2 slices pizza", calories = 520),
            meal(2, "Green salad", calories = 90),
        )))
        val json = executor(log).execute("delete_meal", mapOf("name" to "pizza"))
        assertTrue(json.contains("\"success\":true"))
        assertTrue(json.contains("520"))
        verify(log).deleteMeal(1L)
    }

    @Test
    fun `delete_meal with two matching entries asks for disambiguation and deletes nothing`() = runTest {
        val log = mock<LogRepository>()
        whenever(log.getDay(fixedDate)).thenReturn(dayLog(fixedDate, listOf(
            meal(1, "Chicken breast", calories = 300),
            meal(2, "Grilled chicken thigh", calories = 250),
        )))
        val json = executor(log).execute("delete_meal", mapOf("name" to "chicken"))
        assertTrue(json.contains("needs_disambiguation"))
        verify(log, org.mockito.kotlin.never()).deleteMeal(org.mockito.kotlin.any())
    }

    @Test
    fun `delete_meal with no match returns an error and deletes nothing`() = runTest {
        val log = mock<LogRepository>()
        whenever(log.getDay(fixedDate)).thenReturn(dayLog(fixedDate, listOf(meal(1, "Oatmeal"))))
        val json = executor(log).execute("delete_meal", mapOf("name" to "sushi"))
        assertTrue(json.contains("error"))
        verify(log, org.mockito.kotlin.never()).deleteMeal(org.mockito.kotlin.any())
    }
}
