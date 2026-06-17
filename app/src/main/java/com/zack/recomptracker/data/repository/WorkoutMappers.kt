package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseWithSets
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseWithExercise
import com.zack.recomptracker.data.local.entity.WorkoutSessionWithDetailsDb
import com.zack.recomptracker.data.local.entity.WorkoutWithExercisesDb
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.domain.workout.ExerciseLibraryJson
import com.zack.recomptracker.domain.workout.PlannedSet
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.domain.workout.SessionStatus
import com.zack.recomptracker.domain.workout.WorkoutSession
import com.zack.recomptracker.domain.workout.WorkoutTemplate
import com.zack.recomptracker.domain.workout.WorkoutTemplateExercise

internal fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    externalId = externalId,
    name = name,
    category = category,
    force = force,
    level = level,
    mechanic = mechanic,
    equipment = equipment,
    primaryMuscles = ExerciseLibraryJson.decodeList(primaryMuscles),
    secondaryMuscles = ExerciseLibraryJson.decodeList(secondaryMuscles),
    instructions = ExerciseLibraryJson.decodeList(instructions),
    images = ExerciseLibraryJson.decodeList(images),
    userCreated = userCreated,
)

internal fun WorkoutWithExercisesDb.toDomain(): WorkoutTemplate = WorkoutTemplate(
    id = workout.id,
    name = workout.name,
    note = workout.note,
    createdAt = workout.createdAt,
    updatedAt = workout.updatedAt,
    exercises = exercises.sortedBy { it.workoutExercise.sortOrder }.map { it.toDomain() },
)

internal fun WorkoutExerciseWithExercise.toDomain(): WorkoutTemplateExercise = WorkoutTemplateExercise(
    id = workoutExercise.id,
    exercise = exercise.toDomain(),
    plannedSets = plannedSets.sortedBy { it.setNumber }
        .map { PlannedSet(it.id, it.setNumber, it.targetReps, it.targetWeightKg) },
    sortOrder = workoutExercise.sortOrder,
    note = workoutExercise.note,
)

internal fun SessionSetEntity.toDomain(): SessionSet = SessionSet(
    id = id,
    setNumber = setNumber,
    reps = reps,
    weightKg = weightKg,
    rir = rir,
    completed = completed,
)

internal fun SessionExerciseWithSets.toDomain(): SessionExercise = SessionExercise(
    id = sessionExercise.id,
    exerciseId = sessionExercise.exerciseId,
    exerciseName = sessionExercise.exerciseName,
    sortOrder = sessionExercise.sortOrder,
    note = sessionExercise.note,
    sets = sets.sortedBy { it.setNumber }.map { it.toDomain() },
)

internal fun WorkoutSessionWithDetailsDb.toDomain(): WorkoutSession = WorkoutSession(
    id = session.id,
    workoutId = session.workoutId,
    workoutName = session.workoutName,
    date = session.date,
    startedAt = session.startedAt,
    completedAt = session.completedAt,
    status = runCatching { SessionStatus.valueOf(session.status) }.getOrDefault(SessionStatus.ACTIVE),
    note = session.note,
    exercises = exercises.sortedBy { it.sessionExercise.sortOrder }.map { it.toDomain() },
)
