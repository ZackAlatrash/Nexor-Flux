package com.zack.recomptracker.ai

import kotlin.math.roundToInt

data class ProgressInsightContext(
    val rangeDays: Int,
    val weightTrendKgPerWeek: Double?,
    val waistTrendCmPerWeek: Double?,
    val liftTrendKgPerWeek: Double?,
    val adherencePercent: Double?,
    val weightPointCount: Int,
    val waistPointCount: Int,
    /** Same trend over the PRIOR window of equal length — lets the card say "accelerating". */
    val priorWeightTrendKgPerWeek: Double? = null,
) {
    val hasSufficientData: Boolean
        get() = weightPointCount >= 2 || waistPointCount >= 2

    /** Quantized to 0.1 units so trivial float drift doesn't re-trigger generation. */
    fun key(): String {
        val w = weightTrendKgPerWeek?.let { (it * 10).roundToInt() }?.toString() ?: "n"
        val wa = waistTrendCmPerWeek?.let { (it * 10).roundToInt() }?.toString() ?: "n"
        val l = liftTrendKgPerWeek?.let { (it * 10).roundToInt() }?.toString() ?: "n"
        val a = adherencePercent?.roundToInt()?.toString() ?: "n"
        return "$rangeDays|$w|$wa|$l|$a"
    }
}
