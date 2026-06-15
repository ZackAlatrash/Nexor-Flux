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
}
