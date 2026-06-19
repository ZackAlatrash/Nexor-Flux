package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainStatsBuilderTest {

    private fun exercise(id: Long, name: String, muscles: List<String>) = Exercise(
        id = id, externalId = "e$id", name = name, category = null, force = null, level = null,
        mechanic = null, equipment = null, primaryMuscles = muscles, secondaryMuscles = emptyList(),
        instructions = emptyList(), images = emptyList(), userCreated = false,
    )

    private fun session(id: Long, date: String, exId: Long, exName: String, completed: Boolean) =
        WorkoutSession(
            id = id, workoutId = null, workoutName = "W", date = date, startedAt = "${date}T10:00",
            completedAt = "${date}T11:00", status = SessionStatus.COMPLETED, note = null, durationSeconds = null,
            exercises = listOf(
                SessionExercise(
                    id = id * 10, exerciseId = exId, exerciseName = exName, sortOrder = 0, note = null,
                    sets = listOf(SessionSet(id = id * 100, setNumber = 1, reps = 10, weightKg = 20.0, rir = null, completed = completed)),
                ),
            ),
        )

    @Test fun returnsAllSixCategoriesInFixedOrder() {
        val result = TrainStatsBuilder.build(emptyList(), emptyList())
        assertEquals(MuscleCategory.entries.toList(), result.map { it.category })
        assertTrue(result.all { it.exercises.isEmpty() })
    }

    @Test fun bucketsExerciseUnderItsCategoryWithCountAndLastDate() {
        val library = listOf(exercise(1, "Dumbbell Curl", listOf("Biceps")))
        val history = listOf(
            session(1, "2026-06-10", 1, "Dumbbell Curl", completed = true),
            session(2, "2026-06-17", 1, "Dumbbell Curl", completed = true),
        )
        val arms = TrainStatsBuilder.build(history, library).first { it.category == MuscleCategory.ARMS }
        assertEquals(1, arms.exercises.size)
        val curl = arms.exercises.first()
        assertEquals("Dumbbell Curl", curl.name)
        assertEquals(2, curl.sessionCount)
        assertEquals("2026-06-17", curl.lastDate)
    }

    @Test fun ignoresExercisesWithNoCompletedSets() {
        val library = listOf(exercise(1, "Dumbbell Curl", listOf("Biceps")))
        val history = listOf(session(1, "2026-06-10", 1, "Dumbbell Curl", completed = false))
        val arms = TrainStatsBuilder.build(history, library).first { it.category == MuscleCategory.ARMS }
        assertTrue(arms.exercises.isEmpty())
    }

    @Test fun dropsExercisesWithUnmappedOrMissingMuscles() {
        val library = listOf(exercise(1, "Neck Curl", listOf("Neck")))
        val history = listOf(session(1, "2026-06-10", 1, "Neck Curl", completed = true))
        val result = TrainStatsBuilder.build(history, library)
        assertTrue(result.all { it.exercises.isEmpty() })
    }

    @Test fun exercisesSortedByNameWithinCategory() {
        val library = listOf(
            exercise(1, "Triceps Pushdown", listOf("Triceps")),
            exercise(2, "Dumbbell Curl", listOf("Biceps")),
        )
        val history = listOf(
            session(1, "2026-06-10", 1, "Triceps Pushdown", completed = true),
            session(2, "2026-06-10", 2, "Dumbbell Curl", completed = true),
        )
        val arms = TrainStatsBuilder.build(history, library).first { it.category == MuscleCategory.ARMS }
        assertEquals(listOf("Dumbbell Curl", "Triceps Pushdown"), arms.exercises.map { it.name })
    }
}
