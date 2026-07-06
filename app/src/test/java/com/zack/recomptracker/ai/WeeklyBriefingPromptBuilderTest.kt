package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import com.zack.recomptracker.domain.review.SignalDirection
import com.zack.recomptracker.domain.review.WeeklyReviewComputer
import com.zack.recomptracker.domain.review.WeeklyTraining
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyBriefingPromptBuilderTest {

    private fun data(training: WeeklyTraining? = null) = WeeklyReviewComputer().build(
        "2026-06-08",
        AdjustmentInput(14, 88.0, 4, 0.0, -0.2, PerformanceTrend.UP, RecoveryTrend.GOOD),
        AdjustmentResult(AdjustmentVerdict.HOLD, 0, listOf("MAINTENANCE_TREND"), "maintenance"),
        2550,
        training = training,
    )

    @Test
    fun `prompt includes the deterministic verdict and signal ids`() {
        val p = WeeklyBriefingPromptBuilder().build(data())
        assertTrue(p.contains("Hold calories"))
        assertTrue(p.contains("\"weight\""))
        assertTrue(p.contains("\"waist\""))
        assertTrue(p.contains("\"recovery\""))
    }

    @Test
    fun `prompt forbids inventing numbers and asks for json keys`() {
        val p = WeeklyBriefingPromptBuilder().build(data())
        assertTrue(p.contains("do not change") || p.contains("Do not change"))
        assertTrue(p.contains("interpretations"))
        assertTrue(p.contains("watch_next"))
    }

    @Test
    fun `training summary is appended as supporting context when present`() {
        val training = WeeklyTraining(3, "Bench", SignalDirection.UP, "3 training sessions this week; top lift Bench is trending up.")
        val p = WeeklyBriefingPromptBuilder().build(data(training))
        assertTrue(p.contains("Training this week (SUPPORTING context"))
        assertTrue(p.contains("top lift Bench is trending up"))
    }

    @Test
    fun `training line is omitted when no training was logged`() {
        val p = WeeklyBriefingPromptBuilder().build(data(training = null))
        assertFalse(p.contains("Training this week"))
    }

    @Test
    fun `coach note is appended as supporting colour only`() {
        val note = WeeklyCoachNote("Your steps fell this week.", "avg 6.2k vs 9.1k")
        val p = WeeklyBriefingPromptBuilder().build(data(), note)
        assertTrue(p.contains("What the coach noticed this week"))
        assertTrue(p.contains("SUPPORTING colour only"))
        assertTrue(p.contains("Your steps fell this week."))
    }
}
