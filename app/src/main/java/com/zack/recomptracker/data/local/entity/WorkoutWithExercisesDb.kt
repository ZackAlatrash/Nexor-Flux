package com.zack.recomptracker.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class WorkoutExerciseWithExercise(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val plannedSets: List<PlannedSetEntity>,
)

data class WorkoutWithExercisesDb(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId",
    )
    val exercises: List<WorkoutExerciseWithExercise>,
)
