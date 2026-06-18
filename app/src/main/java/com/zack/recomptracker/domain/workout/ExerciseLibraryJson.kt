package com.zack.recomptracker.domain.workout

import com.zack.recomptracker.data.local.entity.ExerciseEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class FreeExerciseDbExerciseDto(
    val id: String,
    val name: String,
    val force: String? = null,
    val level: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val category: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
)

object ExerciseLibraryJson {
    private val json = Json { ignoreUnknownKeys = true }
    private val stringList = ListSerializer(String.serializer())

    fun parse(raw: String): List<FreeExerciseDbExerciseDto> =
        json.decodeFromString(ListSerializer(FreeExerciseDbExerciseDto.serializer()), raw)

    fun encodeList(list: List<String>): String = json.encodeToString(stringList, list)

    fun decodeList(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else json.decodeFromString(stringList, raw)
}

/** Top-level so it is usable as a plain extension across packages (and same-package tests). */
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
