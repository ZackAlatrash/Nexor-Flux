package com.zack.recomptracker.domain.workout

import com.zack.recomptracker.domain.workout.TrainingPlanBuilder.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingPlanBuilderTest {

    private val today = LocalDate.of(2026, 7, 2) // Thursday; week Monday = 2026-06-29

    // ── test data helpers ────────────────────────────────────────────────────
    private fun ex(id: Long, muscle: String) = Exercise(
        id = id, externalId = "e$id", name = "Ex$id", category = null, force = null, level = null,
        mechanic = null, equipment = null, primaryMuscles = listOf(muscle), secondaryMuscles = emptyList(),
        instructions = emptyList(), images = emptyList(), userCreated = false,
    )

    private fun set() = SessionSet(id = 0, setNumber = 1, reps = 8, weightKg = 50.0, rir = 2, completed = true)

    private fun sx(exerciseId: Long, sets: Int) =
        SessionExercise(id = 0, exerciseId = exerciseId, exerciseName = "Ex$exerciseId", sortOrder = 0,
            note = null, sets = List(sets) { set() })

    private fun session(date: LocalDate, vararg exs: SessionExercise) = WorkoutSession(
        id = 0, workoutId = null, workoutName = "W", date = date.toString(), startedAt = "$date T00:00",
        completedAt = "$date T01:00", status = SessionStatus.COMPLETED, note = null, durationSeconds = 3600,
        exercises = exs.toList(),
    )

    private fun routine(id: Long, name: String, vararg muscles: String) = WorkoutTemplate(
        id = id, name = name, note = null, createdAt = "", updatedAt = "",
        exercises = muscles.mapIndexed { i, m ->
            WorkoutTemplateExercise(id = 0, exercise = ex(1000L + id * 10 + i, m), plannedSets = emptyList(),
                sortOrder = i, note = null)
        },
    )

    // Library must contain every exercise id referenced by sessions/routines.
    private fun libraryFor(vararg pairs: Pair<Long, String>) = pairs.map { ex(it.first, it.second) }

    private fun build(
        history: List<WorkoutSession>,
        library: List<Exercise>,
        routines: List<WorkoutTemplate> = emptyList(),
        weeklyTarget: Int? = null,
        recoveryLow: Boolean = false,
        deloadSuggested: Boolean = false,
    ) = TrainingPlanBuilder.build(
        history = history, library = library, routines = routines, today = today,
        weeklyTarget = weeklyTarget, recoveryLow = recoveryLow, deloadSuggested = deloadSuggested,
    )

    // ── rest triggers ─────────────────────────────────────────────────────────

    @Test fun `rests when the weekly session target is met`() {
        val lib = libraryFor(1L to "chest", 2L to "quadriceps")
        val history = listOf(
            session(today.minusDays(3), sx(1, 4)), // Mon
            session(today.minusDays(1), sx(2, 4)), // Wed (yesterday) — 2 sessions, not consecutive-3
        )
        val plan = build(history, lib, weeklyTarget = 2)
        assertEquals(Verdict.REST, plan.verdict)
        assertTrue(plan.reason.contains("2"))
    }

    @Test fun `rests after three consecutive training days`() {
        val lib = libraryFor(1L to "chest")
        val history = listOf(
            session(today.minusDays(1), sx(1, 3)),
            session(today.minusDays(2), sx(1, 3)),
            session(today.minusDays(3), sx(1, 3)),
        )
        val plan = build(history, lib)
        assertEquals(Verdict.REST, plan.verdict)
        assertTrue(plan.reason.contains("straight") || plan.reason.contains("3 days"))
    }

    @Test fun `rests when a deload is suggested`() {
        val lib = libraryFor(1L to "chest")
        val plan = build(listOf(session(today.minusDays(2), sx(1, 3))), lib, deloadSuggested = true)
        assertEquals(Verdict.REST, plan.verdict)
    }

    @Test fun `trained today rests and still surfaces the next focus`() {
        val lib = libraryFor(1L to "chest")
        val plan = build(listOf(session(today, sx(1, 4))), lib)
        assertEquals(Verdict.REST, plan.verdict)
        assertTrue(plan.reason.contains("today"))
        assertTrue(plan.focus.isNotEmpty()) // what to hit next time
    }

    // ── train + focus + routine match ─────────────────────────────────────────

    @Test fun `trains and focuses on muscles untrained this week`() {
        val lib = libraryFor(1L to "chest", 2L to "quadriceps")
        // This week: chest + legs trained; back/shoulders/arms/core = 0 sets.
        val history = listOf(session(today.minusDays(2), sx(1, 5), sx(2, 5)))
        val plan = build(history, lib, weeklyTarget = 5) // 1 session, under target → TRAIN
        assertEquals(Verdict.TRAIN, plan.verdict)
        assertTrue(plan.focus.contains(MuscleCategory.BACK))
        assertFalse(plan.focus.contains(MuscleCategory.CHEST))
        assertFalse(plan.focus.contains(MuscleCategory.LEGS))
    }

    @Test fun `recommends the routine that best covers the focus`() {
        val lib = libraryFor(1L to "chest", 2L to "shoulders", 3L to "biceps", 4L to "quadriceps")
        // Trained chest, shoulders, arms, legs this week → BACK is the clear gap.
        val history = listOf(
            session(today.minusDays(2), sx(1, 4), sx(2, 4)),
            session(today.minusDays(1), sx(3, 4), sx(4, 4)),
        )
        val pull = routine(1, "Pull", "lats", "biceps")   // covers BACK (+arms)
        val legs = routine(2, "Legs", "quadriceps")        // covers nothing in the focus
        val plan = build(history, lib, routines = listOf(legs, pull), weeklyTarget = 5)
        assertEquals(Verdict.TRAIN, plan.verdict)
        assertTrue(plan.focus.contains(MuscleCategory.BACK))
        assertEquals("Pull", plan.recommendedRoutineName)
        assertEquals(1L, plan.recommendedRoutineId)
    }

    @Test fun `no routine recommended when none covers the focus`() {
        val lib = libraryFor(1L to "chest", 2L to "shoulders", 3L to "biceps", 4L to "quadriceps")
        val history = listOf(
            session(today.minusDays(2), sx(1, 4), sx(2, 4)),
            session(today.minusDays(1), sx(3, 4), sx(4, 4)),
        )
        val legs = routine(2, "Legs", "quadriceps") // BACK is the focus; Legs doesn't cover it
        val plan = build(history, lib, routines = listOf(legs), weeklyTarget = 5)
        assertEquals(Verdict.TRAIN, plan.verdict)
        assertNull(plan.recommendedRoutineId)
        assertNull(plan.recommendedRoutineName)
    }

    // ── weekly breakdown (for the Stats tab) ──────────────────────────────────

    @Test fun `weekly breakdown reports sets and days-since-last-hit per category`() {
        val lib = libraryFor(1L to "chest", 2L to "lats")
        val history = listOf(
            session(today.minusDays(1), sx(1, 5)),  // chest, 5 sets, this week
            session(today.minusDays(10), sx(2, 3)), // back, 3 sets, 10 days ago (outside week)
        )
        val plan = build(history, lib)
        val byCat = plan.weekly.associateBy { it.category }
        assertEquals(6, plan.weekly.size) // all categories present
        assertEquals(5, byCat.getValue(MuscleCategory.CHEST).setsThisWeek)
        assertEquals(1, byCat.getValue(MuscleCategory.CHEST).daysSinceLastHit)
        assertEquals(0, byCat.getValue(MuscleCategory.BACK).setsThisWeek) // 10d ago = outside this week
        assertEquals(10, byCat.getValue(MuscleCategory.BACK).daysSinceLastHit)
        assertNull(byCat.getValue(MuscleCategory.LEGS).daysSinceLastHit) // never trained
    }

    // ── recovery tempering ────────────────────────────────────────────────────

    @Test fun `low recovery tempers a train verdict but does not force rest`() {
        val lib = libraryFor(1L to "chest")
        val plan = build(listOf(session(today.minusDays(3), sx(1, 4))), lib, weeklyTarget = 5, recoveryLow = true)
        assertEquals(Verdict.TRAIN, plan.verdict)
        assertTrue(plan.recoveryTempered)
    }

    // ── only completed sets count ─────────────────────────────────────────────

    @Test fun `incomplete sets do not count toward weekly volume`() {
        val lib = libraryFor(1L to "chest")
        val partial = SessionExercise(
            id = 0, exerciseId = 1, exerciseName = "Ex1", sortOrder = 0, note = null,
            sets = listOf(set(), SessionSet(0, 2, 8, 50.0, 2, completed = false)),
        )
        val plan = build(listOf(session(today.minusDays(1), partial)), lib)
        val chest = plan.weekly.first { it.category == MuscleCategory.CHEST }
        assertEquals(1, chest.setsThisWeek) // only the completed set
    }
}
