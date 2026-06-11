package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import com.zack.recomptracker.domain.review.WeeklyReviewComputer
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyBriefingPromptBuilderTest {

    private fun data() = WeeklyReviewComputer().build(
        "2026-06-08",
        AdjustmentInput(14, 88.0, 4, 0.0, -0.2, PerformanceTrend.UP, RecoveryTrend.GOOD),
        AdjustmentResult(AdjustmentVerdict.HOLD, 0, listOf("MAINTENANCE_TREND"), "maintenance"),
        2550,
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
}
