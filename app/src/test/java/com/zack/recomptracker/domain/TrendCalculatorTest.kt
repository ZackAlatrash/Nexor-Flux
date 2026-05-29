package com.zack.recomptracker.domain

import com.zack.recomptracker.domain.adjustment.PerformanceTrend
import com.zack.recomptracker.domain.adjustment.RecoveryTrend
import com.zack.recomptracker.domain.trend.MeasurementPoint
import com.zack.recomptracker.domain.trend.MovingAverage
import com.zack.recomptracker.domain.trend.PerformancePoint
import com.zack.recomptracker.domain.trend.RecoveryPoint
import com.zack.recomptracker.domain.trend.TrendCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendCalculatorTest {
    private val calculator = TrendCalculator()

    @Test
    fun movingAverageIgnoresMissingMeasurements() {
        val start = LocalDate.of(2026, 5, 1)
        val points = listOf(
            MeasurementPoint(start, 80.0),
            MeasurementPoint(start.plusDays(1), null),
            MeasurementPoint(start.plusDays(2), 81.0),
        )

        val averages = MovingAverage.calculate(points, windowSize = 3)

        assertEquals(2, averages.size)
        assertEquals(80.5, averages.last().value, 0.001)
    }

    @Test
    fun weeklyTrendUsesSlopeAcrossAvailableMeasurements() {
        val start = LocalDate.of(2026, 5, 1)
        val points = (0..6).map { offset ->
            MeasurementPoint(start.plusDays(offset.toLong()), 80.0 + (offset * 0.1))
        }

        val trend = calculator.trendPerWeek(points)

        assertEquals(0.7, trend, 0.001)
    }

    @Test
    fun trendNeedsAtLeastTwoMeasurements() {
        val trend = calculator.trendPerWeek(listOf(MeasurementPoint(LocalDate.of(2026, 5, 1), 80.0)))

        assertEquals(0.0, trend, 0.001)
    }

    @Test
    fun performanceTrendImprovesWhenEstimatedOneRepMaxRises() {
        val start = LocalDate.of(2026, 5, 1)
        val points = listOf(
            PerformancePoint(start, weight = 100.0, reps = 5, sets = 3),
            PerformancePoint(start.plusDays(7), weight = 105.0, reps = 5, sets = 3),
        )

        assertEquals(PerformanceTrend.UP, calculator.performanceTrend(points))
    }

    @Test
    fun recoveryTrendIsPoorWhenSleepAndEnergyAreLowAndSorenessIsHigh() {
        val start = LocalDate.of(2026, 5, 1)
        val points = listOf(
            RecoveryPoint(start, sleepHours = 5.5, energyScore = 3, sorenessScore = 8),
            RecoveryPoint(start.plusDays(1), sleepHours = 5.0, energyScore = 4, sorenessScore = 8),
        )

        assertEquals(RecoveryTrend.POOR, calculator.recoveryTrend(points))
    }
}
