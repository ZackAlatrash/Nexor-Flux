package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutProgressAnalyzerTest {

    private fun set(reps: Int, weight: Double?, completed: Boolean = true, n: Int = 1) =
        SessionSet(id = 0, setNumber = n, reps = reps, weightKg = weight, rir = null, completed = completed)

    @Test
    fun `set volume multiplies reps and weight`() {
        assertEquals(600.0, WorkoutProgressAnalyzer.setVolume(reps = 10, weightKg = 60.0), 0.0001)
    }

    @Test
    fun `set volume treats null weight as zero`() {
        assertEquals(0.0, WorkoutProgressAnalyzer.setVolume(reps = 12, weightKg = null), 0.0001)
    }

    @Test
    fun `estimated one rep max uses Epley`() {
        // 100 * (1 + 5/30) = 116.6667
        assertEquals(116.6667, WorkoutProgressAnalyzer.estimatedOneRepMax(reps = 5, weightKg = 100.0)!!, 0.001)
    }

    @Test
    fun `estimated one rep max is null for bodyweight`() {
        assertNull(WorkoutProgressAnalyzer.estimatedOneRepMax(reps = 5, weightKg = null))
    }

    @Test
    fun `estimated one rep max is null for zero reps`() {
        assertNull(WorkoutProgressAnalyzer.estimatedOneRepMax(reps = 0, weightKg = 100.0))
    }

    @Test
    fun `session volume sums only completed sets`() {
        val sets = listOf(set(10, 60.0), set(8, 70.0), set(5, 80.0, completed = false))
        // 600 + 560 = 1160 (third set not completed)
        assertEquals(1160.0, WorkoutProgressAnalyzer.sessionVolume(sets), 0.0001)
    }

    @Test
    fun `best set picks highest estimated one rep max`() {
        val sets = listOf(set(10, 60.0, n = 1), set(3, 90.0, n = 2), set(8, 70.0, n = 3))
        // 1RMs: 60*1.333=80, 90*1.1=99, 70*1.267=88.7 -> set 2 wins
        assertEquals(2, WorkoutProgressAnalyzer.bestSet(sets)!!.setNumber)
    }

    @Test
    fun `best set is null when no weighted completed sets`() {
        assertNull(WorkoutProgressAnalyzer.bestSet(listOf(set(12, null), set(5, 80.0, completed = false))))
    }

    @Test
    fun `trend points aggregate per date`() {
        val history = listOf(
            ExerciseHistoryPoint(date = "2026-06-10", reps = 10, weightKg = 60.0, rir = 2),
            ExerciseHistoryPoint(date = "2026-06-10", reps = 8, weightKg = 70.0, rir = 1),
            ExerciseHistoryPoint(date = "2026-06-17", reps = 5, weightKg = 90.0, rir = 0),
        )
        val trend = WorkoutProgressAnalyzer.trendPoints(history)

        assertEquals(2, trend.size)
        assertEquals("2026-06-10", trend[0].date)
        assertEquals(1160.0, trend[0].totalVolume, 0.0001) // 600 + 560
        assertEquals("2026-06-17", trend[1].date)
        assertEquals(90.0 * (1 + 5 / 30.0), trend[1].bestEstimatedOneRepMax!!, 0.001)
    }
}
