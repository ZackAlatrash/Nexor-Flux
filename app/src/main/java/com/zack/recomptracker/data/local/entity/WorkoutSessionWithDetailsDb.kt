package com.zack.recomptracker.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class SessionExerciseWithSets(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<SessionSetEntity>,
)

data class WorkoutSessionWithDetailsDb(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val exercises: List<SessionExerciseWithSets>,
)

/** Flat projection for AI exercise-history queries. */
data class ExerciseHistoryRow(
    val date: String,
    val reps: Int,
    val weightKg: Double?,
    val rir: Int?,
)
