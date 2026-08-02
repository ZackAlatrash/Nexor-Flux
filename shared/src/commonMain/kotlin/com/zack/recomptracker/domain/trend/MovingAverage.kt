package com.zack.recomptracker.domain.trend

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

data class MeasurementPoint(
    val date: LocalDate,
    val value: Double?,
)

data class AveragePoint(
    val date: LocalDate,
    val value: Double,
)

object MovingAverage {
    fun calculate(points: List<MeasurementPoint>, windowSize: Int): List<AveragePoint> {
        if (windowSize <= 0) return emptyList()

        val sorted = points.sortedBy { it.date }
        return sorted.mapNotNull { point ->
            val value = point.value ?: return@mapNotNull null
            val windowStart = point.date.minus(windowSize - 1, DateTimeUnit.DAY)
            val values = sorted
                .filter { candidate ->
                    candidate.value != null &&
                        candidate.date >= windowStart &&
                        candidate.date <= point.date
                }
                .mapNotNull { it.value }

            if (values.isEmpty()) null else AveragePoint(point.date, values.average())
        }
    }
}
