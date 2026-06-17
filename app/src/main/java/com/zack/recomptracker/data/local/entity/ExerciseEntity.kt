package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["source", "externalId"], unique = true),
        Index(value = ["name"]),
    ],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val sourceVersion: String,
    val externalId: String,
    val name: String,
    val category: String?,
    val force: String?,
    val level: String?,
    val mechanic: String?,
    val equipment: String?,
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val instructions: String,
    val images: String,
    val userCreated: Boolean = false,
)
