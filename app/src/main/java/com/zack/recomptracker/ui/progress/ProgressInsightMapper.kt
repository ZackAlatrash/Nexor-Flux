package com.zack.recomptracker.ui.progress

import com.zack.recomptracker.ai.ProgressInsightContext

/**
 * Pure mapper: assembles a [ProgressInsightContext] from values the ProgressViewModel already
 * computed. Trends (kg-or-cm per week) are passed in pre-computed by the ViewModel via the shared
 * date-based TrendCalculator — the mapper no longer derives them from point count (P1-12). A trend
 * is null when its series had fewer than two logged points (and doesn't count toward sufficiency).
 */
fun buildProgressInsightContext(
    rangeDays: Int,
    weightTrendKgPerWeek: Double?,
    waistTrendCmPerWeek: Double?,
    liftTrendKgPerWeek: Double?,
    weightPointCount: Int,
    waistPointCount: Int,
    adherencePercent: Float?,
    trainingSessionsPerWeek: Double? = null,
    weeklyGymSessionsTarget: Int? = null,
): ProgressInsightContext = ProgressInsightContext(
    rangeDays = rangeDays,
    weightTrendKgPerWeek = weightTrendKgPerWeek,
    waistTrendCmPerWeek = waistTrendCmPerWeek,
    liftTrendKgPerWeek = liftTrendKgPerWeek,
    adherencePercent = adherencePercent?.toDouble(),
    weightPointCount = weightPointCount,
    waistPointCount = waistPointCount,
    trainingSessionsPerWeek = trainingSessionsPerWeek,
    weeklyGymSessionsTarget = weeklyGymSessionsTarget,
)
