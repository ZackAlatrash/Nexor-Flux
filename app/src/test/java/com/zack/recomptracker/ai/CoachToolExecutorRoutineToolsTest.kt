package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.NewWorkoutLine
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.domain.workout.PlannedSet
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutTemplateExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class CoachToolExecutorRoutineToolsTest {

    private val fixedDate = java.time.LocalDate.of(2026, 6, 5)
    private val dateProvider = object : DateProvider { override fun today() = fixedDate }

    private fun exercise(id: Long, name: String, primary: List<String> = emptyList()) =
        Exercise(id, "ext_$id", name, null, null, null, null, null, primary, emptyList(), emptyList(), emptyList(), false)

    /** Fake exercise library: search matches by case-insensitive substring. */
    class FakeExerciseLibrary(private val all: List<Exercise>) : ExerciseLibraryRepository(mock<ExerciseDao>()) {
        val created = mutableListOf<Triple<String, List<String>, List<String>>>()
        override suspend fun search(query: String): List<Exercise> =
            all.filter { it.name.contains(query, ignoreCase = true) }
        override suspend fun getById(id: Long): Exercise? = all.firstOrNull { it.id == id }
        override suspend fun addCustomExercise(name: String, primaryMuscles: List<String>, secondaryMuscles: List<String>): Long {
            created += Triple(name, primaryMuscles, secondaryMuscles)
            return 999L
        }
    }

    /** Fake workout repo: in-memory templates; records create/update calls. */
    class FakeWorkoutRepo(initial: List<WorkoutTemplate> = emptyList()) : WorkoutRepository(mock<WorkoutDao>()) {
        val templates = initial.toMutableList()
        var lastSaved: Triple<String, String?, List<NewWorkoutLine>>? = null
        var lastUpdated: Triple<Long, String, List<NewWorkoutLine>>? = null
        override fun observeAll(): Flow<List<WorkoutTemplate>> = flowOf(templates)
        override suspend fun getById(id: Long): WorkoutTemplate? = templates.firstOrNull { it.id == id }
        override suspend fun saveWorkout(name: String, note: String?, lines: List<NewWorkoutLine>): Long {
            if (lines.isEmpty()) throw IllegalArgumentException("A workout must contain at least one exercise.")
            lastSaved = Triple(name, note, lines); return 1L
        }
        override suspend fun updateWorkout(workoutId: Long, name: String, note: String?, lines: List<NewWorkoutLine>) {
            lastUpdated = Triple(workoutId, name, lines)
        }
    }

    private fun executor(
        workoutRepo: WorkoutRepository,
        library: ExerciseLibraryRepository,
    ) = CoachToolExecutor(
        logRepository = mock<LogRepository>(),
        planRepository = mock<PlanRepository>(),
        dateProvider = dateProvider,
        workoutRepository = workoutRepo,
        exerciseLibraryRepository = library,
    )

    private fun template(id: Long, name: String, ex: List<Pair<String, Int>>) = WorkoutTemplate(
        id = id, name = name, note = null, createdAt = "", updatedAt = "",
        exercises = ex.mapIndexed { i, (exName, setCount) ->
            WorkoutTemplateExercise(
                id = i.toLong(),
                exercise = exercise(i.toLong(), exName),
                plannedSets = (1..setCount).map { PlannedSet(it.toLong(), it, 8, null) },
                sortOrder = i, note = null,
            )
        },
    )

    @Test
    fun `get_routines returns the users routines with exercises and set counts`() = runTest {
        val repo = FakeWorkoutRepo(listOf(template(1, "Push Day", listOf("Bench Press" to 4))))
        val json = executor(repo, FakeExerciseLibrary(emptyList())).execute("get_routines", emptyMap())
        assertTrue(json.contains("Push Day"))
        assertTrue(json.contains("Bench Press"))
    }

    @Test
    fun `search_exercises returns library matches by name`() = runTest {
        val lib = FakeExerciseLibrary(listOf(exercise(1, "Barbell Bench Press", listOf("chest"))))
        val json = executor(FakeWorkoutRepo(), lib).execute("search_exercises", mapOf("query" to "bench"))
        assertTrue(json.contains("Barbell Bench Press"))
    }

    @Test
    fun `create_routine resolves exercises and saves with correct set counts`() = runTest {
        val lib = FakeExerciseLibrary(listOf(exercise(1, "Barbell Bench Press")))
        val repo = FakeWorkoutRepo()
        val json = executor(repo, lib).execute(
            "create_routine",
            mapOf("name" to "Push Day", "exercises" to """[{"name":"bench press","sets":4,"reps":8}]"""),
        )
        assertTrue(json.contains("\"success\":true"))
        val saved = repo.lastSaved!!
        assertTrue(saved.first == "Push Day")
        assertTrue(saved.third.size == 1)
        assertTrue(saved.third[0].plannedSets.size == 4)
        assertTrue(saved.third[0].plannedSets.all { it.targetReps == 8 })
    }

    @Test
    fun `create_routine allows an exercise with only a set count and no reps`() = runTest {
        val lib = FakeExerciseLibrary(listOf(exercise(1, "Barbell Bench Press")))
        val repo = FakeWorkoutRepo()
        executor(repo, lib).execute(
            "create_routine",
            mapOf("name" to "Push Day", "exercises" to """[{"name":"bench press","sets":3}]"""),
        )
        val line = repo.lastSaved!!.third[0]
        assertTrue(line.plannedSets.size == 3)
        assertTrue(line.plannedSets.all { it.targetReps == null })
    }

    @Test
    fun `create_routine reports unresolved exercises without saving`() = runTest {
        val repo = FakeWorkoutRepo()
        val json = executor(repo, FakeExerciseLibrary(emptyList())).execute(
            "create_routine",
            mapOf("name" to "Push Day", "exercises" to """[{"name":"Zercher Thruster","sets":3}]"""),
        )
        assertTrue(json.contains("error"))
        assertTrue(json.contains("Zercher Thruster"))
        assertTrue(repo.lastSaved == null)
    }

    @Test
    fun `create_routine surfaces validation errors from the repository`() = runTest {
        val json = executor(FakeWorkoutRepo(), FakeExerciseLibrary(emptyList())).execute(
            "create_routine",
            mapOf("name" to "Empty", "exercises" to "[]"),
        )
        assertTrue(json.contains("error"))
    }
}
