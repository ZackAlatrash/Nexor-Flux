package com.zack.recomptracker.domain.review

import com.zack.recomptracker.domain.review.BriefingPhase
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeeklyReviewComputerTest {

    private val computer = WeeklyReviewComputer()

    private fun input(
        days: Int = 14,
        weight: Double = 0.0,
        waist: Double = -0.2,
        adherence: Double = 90.0,
        perf: PerformanceTrend = PerformanceTrend.UP,
        recovery: RecoveryTrend = RecoveryTrend.GOOD,
    ) = AdjustmentInput(
        daysLogged = days, adherencePercent = adherence, weeksSincePhaseStart = 4,
        weightTrendKgPerWeek = weight, waistTrendCmPerWeek = waist,
        performanceTrend = perf, recoveryTrend = recovery,
    )

    private fun result(
        verdict: AdjustmentVerdict = AdjustmentVerdict.HOLD,
        change: Int = 0,
    ) = AdjustmentResult(verdict, change, listOf("X"), "summary")

    @Test
    fun `full phase with reduce verdict yields apply target`() {
        val data = computer.build("2026-06-08", input(), result(AdjustmentVerdict.REDUCE_CALORIES, -100), 2550)
        assertEquals(BriefingPhase.FULL, data.phase)
        assertEquals(2450, data.applyTargetCalories)
        assertEquals(5, data.signals.size)
    }

    @Test
    fun `early phase under 14 days has null apply target`() {
        val data = computer.build("2026-06-08", input(days = 9), result(AdjustmentVerdict.WAIT_FOR_DATA, 0), 2550)
        assertEquals(BriefingPhase.EARLY, data.phase)
        assertNull(data.applyTargetCalories)
    }

    @Test
    fun `hold verdict is full phase but no apply target`() {
        val data = computer.build("2026-06-08", input(), result(AdjustmentVerdict.HOLD, 0), 2550)
        assertEquals(BriefingPhase.FULL, data.phase)
        assertNull(data.applyTargetCalories)
    }

    @Test
    fun `signature is stable for identical inputs`() {
        val a = computer.signature(computer.build("2026-06-08", input(), result(), 2550))
        val b = computer.signature(computer.build("2026-06-08", input(), result(), 2550))
        assertEquals(a, b)
    }

    @Test
    fun `signature ignores sub-bucket wiggle`() {
        val a = computer.signature(computer.build("2026-06-08", input(waist = -0.20), result(), 2550))
        val b = computer.signature(computer.build("2026-06-08", input(waist = -0.22), result(), 2550))
        assertEquals(a, b)
    }

    @Test
    fun `signature changes when the week target changes`() {
        // Proves currentTargetCalories participates in the signature: this is why
        // Task 7 passing the week-end HISTORICAL target (not the current plan) keeps
        // a past week's signature stable across later plan changes.
        val a = computer.signature(computer.build("2026-06-22", input(), result(), 2500))
        val b = computer.signature(computer.build("2026-06-22", input(), result(), 2600))
        assertNotEquals(a, b)
    }

    @Test
    fun `signature changes when verdict changes`() {
        val a = computer.signature(computer.build("2026-06-08", input(), result(AdjustmentVerdict.HOLD, 0), 2550))
        val b = computer.signature(computer.build("2026-06-08", input(), result(AdjustmentVerdict.REDUCE_CALORIES, -100), 2550))
        assertNotEquals(a, b)
    }

    @Test
    fun `14 days with WAIT_FOR_DATA is still EARLY phase`() {
        val data = computer.build("2026-06-08", input(days = 14), result(AdjustmentVerdict.WAIT_FOR_DATA), 2550)
        assertEquals(BriefingPhase.EARLY, data.phase)
        assertNull(data.applyTargetCalories)
    }
}
