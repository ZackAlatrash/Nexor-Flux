package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.domain.workout.SessionStatus
import com.zack.recomptracker.domain.workout.WorkoutSession
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingDerivationsTest {

    private val today = LocalDate.of(2026, 7, 1)

    // ── Fixture builders (plain WorkoutSession, no Room/Android) ─────────────────────

    private fun set(reps: Int, weightKg: Double?, rir: Int? = null, completed: Boolean = true) =
        SessionSet(id = 0, setNumber = 1, reps = reps, weightKg = weightKg, rir = rir, completed = completed)

    private fun exercise(name: String, sets: List<SessionSet>) = SessionExercise(
        id = 0, exerciseId = 1, exerciseName = name, sortOrder = 0, note = null, sets = sets,
    )

    private fun session(date: LocalDate, exercises: List<SessionExercise>) = WorkoutSession(
        id = 0,
        workoutId = null,
        workoutName = "w",
        date = date.toString(),
        startedAt = "",
        completedAt = "",
        status = SessionStatus.COMPLETED,
        note = null,
        durationSeconds = null,
        exercises = exercises,
    )

    private fun session(date: LocalDate, exerciseName: String, sets: List<SessionSet>) =
        session(date, listOf(exercise(exerciseName, sets)))

    // Pairs the derivation consumes: (date, session). Mirrors what the assembler passes in.
    private fun dated(vararg s: WorkoutSession): List<Pair<LocalDate, WorkoutSession>> =
        s.map { LocalDate.parse(it.date) to it }

    // ── e1RM series: best-per-day-per-exercise, oldest → newest ─────────────────────

    @Test
    fun `e1rm series keeps best set per day per exercise, oldest first`() {
        val sessions = dated(
            session(today.minusDays(7), "Bench", listOf(set(5, 100.0), set(8, 80.0))), // best 116.67
            session(today, "Bench", listOf(set(8, 100.0))), // 126.67
        )
        val series = TrainingDerivations.e1rmSeriesByExercise(sessions)["Bench"]!!
        assertEquals(2, series.size)
        assertEquals(today.minusDays(7), series.first().date)
        assertEquals(116.667, series.first().estimatedOneRepMax, 0.01)
        assertEquals(today, series.last().date)
        assertEquals(126.667, series.last().estimatedOneRepMax, 0.01)
    }

    @Test
    fun `e1rm collapses multiple same-day sessions to the single best that day`() {
        val sessions = dated(
            session(today, "Bench", listOf(set(5, 100.0))), // 116.67
            session(today, "Bench", listOf(set(3, 110.0))), // 121.0 -> wins the day
        )
        val series = TrainingDerivations.e1rmSeriesByExercise(sessions)["Bench"]!!
        assertEquals(1, series.size)
        assertEquals(121.0, series.first().estimatedOneRepMax, 0.01)
    }

    @Test
    fun `e1rm ignores incomplete and bodyweight sets, dropping empty exercises`() {
        val sessions = dated(
            session(today, "Pullup", listOf(set(10, null), set(5, 40.0, completed = false))),
        )
        val series = TrainingDerivations.e1rmSeriesByExercise(sessions)
        assertNull(series["Pullup"])
    }

    @Test
    fun `e1rm handles several exercises within one session independently`() {
        val sessions = dated(
            session(today, listOf(
                exercise("Bench", listOf(set(5, 100.0))),
                exercise("Squat", listOf(set(5, 140.0))),
            )),
        )
        val series = TrainingDerivations.e1rmSeriesByExercise(sessions)
        assertEquals(2, series.size)
        assertEquals(116.667, series["Bench"]!!.first().estimatedOneRepMax, 0.01)
        assertEquals(163.333, series["Squat"]!!.first().estimatedOneRepMax, 0.01)
    }

    // ── latest e1RM per lift ────────────────────────────────────────────────────────

    @Test
    fun `latest e1rm returns the newest point value per lift`() {
        val sessions = dated(
            session(today.minusDays(7), "Bench", listOf(set(5, 100.0))), // 116.67
            session(today, "Bench", listOf(set(8, 100.0))), // 126.67 (newest)
        )
        val latest = TrainingDerivations.latestE1rmByExercise(sessions)
        assertEquals(126.667, latest["Bench"]!!, 0.01)
    }

    @Test
    fun `latest e1rm empty when no qualifying sets`() {
        assertTrue(TrainingDerivations.latestE1rmByExercise(emptyList()).isEmpty())
    }

    // ── recent RIR ──────────────────────────────────────────────────────────────────

    @Test
    fun `recent rir gathers completed-set rir from the most recent sessions, newest first`() {
        val sessions = dated(
            session(today, "Bench", listOf(set(5, 100.0, rir = 1), set(5, 100.0, rir = 2))),
            session(today.minusDays(2), "Squat", listOf(set(5, 140.0, rir = 3))),
        )
        val rir = TrainingDerivations.recentRir(sessions, sessionCount = 3)
        assertEquals(listOf(1, 2, 3), rir)
    }

    @Test
    fun `recent rir caps to the requested number of most-recent sessions`() {
        val sessions = dated(
            session(today, "Bench", listOf(set(5, 100.0, rir = 0))),
            session(today.minusDays(1), "Bench", listOf(set(5, 100.0, rir = 1))),
            session(today.minusDays(2), "Bench", listOf(set(5, 100.0, rir = 2))),
        )
        val rir = TrainingDerivations.recentRir(sessions, sessionCount = 2)
        assertEquals(listOf(0, 1), rir)
    }

    @Test
    fun `recent rir skips incomplete sets and null rir`() {
        val sessions = dated(
            session(today, "Bench", listOf(
                set(5, 100.0, rir = 2),
                set(5, 100.0, rir = 3, completed = false), // incomplete
                set(5, 100.0, rir = null), // no rir
            )),
        )
        assertEquals(listOf(2), TrainingDerivations.recentRir(sessions, sessionCount = 3))
    }

    // ── weekly training volume ──────────────────────────────────────────────────────

    @Test
    fun `weekly volume sums reps times weight of completed sets across all lifts`() {
        val sessions = dated(
            session(today, listOf(
                exercise("Bench", listOf(set(5, 100.0), set(5, 100.0))), // 500 + 500
                exercise("Squat", listOf(set(3, 140.0))), // 420
            )),
        )
        // 1000 + 420 = 1420
        assertEquals(1420.0, TrainingDerivations.totalTrainingVolume(sessions), 0.001)
    }

    @Test
    fun `weekly volume excludes incomplete and bodyweight sets`() {
        val sessions = dated(
            session(today, "Bench", listOf(
                set(5, 100.0), // 500
                set(5, 100.0, completed = false), // excluded
                set(10, null), // bodyweight -> 0 contribution
            )),
        )
        assertEquals(500.0, TrainingDerivations.totalTrainingVolume(sessions), 0.001)
    }

    @Test
    fun `weekly volume is zero for no sessions`() {
        assertEquals(0.0, TrainingDerivations.totalTrainingVolume(emptyList()), 0.001)
    }

    // ── per-lift trend direction ────────────────────────────────────────────────────

    @Test
    fun `trend direction is UP when recent e1rm rises across the window`() {
        val sessions = dated(
            session(today.minusDays(14), "Bench", listOf(set(5, 90.0))),  // 105.0
            session(today.minusDays(7), "Bench", listOf(set(5, 100.0))),  // 116.67
            session(today, "Bench", listOf(set(5, 110.0))),               // 128.33
        )
        assertEquals(TrendDirection.UP, TrainingDerivations.trendDirection(sessions, "Bench"))
    }

    @Test
    fun `trend direction is DOWN when recent e1rm falls`() {
        val sessions = dated(
            session(today.minusDays(14), "Bench", listOf(set(5, 110.0))), // 128.33
            session(today.minusDays(7), "Bench", listOf(set(5, 100.0))),  // 116.67
            session(today, "Bench", listOf(set(5, 90.0))),                // 105.0
        )
        assertEquals(TrendDirection.DOWN, TrainingDerivations.trendDirection(sessions, "Bench"))
    }

    @Test
    fun `trend direction is FLAT when e1rm barely moves`() {
        val sessions = dated(
            session(today.minusDays(14), "Bench", listOf(set(5, 100.0))),
            session(today.minusDays(7), "Bench", listOf(set(5, 100.0))),
            session(today, "Bench", listOf(set(5, 100.0))),
        )
        assertEquals(TrendDirection.FLAT, TrainingDerivations.trendDirection(sessions, "Bench"))
    }

    @Test
    fun `trend direction only considers the last N points`() {
        // A big early drop then a clean 3-point rise; with lastN=3 the early drop is ignored -> UP.
        val sessions = dated(
            session(today.minusDays(28), "Bench", listOf(set(5, 150.0))), // ignored
            session(today.minusDays(21), "Bench", listOf(set(5, 80.0))),  // ignored
            session(today.minusDays(14), "Bench", listOf(set(5, 90.0))),  // 105.0
            session(today.minusDays(7), "Bench", listOf(set(5, 100.0))),  // 116.67
            session(today, "Bench", listOf(set(5, 110.0))),               // 128.33
        )
        assertEquals(TrendDirection.UP, TrainingDerivations.trendDirection(sessions, "Bench", lastN = 3))
    }

    @Test
    fun `trend direction is null with fewer than two points or unknown lift`() {
        val single = dated(session(today, "Bench", listOf(set(5, 100.0))))
        assertNull(TrainingDerivations.trendDirection(single, "Bench"))
        assertNull(TrainingDerivations.trendDirection(single, "Deadlift"))
        assertNull(TrainingDerivations.trendDirection(emptyList(), "Bench"))
    }
}
