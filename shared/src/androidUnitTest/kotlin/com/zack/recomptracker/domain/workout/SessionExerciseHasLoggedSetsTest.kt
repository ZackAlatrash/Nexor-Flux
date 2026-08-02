package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-17: removing (or replacing) an exercise is destructive only when it has logged work. This is the
 * shared predicate that gates both behind a confirm dialog.
 */
class SessionExerciseHasLoggedSetsTest {

    private fun set(reps: Int = 0, weightKg: Double? = null, completed: Boolean = false) =
        SessionSet(id = 1, setNumber = 1, reps = reps, weightKg = weightKg, rir = null, completed = completed)

    private fun exercise(sets: List<SessionSet>) =
        SessionExercise(id = 1, exerciseId = 1, exerciseName = "Bench", sortOrder = 0, note = null, sets = sets)

    @Test
    fun `a completed set counts as logged`() {
        assertTrue(exercise(listOf(set(completed = true))).hasLoggedSets())
    }

    @Test
    fun `entered reps count as logged`() {
        assertTrue(exercise(listOf(set(reps = 8))).hasLoggedSets())
    }

    @Test
    fun `an entered weight counts as logged`() {
        assertTrue(exercise(listOf(set(weightKg = 60.0))).hasLoggedSets())
    }

    @Test
    fun `blank untouched sets are not logged`() {
        assertFalse(exercise(listOf(set(), set())).hasLoggedSets())
    }

    @Test
    fun `no sets is not logged`() {
        assertFalse(exercise(emptyList()).hasLoggedSets())
    }
}
