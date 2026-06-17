package com.zack.recomptracker.domain.workout

/** Library exercise with decoded list fields. */
data class Exercise(
    val id: Long,
    val externalId: String,
    val name: String,
    val category: String?,
    val force: String?,
    val level: String?,
    val mechanic: String?,
    val equipment: String?,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    val images: List<String>,
    val userCreated: Boolean,
)

/** One line of a workout template. */
data class WorkoutTemplateExercise(
    val id: Long,
    val exercise: Exercise,
    val plannedSets: Int,
    val targetReps: Int?,
    val sortOrder: Int,
    val note: String?,
)

/** A reusable workout template. */
data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val note: String?,
    val createdAt: String,
    val updatedAt: String,
    val exercises: List<WorkoutTemplateExercise>,
)

enum class SessionStatus { ACTIVE, COMPLETED, ABANDONED }

data class SessionSet(
    val id: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double?,
    val rir: Int?,
    val completed: Boolean,
)

data class SessionExercise(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val sortOrder: Int,
    val note: String?,
    val sets: List<SessionSet>,
)

data class WorkoutSession(
    val id: Long,
    val workoutId: Long?,
    val workoutName: String,
    val date: String,
    val startedAt: String,
    val completedAt: String?,
    val status: SessionStatus,
    val note: String?,
    val exercises: List<SessionExercise>,
)

/** Flat per-set record used for AI progress analysis. */
data class ExerciseHistoryPoint(
    val date: String,
    val reps: Int,
    val weightKg: Double?,
    val rir: Int?,
)

/** One day's aggregated progress for a single exercise. */
data class ExerciseTrendPoint(
    val date: String,
    val totalVolume: Double,
    val bestEstimatedOneRepMax: Double?,
)
