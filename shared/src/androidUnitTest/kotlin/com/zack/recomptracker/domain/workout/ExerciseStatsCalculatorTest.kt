package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseStatsCalculatorTest {

    private fun p(date: String, reps: Int, weight: Double?) = ExerciseHistoryPoint(date, reps, weight, null)

    @Test fun emptyHistoryHasNoData() {
        val s = ExerciseStatsCalculator.calculate(emptyList())
        assertFalse(s.hasData)
        assertNull(s.bestOneRepMax)
        assertTrue(s.topSetSeries.isEmpty())
        assertTrue(s.recentSessions.isEmpty())
    }

    @Test fun heaviestSetAndMaxRepsAndLastDate() {
        val history = listOf(
            p("2026-06-10", 10, 12.0),
            p("2026-06-10", 8, 15.0),
            p("2026-06-17", 12, 14.0),
        )
        val s = ExerciseStatsCalculator.calculate(history)
        assertTrue(s.hasData)
        assertEquals(15.0, s.heaviestWeightKg!!, 0.0001)
        assertEquals(8, s.heaviestReps)
        assertEquals(12, s.maxReps)
        assertEquals("2026-06-17", s.lastPerformedDate)
    }

    @Test fun bestOneRepMaxUsesEpleyAcrossHistory() {
        val history = listOf(p("2026-06-10", 8, 15.0), p("2026-06-17", 12, 14.0))
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals(19.6, s.bestOneRepMax!!, 0.01)
    }

    @Test fun topSetSeriesIsMaxWeightPerDayAscending() {
        val history = listOf(
            p("2026-06-17", 12, 14.0),
            p("2026-06-10", 10, 12.0),
            p("2026-06-10", 8, 15.0),
        )
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals(listOf("2026-06-10", "2026-06-17"), s.topSetSeries.map { it.date })
        assertEquals(15.0, s.topSetSeries[0].value, 0.0001)
        assertEquals(14.0, s.topSetSeries[1].value, 0.0001)
    }

    @Test fun bestDayVolumeIsMaxOfPerDayVolume() {
        val history = listOf(p("2026-06-10", 10, 12.0), p("2026-06-10", 8, 15.0), p("2026-06-17", 12, 14.0))
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals(240.0, s.bestDayVolume!!, 0.0001)
    }

    @Test fun frequencyIsSessionsPerWeekOverSpan() {
        val history = listOf(p("2026-06-01", 5, 10.0), p("2026-06-08", 5, 10.0), p("2026-06-15", 5, 10.0))
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals(1.5, s.sessionsPerWeek!!, 0.01)
    }

    @Test fun frequencyWithinOneWeekCountsAsThatManyPerWeek() {
        val history = listOf(p("2026-06-01", 5, 10.0), p("2026-06-03", 5, 10.0))
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals(2.0, s.sessionsPerWeek!!, 0.01)
    }

    @Test fun recentSessionsNewestFirstWithVolume() {
        val history = listOf(p("2026-06-10", 10, 12.0), p("2026-06-10", 8, 15.0), p("2026-06-17", 12, 14.0))
        val s = ExerciseStatsCalculator.calculate(history)
        assertEquals("2026-06-17", s.recentSessions.first().date)
        assertEquals(2, s.recentSessions.first { it.date == "2026-06-10" }.sets.size)
        assertEquals(240.0, s.recentSessions.first { it.date == "2026-06-10" }.volume, 0.0001)
    }

    @Test fun bodyweightOnlyHistoryHasNullStrengthStatsButHasData() {
        val history = listOf(p("2026-06-10", 12, null), p("2026-06-17", 15, null))
        val s = ExerciseStatsCalculator.calculate(history)
        assertTrue(s.hasData)
        assertNull(s.bestOneRepMax)
        assertNull(s.heaviestWeightKg)
        assertEquals(15, s.maxReps)
        assertNotNull(s.sessionsPerWeek)
    }
}
