package com.zack.recomptracker.ai.harness

import com.zack.recomptracker.ai.InsightContext
import com.zack.recomptracker.ai.ProgressInsightContext
import com.zack.recomptracker.ai.RecoveryInsightContext
import com.zack.recomptracker.ai.RestOfDayInsightContext
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextBaselineFieldsTest {

    @Test
    fun `weekly context carries desired rate and prior target`() {
        val c = InsightContext(
            result = AdjustmentResult(AdjustmentVerdict.HOLD, 0, emptyList(), "s"),
            input = AdjustmentInput(14, 90.0, 3, -0.3, 0.0, PerformanceTrend.STABLE, RecoveryTrend.GOOD),
            targetCalories = 2500,
            targetProteinG = 165,
            desiredWeeklyRateKg = -0.4,
            priorTargetCalories = 2600,
        )
        assertEquals(-0.4, c.desiredWeeklyRateKg!!, 0.0001)
        assertEquals(2600, c.priorTargetCalories)
    }

    @Test
    fun `progress context carries prior-window trends`() {
        val c = ProgressInsightContext(28, -0.3, 0.0, 0.4, 90.0, 6, 6, priorWeightTrendKgPerWeek = -0.1)
        assertEquals(-0.1, c.priorWeightTrendKgPerWeek!!, 0.0001)
    }

    @Test
    fun `recovery context carries personal averages`() {
        val c = RecoveryInsightContext(5.0, 3, 7, 8, true, avgSleepHours = 7.5, avgEnergyScore = 6)
        assertEquals(7.5, c.avgSleepHours!!, 0.0001)
        assertEquals(6, c.avgEnergyScore)
    }

    @Test
    fun `rest-of-day context carries fraction-of-day-elapsed`() {
        val c = RestOfDayInsightContext(1420, 2200, 2050, 2350, 72.0, 165, 2, fractionOfDayElapsed = 0.6)
        assertEquals(0.6, c.fractionOfDayElapsed!!, 0.0001)
    }
}
