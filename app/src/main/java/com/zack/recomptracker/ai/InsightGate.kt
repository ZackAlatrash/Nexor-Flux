package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import kotlin.math.abs

/**
 * Decides whether a card has anything worth saying (doctrine rule 7: "provide nothing").
 * Pure and deterministic — runs BEFORE any cloud call, so silence costs zero tokens.
 */
object InsightGate {

    private const val LOW_ADHERENCE = 75.0

    /**
     * Weekly summary fires when there is a real decision or problem to surface:
     * any non-HOLD verdict, low adherence, or a meaningful waist drift. A HOLD with good
     * adherence and a stable waist is "on track" — stay quiet.
     */
    fun shouldFireWeekly(context: InsightContext): Boolean {
        if (context.result.verdict != AdjustmentVerdict.HOLD) return true
        if (context.input.adherencePercent < LOW_ADHERENCE) return true
        if (abs(context.input.waistTrendCmPerWeek) >= 0.25) return true
        return false
    }

    private const val NOISE_JUMP_KG = 0.6
    private const val NOISE_FLAT_TREND_KG_WK = 0.10

    /**
     * The noise-defuser fires only when today's day-over-day jump is large AND it contradicts the
     * smoothed trend — either pointing the opposite way, or jumping while the trend is essentially
     * flat. A jump that agrees with the trend is real signal, not noise, so stay quiet.
     */
    fun shouldFireNoiseDefuser(context: NoiseDefuserContext): Boolean {
        val jump = context.todayDeltaKg
        if (abs(jump) < NOISE_JUMP_KG) return false
        val trend = context.smoothedTrendKgPerWeek
        if (abs(trend) < NOISE_FLAT_TREND_KG_WK) return true
        // Opposite signs → today contradicts the trend.
        return (jump > 0) != (trend > 0)
    }
}
