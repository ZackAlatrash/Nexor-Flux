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
        waistTrend: Double = 0.02,
        performanceTrend: PerformanceTrend = PerformanceTrend.DOWN,
        recoveryTrend: RecoveryTrend = RecoveryTrend.POOR,
        adherence: Double = 91.0,
        weeksSincePhaseStart: Int = 3,
        targetCalories: Int = 2550,
        summary: String = "Stable.",
    ) = InsightContext(
        result = AdjustmentResult(
            verdict = verdict,
            recommendedCalorieChange = change,
            reasonCodes = codes,
            summary = summary,
        ),
        input = AdjustmentInput(
            daysLogged = 14,
            adherencePercent = adherence,
            weeksSincePhaseStart = weeksSincePhaseStart,
            weightTrendKgPerWeek = weightTrend,
            waistTrendCmPerWeek = waistTrend,
            performanceTrend = performanceTrend,
            recoveryTrend = recoveryTrend,
        ),
        targetCalories = targetCalories,
        targetProteinG = 165,
    )

    // --- Verdict label mapping ---

    @Test
    fun `INCREASE_CALORIES verdict maps to human label`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(verdict = AdjustmentVerdict.INCREASE_CALORIES))
        assertTrue("Expected 'Increase calories' in prompt", "Increase calories" in prompt)
        assertFalse("Raw enum name must not appear", "INCREASE_CALORIES" in prompt)
    }

    @Test
    fun `HOLD verdict maps to human label`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(verdict = AdjustmentVerdict.HOLD))
        assertTrue("Expected 'Hold' label in prompt", "Hold" in prompt)
        assertFalse("Raw enum name must not appear as verdict", "Verdict: HOLD" in prompt)
    }

    @Test
    fun `REDUCE_CALORIES verdict maps to human label`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(verdict = AdjustmentVerdict.REDUCE_CALORIES))
        assertTrue("Expected 'Reduce calories' in prompt", "Reduce calories" in prompt)
        assertFalse("Raw enum name must not appear", "REDUCE_CALORIES" in prompt)
    }

    @Test
    fun `WAIT_FOR_DATA verdict maps to human label`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(verdict = AdjustmentVerdict.WAIT_FOR_DATA))
        assertTrue("Expected 'Insufficient data' in prompt", "Insufficient data" in prompt)
        assertFalse("Raw enum name must not appear", "WAIT_FOR_DATA" in prompt)
    }

    // --- Reason code mapping ---

    @Test
    fun `LOSING_WITH_POOR_RECOVERY maps to description`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(codes = listOf("LOSING_WITH_POOR_RECOVERY")))
        assertTrue("Expected description", "performance or recovery is suffering" in prompt)
        assertFalse("Raw code must not appear", "LOSING_WITH_POOR_RECOVERY" in prompt)
    }

    @Test
    fun `GAINING_WITH_WAIST_INCREASE maps to description`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(codes = listOf("GAINING_WITH_WAIST_INCREASE")))
        assertTrue("Expected description", "waist are trending upward" in prompt)
        assertFalse("Raw code must not appear", "GAINING_WITH_WAIST_INCREASE" in prompt)
    }

    @Test
    fun `EARLY_SCALE_JUMP maps to description`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(codes = listOf("EARLY_SCALE_JUMP")))
        assertTrue("Expected description", "week one" in prompt)
        assertFalse("Raw code must not appear", "EARLY_SCALE_JUMP" in prompt)
    }

    @Test
    fun `MAINTENANCE_TREND maps to description`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(codes = listOf("MAINTENANCE_TREND")))
        assertTrue("Expected description", "all stable" in prompt)
        assertFalse("Raw code must not appear", "MAINTENANCE_TREND" in prompt)
    }

    @Test
    fun `unknown reason code has readable fallback`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(codes = listOf("SOME_FUTURE_CODE")))
        assertFalse("Raw unknown code must not appear verbatim", "SOME_FUTURE_CODE" in prompt)
        assertTrue("Fallback text should appear", "Some future code" in prompt)
    }

    // --- Qualitative weight signal ---

    @Test
    fun `weight trending down is qualitative`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(weightTrend = -0.35))
        assertTrue("Expected 'trending down'", "Weight: trending down" in prompt)
        assertFalse("Raw number must not appear", "-0.35" in prompt)
    }

    @Test
    fun `weight stable is qualitative`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(weightTrend = 0.05))
        assertTrue("Expected stable label", "Weight: stable" in prompt)
        assertFalse("Raw number must not appear", "0.05" in prompt)
    }

    @Test
    fun `weight trending up is qualitative`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(weightTrend = 0.35))
        assertTrue("Expected 'trending up'", "Weight: trending up" in prompt)
        assertFalse("Raw number must not appear", "0.35" in prompt)
    }

    // --- Qualitative waist signal ---

    @Test
    fun `waist stable is qualitative`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(waistTrend = 0.02))
        assertTrue("Expected stable label", "Waist: stable" in prompt)
        assertFalse("Raw number must not appear", "0.02" in prompt)
    }

    @Test
    fun `waist trending up is qualitative`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(waistTrend = 0.30))
        assertTrue("Expected 'trending up'", "Waist: trending up" in prompt)
        assertFalse("Raw number must not appear", "0.30" in prompt)
    }

    // --- Qualitative performance signal ---

    @Test
    fun `performance DOWN maps to declining`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(performanceTrend = PerformanceTrend.DOWN))
        assertTrue("Expected 'declining'", "Performance: declining" in prompt)
        assertFalse("Raw enum label must not appear", "Performance: DOWN" in prompt)
    }

    @Test
    fun `performance UP maps to improving`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(performanceTrend = PerformanceTrend.UP))
        assertTrue("Expected 'improving'", "Performance: improving" in prompt)
        assertFalse("Raw enum label must not appear", "Performance: UP" in prompt)
    }

    @Test
    fun `performance UNKNOWN maps to no data`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(performanceTrend = PerformanceTrend.UNKNOWN))
        assertTrue("Expected 'no data'", "Performance: no data" in prompt)
    }

    // --- Qualitative recovery signal ---

    @Test
    fun `recovery POOR maps to poor`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(recoveryTrend = RecoveryTrend.POOR))
        assertTrue("Expected 'poor'", "Recovery: poor" in prompt)
        assertFalse("Raw enum label must not appear", "Recovery: POOR" in prompt)
    }

    @Test
    fun `recovery GOOD maps to good`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(recoveryTrend = RecoveryTrend.GOOD))
        assertTrue("Expected 'good'", "Recovery: good" in prompt)
        assertFalse("Raw enum label must not appear", "Recovery: GOOD" in prompt)
    }

    // --- Qualitative adherence signal ---

    @Test
    fun `adherence high is qualitative`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(adherence = 91.0))
        assertTrue("Expected 'high'", "Adherence: high" in prompt)
        assertFalse("Raw percentage must not appear", "91" in prompt)
    }

    @Test
    fun `adherence moderate is qualitative`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(adherence = 78.0))
        assertTrue("Expected 'moderate'", "Adherence: moderate" in prompt)
        assertFalse("Raw percentage must not appear", "78" in prompt)
    }

    @Test
    fun `adherence low is qualitative`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(adherence = 60.0))
        assertTrue("Expected 'low'", "Adherence: low" in prompt)
        assertFalse("Raw percentage must not appear", "60" in prompt)
    }

    // --- weeksSincePhaseStart ---

    @Test
    fun `weeksSincePhaseStart default 4 is omitted from prompt`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(weeksSincePhaseStart = 4))
        assertFalse("Default fallback weeks must not appear", "Weeks in current phase" in prompt)
    }

    @Test
    fun `weeksSincePhaseStart non-default is included in prompt`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(weeksSincePhaseStart = 3))
        assertTrue("Real week count should appear", "Weeks in current phase: 3" in prompt)
    }

    @Test
    fun `weeksSincePhaseStart week 1 is included in prompt`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(weeksSincePhaseStart = 1))
        assertTrue("Week 1 should appear", "Weeks in current phase: 1" in prompt)
    }

    // --- Structural requirements ---

    @Test
    fun `prompt contains few-shot example`() {
        val prompt = builder.buildWeeklySummaryPrompt(context())
        assertTrue("Expected few-shot example header", "Example output" in prompt)
    }

    @Test
    fun `prompt includes result summary for grounding`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(summary = "Keep logging and hold."))
        assertTrue("Expected summary text in prompt", "Keep logging and hold." in prompt)
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
    fun `prompt contains calorie target`() {
        val prompt = builder.buildWeeklySummaryPrompt(context(targetCalories = 2550))
        assertTrue("Expected calorie target in prompt", "2550" in prompt || "2,550" in prompt)
    }
}
