package com.zack.recomptracker.ui.train

import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.domain.workout.PlannedSet
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutTemplateExercise
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

    private class FakeWorkoutRepository(
        private val templates: Map<Long, WorkoutTemplate>,
    ) : WorkoutRepository(mock<WorkoutDao>()) {
        override suspend fun getById(id: Long): WorkoutTemplate? = templates[id]
    }

    private fun template(id: Long, name: String, ex: Exercise) = WorkoutTemplate(
        id = id, name = name, note = null, createdAt = "", updatedAt = "",
        exercises = listOf(
            WorkoutTemplateExercise(
                id = 1L, exercise = ex,
                plannedSets = listOf(PlannedSet(1L, 1, 10, 50.0)),
                sortOrder = 0, note = null,
            ),
        ),
    )

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

    // ── P1-15: reloading the already-loaded routine must not wipe the draft ──────

    @Test
    fun `loadWorkout is a no-op once its routine is already loaded so a picker return keeps the draft`() = runTest {
        val picked = exercise(1L, "Bench Press")
        val vm = RoutineBuilderViewModel(
            workoutRepository = FakeWorkoutRepository(mapOf(5L to template(5L, "Push Day", exercise(5L, "Deadlift")))),
            exerciseLibraryRepository = FakeExerciseLibraryRepository(mapOf(1L to picked)),
        )
        vm.loadWorkout(5L)                        // initial edit-mode load
        assertEquals("Push Day", vm.state.value.name)
        assertEquals(1, vm.state.value.exercises.size)

        // User edits the name and adds a just-picked exercise, then returns from the picker.
        vm.setName("Push Day (edited)")
        vm.addExercises(longArrayOf(1L))
        assertEquals(2, vm.state.value.exercises.size)

        // The re-fired load effect must NOT clobber the in-progress draft.
        vm.loadWorkout(5L)
        assertEquals("Push Day (edited)", vm.state.value.name)
        assertEquals(2, vm.state.value.exercises.size)
    }

    @Test
    fun `loadWorkout with a genuinely different routine id replaces the draft`() = runTest {
        val vm = RoutineBuilderViewModel(
            workoutRepository = FakeWorkoutRepository(
                mapOf(
                    5L to template(5L, "Push Day", exercise(5L, "Deadlift")),
                    7L to template(7L, "Pull Day", exercise(7L, "Row")),
                ),
            ),
            exerciseLibraryRepository = FakeExerciseLibraryRepository(emptyMap()),
        )
        vm.loadWorkout(5L)
        vm.setName("edited")
        vm.loadWorkout(7L)                        // different routine → replace, not skip
        assertEquals("Pull Day", vm.state.value.name)
        assertEquals(7L, vm.state.value.workoutId)
    }
}
