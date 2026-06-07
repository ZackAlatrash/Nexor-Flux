package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AiInsightStateTest {

    @Test
    fun `resultKey is stable for identical AdjustmentResult`() {
        val result = AdjustmentResult(
            verdict = AdjustmentVerdict.HOLD,
            recommendedCalorieChange = 0,
            reasonCodes = listOf("MAINTENANCE_TREND"),
            summary = "Stable.",
        )
        assertEquals(result.key(), result.key())
    }

    @Test
    fun `resultKey differs when verdict differs`() {
        val hold = AdjustmentResult(
            verdict = AdjustmentVerdict.HOLD,
            recommendedCalorieChange = 0,
            reasonCodes = listOf("MAINTENANCE_TREND"),
            summary = "Stable.",
        )
        val increase = hold.copy(verdict = AdjustmentVerdict.INCREASE_CALORIES, recommendedCalorieChange = 150)
        assertNotEquals(hold.key(), increase.key())
    }

    @Test
    fun `resultKey differs when reasonCodes differ`() {
        val a = AdjustmentResult(
            verdict = AdjustmentVerdict.HOLD,
            recommendedCalorieChange = 0,
            reasonCodes = listOf("MAINTENANCE_TREND"),
            summary = "Stable.",
        )
        val b = a.copy(reasonCodes = listOf("NO_CLEAR_CHANGE_SIGNAL"))
        assertNotEquals(a.key(), b.key())
    }
}
