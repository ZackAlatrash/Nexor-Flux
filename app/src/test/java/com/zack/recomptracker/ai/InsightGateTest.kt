package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightGateTest {

    private fun weekly(verdict: AdjustmentVerdict, weight: Double, waist: Double, adherence: Double) =
        InsightContext(
            result = AdjustmentResult(verdict, 0, emptyList(), "s"),
            input = AdjustmentInput(14, adherence, 3, weight, waist, PerformanceTrend.STABLE, RecoveryTrend.GOOD),
            targetCalories = 2500, targetProteinG = 165,
        )

    @Test
    fun `holds and stays quiet when on-plan and flat`() {
        assertFalse(InsightGate.shouldFireWeekly(weekly(AdjustmentVerdict.HOLD, -0.38, 0.0, 92.0)))
    }

    @Test
    fun `fires when the verdict changes calories`() {
        assertTrue(InsightGate.shouldFireWeekly(weekly(AdjustmentVerdict.REDUCE_CALORIES, 0.3, 0.4, 80.0)))
    }

    @Test
    fun `fires on a hold when adherence is low (worth addressing)`() {
        assertTrue(InsightGate.shouldFireWeekly(weekly(AdjustmentVerdict.HOLD, -0.1, 0.0, 60.0)))
    }
}
