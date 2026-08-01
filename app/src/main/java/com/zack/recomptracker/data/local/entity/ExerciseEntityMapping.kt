package com.zack.recomptracker.data.local.entity

import com.zack.recomptracker.domain.workout.ExerciseLibraryJson
import com.zack.recomptracker.domain.workout.FreeExerciseDbExerciseDto

/**
 * Maps the pure library DTO onto the Room entity. Lives in :app because ExerciseEntity is a Room
 * type; the DTO and its codec stay in :shared.
 */
fun FreeExerciseDbExerciseDto.toEntity(source: String, sourceVersion: String): ExerciseEntity =
    ExerciseEntity(
        source = source,
        sourceVersion = sourceVersion,
        externalId = id,
        name = name,
        category = category,
        force = force,
        level = level,
        mechanic = mechanic,
        equipment = equipment,
        primaryMuscles = ExerciseLibraryJson.encodeList(primaryMuscles),
        secondaryMuscles = ExerciseLibraryJson.encodeList(secondaryMuscles),
        instructions = ExerciseLibraryJson.encodeList(instructions),
        images = ExerciseLibraryJson.encodeList(images),
        userCreated = false,
    )
