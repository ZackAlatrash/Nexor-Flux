package com.zack.recomptracker.domain.trend

import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

data class PerformancePoint(
    val date: LocalDate,
    val weight: Double,
    val reps: Int,
    val sets: Int,
)

data class RecoveryPoint(
    val date: LocalDate,
    val sleepHours: Double?,
    val energyScore: Int?,
    val sorenessScore: Int?,
)

class TrendCalculator {
    fun trendPerWeek(points: List<MeasurementPoint>): Double {
        val usable = points
            .filter { it.value != null }
            .sortedBy { it.date }

        if (usable.size < 2) return 0.0

        val start = usable.first().date
        val xs = usable.map { start.daysUntil(it.date).toDouble() }
        val ys = usable.map { requireNotNull(it.value) }
        val xMean = xs.average()
        val yMean = ys.average()
        val denominator = xs.sumOf { (it - xMean) * (it - xMean) }
        if (denominator == 0.0) return 0.0

        val numerator = xs.indices.sumOf { index -> (xs[index] - xMean) * (ys[index] - yMean) }
        return (numerator / denominator) * 7.0
    }

    fun performanceTrend(points: List<PerformancePoint>): PerformanceTrend {
        val usable = points.sortedBy { it.date }
        if (usable.size < 2) return PerformanceTrend.UNKNOWN

        val first = usable.first().estimatedOneRepMax()
        val last = usable.last().estimatedOneRepMax()
        val changePercent = ((last - first) / first) * 100.0

        return when {
            changePercent >= 2.0 -> PerformanceTrend.UP
            changePercent <= -2.0 -> PerformanceTrend.DOWN
            else -> PerformanceTrend.STABLE
        }
    }

    fun recoveryTrend(points: List<RecoveryPoint>): RecoveryTrend {
        if (points.isEmpty()) return RecoveryTrend.UNKNOWN

        val sleepAverage = points.mapNotNull { it.sleepHours }.averageOrNull()
        val energyAverage = points.mapNotNull { it.energyScore }.map { it.toDouble() }.averageOrNull()
        val sorenessAverage = points.mapNotNull { it.sorenessScore }.map { it.toDouble() }.averageOrNull()

        if (
            (sleepAverage != null && sleepAverage < 6.0) ||
            (energyAverage != null && energyAverage < 5.0) ||
            (sorenessAverage != null && sorenessAverage > 7.0)
        ) {
            return RecoveryTrend.POOR
        }

        if (
            (sleepAverage == null || sleepAverage >= 7.0) &&
            (energyAverage == null || energyAverage >= 7.0) &&
            (sorenessAverage == null || sorenessAverage <= 5.0)
        ) {
            return RecoveryTrend.GOOD
        }

        return RecoveryTrend.OK
    }

    private fun PerformancePoint.estimatedOneRepMax(): Double {
        val bestSetWeight = weight
        return bestSetWeight * (1.0 + reps.coerceAtLeast(1) / 30.0)
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
