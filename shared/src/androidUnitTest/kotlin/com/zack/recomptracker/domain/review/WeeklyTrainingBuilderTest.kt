package com.zack.recomptracker.domain.review

import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.domain.workout.SessionStatus
import com.zack.recomptracker.domain.workout.WorkoutSession
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyTrainingBuilderTest {

    private val today = LocalDate(2026, 7, 1)

    private fun set(reps: Int, weightKg: Double?) =
        SessionSet(id = 0, setNumber = 1, reps = reps, weightKg = weightKg, rir = null, completed = true)

    private fun session(date: LocalDate, exerciseName: String, sets: List<SessionSet>) = WorkoutSession(
        id = 0, workoutId = null, workoutName = "w", date = date.toString(), startedAt = "",
        completedAt = "", status = SessionStatus.COMPLETED, note = null, durationSeconds = null,
        exercises = listOf(SessionExercise(0, 1, exerciseName, 0, null, sets)),
    )

    private fun dated(vararg s: WorkoutSession) = s.map { LocalDate.parse(it.date) to it }

    @Test
    fun `null when no sessions in the window`() {
        assertNull(WeeklyTrainingBuilder.build(emptyList(), today))
    }

    @Test
    fun `counts only sessions in the trailing seven days as this week`() {
        val t = WeeklyTrainingBuilder.build(
            dated(
                session(today, "Bench", listOf(set(8, 100.0))),
                session(today.minus(3, DateTimeUnit.DAY), "Bench", listOf(set(8, 95.0))),
                session(today.minus(10, DateTimeUnit.DAY), "Bench", listOf(set(8, 90.0))), // outside this week
            ),
            today,
        )!!
        assertEquals(2, t.sessionsThisWeek)
        assertEquals("Bench", t.topLift)
    }

    @Test
    fun `top lift trend up produces an up summary and never invents a number`() {
        val t = WeeklyTrainingBuilder.build(
            dated(
                session(today.minus(6, DateTimeUnit.DAY), "Bench", listOf(set(8, 90.0))),
                session(today.minus(3, DateTimeUnit.DAY), "Bench", listOf(set(8, 95.0))),
                session(today, "Bench", listOf(set(8, 105.0))),
            ),
            today,
        )!!
        assertEquals(SignalDirection.UP, t.topLiftTrend)
        assertTrue("summary mentions the top lift", t.summary.contains("Bench"))
        assertTrue("summary is number-safe (no fabricated e1RM)", !t.summary.contains("e1RM"))
        // The only number in the summary is the deterministic session count.
        assertTrue(t.summary.startsWith("3 training sessions this week"))
    }

    @Test
    fun `no weighted lift yields a flat trend and a no-lifts summary`() {
        val t = WeeklyTrainingBuilder.build(
            dated(session(today, "Plank", listOf(set(30, null)))),
            today,
        )!!
        assertNull(t.topLift)
        assertEquals(SignalDirection.FLAT, t.topLiftTrend)
        assertTrue(t.summary.contains("no weighted lifts"))
    }
}
