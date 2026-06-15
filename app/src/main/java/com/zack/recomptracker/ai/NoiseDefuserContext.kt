package com.zack.recomptracker.ai

import kotlin.math.roundToInt

/**
 * Defuses a scary daily scale reading that contradicts the smoothed weight trend
 * ("up 0.9 kg today, but your 10-day trend is flat — that's water").
 *
 * [todayDeltaKg] is today's day-over-day jump; [smoothedTrendKgPerWeek] is the real direction.
 * Whether the card actually fires is decided by [InsightGate.shouldFireNoiseDefuser] (the jump
 * must contradict, or dwarf, the trend) — not every daily reading deserves a card.
 */
data class NoiseDefuserContext(
    val todayWeightKg: Double,
    val priorWeightKg: Double,
    val smoothedTrendKgPerWeek: Double,
) {
    val todayDeltaKg: Double get() = todayWeightKg - priorWeightKg

    val hasSufficientData: Boolean get() = true

    fun key(): String =
        "${(todayWeightKg * 10).roundToInt()}|${(smoothedTrendKgPerWeek * 100).roundToInt()}"
}
