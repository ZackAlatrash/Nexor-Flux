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

    // --- Noise-defuser gate ---

    private fun noise(today: Double, prior: Double, trend: Double) =
        NoiseDefuserContext(todayWeightKg = today, priorWeightKg = prior, smoothedTrendKgPerWeek = trend)

    @Test
    fun `noise-defuser fires on a big jump against a flat trend`() {
        // +0.9 kg today, trend essentially flat -> water/food, defuse.
        assertTrue(InsightGate.shouldFireNoiseDefuser(noise(81.0, 80.1, -0.02)))
    }

    @Test
    fun `noise-defuser fires when today contradicts the trend direction`() {
        // Up 0.7 kg today but the smoothed trend is downward -> contradicts, defuse.
        assertTrue(InsightGate.shouldFireNoiseDefuser(noise(80.7, 80.0, -0.30)))
    }

    @Test
    fun `noise-defuser stays quiet when the jump agrees with the trend`() {
        // Up 0.7 kg today and the trend is also up -> real signal, not noise.
        assertFalse(InsightGate.shouldFireNoiseDefuser(noise(80.7, 80.0, 0.30)))
    }

    @Test
    fun `noise-defuser stays quiet for a small daily wobble`() {
        // Only +0.2 kg -> below the jump threshold, nothing to defuse.
        assertFalse(InsightGate.shouldFireNoiseDefuser(noise(80.2, 80.0, -0.30)))
    }
}
