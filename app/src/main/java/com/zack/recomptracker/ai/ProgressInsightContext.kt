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
) {
    val hasSufficientData: Boolean
        get() = weightPointCount >= 2 || waistPointCount >= 2

    fun key(): String {
        val w = weightTrendKgPerWeek?.let { (it * 10).roundToInt() } ?: 0
        val wa = waistTrendCmPerWeek?.let { (it * 10).roundToInt() } ?: 0
        val l = liftTrendKgPerWeek?.let { (it * 10).roundToInt() } ?: 0
        val a = adherencePercent?.roundToInt() ?: -1
        return "$rangeDays|$w|$wa|$l|$a"
    }
}
