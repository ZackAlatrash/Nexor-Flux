package com.zack.recomptracker.domain.foodimport

import kotlin.math.round
import kotlinx.serialization.Serializable

@Serializable
data class FoodImportCandidate(
    val externalId: String? = null,
    val name: String,
    val servingName: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

data class FoodImportSummary(
    val importedCount: Int,
    val skippedCount: Int = 0,
    val duplicateCount: Int = 0,
)

data class FoodIdentity(
    val normalizedName: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

fun FoodImportCandidate.identity(): FoodIdentity = FoodIdentity(
    normalizedName = FoodNameNormalizer.normalize(name),
    calories = calories,
    proteinG = proteinG.roundToOneDecimal(),
    carbsG = carbsG.roundToOneDecimal(),
    fatG = fatG.roundToOneDecimal(),
)

private fun Double.roundToOneDecimal(): Double = round(this * 10.0) / 10.0
