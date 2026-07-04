package com.zack.recomptracker.ui.train

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.domain.workout.Exercise
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock

class RoutineBuilderViewModelTest {

    private fun exercise(id: Long, name: String) = Exercise(
        id = id,
        externalId = "ext-$id",
        name = name,
        category = null,
        force = null,
        level = null,
        mechanic = null,
        equipment = null,
        primaryMuscles = emptyList(),
        secondaryMuscles = emptyList(),
        instructions = emptyList(),
        images = emptyList(),
        userCreated = false,
    )

    private class FakeExerciseLibraryRepository(
        private val exercises: Map<Long, Exercise>,
    ) : ExerciseLibraryRepository(mock<ExerciseDao>()) {
        override suspend fun getById(id: Long): Exercise? = exercises[id]
    }

    private fun newViewModel(exercises: Map<Long, Exercise>) = RoutineBuilderViewModel(
        workoutRepository = WorkoutRepository(mock<WorkoutDao>()),
        exerciseLibraryRepository = FakeExerciseLibraryRepository(exercises),
    )

    @Test
    fun `setTarget updates only the targeted set and preserves reference identity of untouched exercises and sets`() = runTest {
        val ex1 = exercise(1L, "Bench Press")
        val ex2 = exercise(2L, "Squat")
        val vm = newViewModel(mapOf(1L to ex1, 2L to ex2))

        // Two exercises, each starting with one default set.
        vm.addExercises(longArrayOf(1L, 2L))
        // Give exercise 0 (Bench Press) a second set so we can assert its sibling set is untouched.
        vm.addSet(exIndex = 0)

        val before = vm.state.value
        assertEquals(2, before.exercises.size)
        assertEquals(2, before.exercises[0].sets.size)
        val untouchedExercise = before.exercises[1] // Squat — not targeted
        val untouchedSetInTarget = before.exercises[0].sets[1] // Bench's 2nd set — not targeted

        vm.setTarget(exIndex = 0, setIndex = 0, reps = 8, weightKg = 60.0)
        val after = vm.state.value

        // (a) the targeted set changed
        val updatedSet = after.exercises[0].sets[0]
        assertEquals(8, updatedSet.targetReps)
        assertEquals(60.0, updatedSet.targetWeightKg)

        // (b) untouched exercise object is reference-identical (===) after the call
        assertSame(untouchedExercise, after.exercises[1])

        // Bonus: the untouched sibling set within the targeted exercise is also reference-identical.
        assertSame(untouchedSetInTarget, after.exercises[0].sets[1])
    }

    @Test
    fun `setTarget with an out-of-range index leaves state unchanged`() = runTest {
        val ex1 = exercise(1L, "Bench Press")
        val vm = newViewModel(mapOf(1L to ex1))
        vm.addExercises(longArrayOf(1L))

        val before = vm.state.value
        vm.setTarget(exIndex = 5, setIndex = 0, reps = 10, weightKg = 40.0)
        val after = vm.state.value

        assertSame(before, after)
    }
}
