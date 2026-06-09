package com.zack.recomptracker.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class RestOfDayInsightPromptTest {

    private val builder = InsightPromptBuilder()

    private fun ctx(
        caloriesConsumed: Int = 1420,
        targetCalories: Int = 2200,
        calorieZoneLowerBound: Int = 2100,
        calorieZoneUpperBound: Int = 2300,
        proteinConsumedG: Double = 102.0,
        proteinTargetG: Int = 165,
        mealsLoggedCount: Int = 2,
    ) = RestOfDayInsightContext(
        caloriesConsumed, targetCalories, calorieZoneLowerBound,
        calorieZoneUpperBound, proteinConsumedG, proteinTargetG, mealsLoggedCount,
    )

    @Test
    fun `shows concrete calorie numbers`() {
        val prompt = builder.buildRestOfDayPrompt(ctx(caloriesConsumed = 1420, targetCalories = 2200))
        assertTrue("1420" in prompt)
        assertTrue("2200" in prompt)
    }

    @Test
    fun `shows calories remaining`() {
        val prompt = builder.buildRestOfDayPrompt(ctx(caloriesConsumed = 1420, targetCalories = 2200))
        assertTrue("780" in prompt)
    }

    @Test
    fun `shows protein gap`() {
        val prompt = builder.buildRestOfDayPrompt(ctx(proteinConsumedG = 102.0, proteinTargetG = 165))
        assertTrue("63" in prompt)
    }

    @Test
    fun `shows meals logged count`() {
        assertTrue("2" in builder.buildRestOfDayPrompt(ctx(mealsLoggedCount = 2)))
    }

    @Test
    fun `instructs not to invent foods`() {
        assertTrue(builder.buildRestOfDayPrompt(ctx()).contains("invent", ignoreCase = true))
    }

    @Test
    fun `requests short output and few-shot`() {
        val prompt = builder.buildRestOfDayPrompt(ctx())
        assertTrue("2" in prompt || "3" in prompt)
        assertTrue("Example output" in prompt)
    }

    @Test
    fun `protein remaining clamps to zero when over target`() {
        val prompt = builder.buildRestOfDayPrompt(ctx(proteinConsumedG = 180.0, proteinTargetG = 165))
        assertTrue("0 g remaining" in prompt)
    }

    @Test
    fun `calories remaining can go negative when over target`() {
        val prompt = builder.buildRestOfDayPrompt(ctx(caloriesConsumed = 2400, targetCalories = 2200))
        assertTrue("-200 remaining" in prompt)
    }
}
