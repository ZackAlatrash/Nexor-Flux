package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "catalog_foods",
    indices = [
        Index(value = ["source", "externalId"], unique = true),
        Index(value = ["name"]),
    ],
)
data class CatalogFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val sourceVersion: String,
    val externalId: String,
    val name: String,
    val servingName: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)
