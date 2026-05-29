package com.zack.recomptracker.domain

import com.zack.recomptracker.domain.adjustment.AdjustmentEngine
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentEngineTest {
    private val engine = AdjustmentEngine()

    @Test
    fun fewerThanFourteenLoggedDaysWaitsForData() {
        val result = engine.evaluate(baseInput(daysLogged = 13))

        assertEquals(AdjustmentVerdict.WAIT_FOR_DATA, result.verdict)
        assertEquals(0, result.recommendedCalorieChange)
        assertTrue(result.reasonCodes.contains("INSUFFICIENT_DATA"))
    }

    @Test
    fun lowAdherenceWaitsEvenWhenTrendLooksClear() {
        val result = engine.evaluate(
            baseInput(
                adherencePercent = 70.0,
                weightTrendKgPerWeek = 0.35,
                waistTrendCmPerWeek = 0.4,
            ),
        )

        assertEquals(AdjustmentVerdict.WAIT_FOR_DATA, result.verdict)
        assertTrue(result.reasonCodes.contains("LOW_ADHERENCE"))
    }

    @Test
    fun stableWeightStableWaistAndStablePerformanceHoldsCalories() {
        val result = engine.evaluate(
            baseInput(
                weightTrendKgPerWeek = 0.05,
                waistTrendCmPerWeek = 0.0,
                performanceTrend = PerformanceTrend.STABLE,
            ),
        )

        assertEquals(AdjustmentVerdict.HOLD, result.verdict)
        assertTrue(result.reasonCodes.contains("MAINTENANCE_TREND"))
    }

    @Test
    fun fallingWeightWithPoorRecoveryIncreasesCalories() {
        val result = engine.evaluate(
            baseInput(
                weightTrendKgPerWeek = -0.35,
                recoveryTrend = RecoveryTrend.POOR,
            ),
        )

        assertEquals(AdjustmentVerdict.INCREASE_CALORIES, result.verdict)
        assertTrue(result.recommendedCalorieChange > 0)
        assertTrue(result.reasonCodes.contains("LOSING_WITH_POOR_RECOVERY"))
    }

    @Test
    fun risingWeightAndRisingWaistReducesCalories() {
        val result = engine.evaluate(
            baseInput(
                weightTrendKgPerWeek = 0.42,
                waistTrendCmPerWeek = 0.35,
            ),
        )

        assertEquals(AdjustmentVerdict.REDUCE_CALORIES, result.verdict)
        assertTrue(result.recommendedCalorieChange < 0)
        assertTrue(result.reasonCodes.contains("GAINING_WITH_WAIST_INCREASE"))
    }

    @Test
    fun earlyScaleJumpWithStableWaistHoldsCalories() {
        val result = engine.evaluate(
            baseInput(
                weeksSincePhaseStart = 1,
                weightTrendKgPerWeek = 0.45,
                waistTrendCmPerWeek = 0.0,
                performanceTrend = PerformanceTrend.UP,
            ),
        )

        assertEquals(AdjustmentVerdict.HOLD, result.verdict)
        assertEquals(0, result.recommendedCalorieChange)
        assertTrue(result.reasonCodes.contains("EARLY_SCALE_JUMP"))
    }

    private fun baseInput(
        daysLogged: Int = 18,
        adherencePercent: Double = 90.0,
        weeksSincePhaseStart: Int = 4,
        weightTrendKgPerWeek: Double = 0.0,
        waistTrendCmPerWeek: Double = 0.0,
        performanceTrend: PerformanceTrend = PerformanceTrend.STABLE,
        recoveryTrend: RecoveryTrend = RecoveryTrend.OK,
    ) = AdjustmentInput(
        daysLogged = daysLogged,
        adherencePercent = adherencePercent,
        weeksSincePhaseStart = weeksSincePhaseStart,
        weightTrendKgPerWeek = weightTrendKgPerWeek,
        waistTrendCmPerWeek = waistTrendCmPerWeek,
        performanceTrend = performanceTrend,
        recoveryTrend = recoveryTrend,
    )
}
