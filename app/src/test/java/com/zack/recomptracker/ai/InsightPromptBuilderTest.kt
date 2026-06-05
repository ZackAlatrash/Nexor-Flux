package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightPromptBuilderTest {

    private val builder = InsightPromptBuilder()

    private fun context(
        verdict: AdjustmentVerdict = AdjustmentVerdict.HOLD,
        codes: List<String> = listOf("MAINTENANCE_TREND"),
        change: Int = 0,
        weightTrend: Double = -0.35,
        performanceTrend: PerformanceTrend = PerformanceTrend.DOWN,
        recoveryTrend: RecoveryTrend = RecoveryTrend.POOR,
        adherence: Double = 91.0,
        targetCalories: Int = 2550,
    ) = InsightContext(
        result = AdjustmentResult(
            verdict = verdict,
            recommendedCalorieChange = change,
            reasonCodes = codes,
            summary = "Stable.",
        ),
        input = AdjustmentInput(
            daysLogged = 14,
            adherencePercent = adherence,
            weeksSincePhaseStart = 3,
            weightTrendKgPerWeek = weightTrend,
            waistTrendCmPerWeek = 0.02,
            performanceTrend = performanceTrend,
            recoveryTrend = recoveryTrend,
        ),
        targetCalories = targetCalories,
        targetProteinG = 165,
    )

    @Test
    fun `prompt contains verdict name`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(verdict = AdjustmentVerdict.INCREASE_CALORIES))
        assertTrue("Expected INCREASE_CALORIES in prompt", "INCREASE_CALORIES" in prompt)
    }

    @Test
    fun `prompt contains reason codes`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(codes = listOf("LOSING_WITH_POOR_RECOVERY")))
        assertTrue("Expected reason code in prompt", "LOSING_WITH_POOR_RECOVERY" in prompt)
    }

    @Test
    fun `prompt instructs not to change verdict`() {
        val prompt = builder.buildWeeklySummaryPrompt(context())
        assertTrue("Expected instruction to preserve verdict", prompt.contains("do not change", ignoreCase = true))
    }

    @Test
    fun `prompt requests short output`() {
        val prompt = builder.buildWeeklySummaryPrompt(context())
        assertTrue("Expected short-output instruction", prompt.contains("2") || prompt.contains("3"))
    }

    @Test
    fun `prompt contains weight trend`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(weightTrend = -0.35))
        assertTrue("Expected weight trend in prompt", "-0.35" in prompt)
    }

    @Test
    fun `prompt contains performance trend`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(performanceTrend = PerformanceTrend.DOWN))
        assertTrue("Expected performance trend in prompt", "DOWN" in prompt)
    }

    @Test
    fun `prompt contains recovery trend`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(recoveryTrend = RecoveryTrend.POOR))
        assertTrue("Expected recovery trend in prompt", "POOR" in prompt)
    }

    @Test
    fun `prompt contains adherence`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(adherence = 91.0))
        assertTrue("Expected adherence in prompt", "91" in prompt)
    }

    @Test
    fun `prompt contains calorie target`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(targetCalories = 2550))
        assertTrue("Expected calorie target in prompt", "2550" in prompt || "2,550" in prompt)
    }
}
