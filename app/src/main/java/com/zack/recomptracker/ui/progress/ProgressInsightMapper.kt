package com.zack.recomptracker.ui.progress

import com.zack.recomptracker.ai.ProgressInsightContext

/**
 * Pure mapper: builds a [ProgressInsightContext] from the raw per-metric value series the
 * ProgressViewModel already computes for its charts. Trends are kg-or-cm per week; a series
 * with fewer than two points yields a null trend (and does not count toward sufficiency).
 */
fun buildProgressInsightContext(
    rangeDays: Int,
    weightValues: List<Float>,
    waistValues: List<Float>,
    liftValues: List<Float>,
    adherencePercent: Float?,
): ProgressInsightContext = ProgressInsightContext(
    rangeDays = rangeDays,
    weightTrendKgPerWeek = trendPerWeek(weightValues),
    waistTrendCmPerWeek = trendPerWeek(waistValues),
    liftTrendKgPerWeek = trendPerWeek(liftValues),
    adherencePercent = adherencePercent?.toDouble(),
    weightPointCount = weightValues.size,
    waistPointCount = waistValues.size,
)

private fun trendPerWeek(values: List<Float>): Double? {
    if (values.size < 2) return null
    val weeks = (values.size - 1).toFloat() / 7f
    return if (weeks > 0f) ((values.last() - values.first()) / weeks).toDouble() else null
}
