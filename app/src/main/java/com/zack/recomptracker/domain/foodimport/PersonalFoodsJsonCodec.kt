package com.zack.recomptracker.domain.foodimport

import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class DecodedPersonalFoods(
    val version: Int,
    val exportedAt: String,
    val foods: List<FoodImportCandidate>,
)

class PersonalFoodsJsonCodec(
    private val now: () -> String = { Instant.now().toString() },
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(foods: List<FoodImportCandidate>): String = json.encodeToString(
        PersonalFoodsPayload.serializer(),
        PersonalFoodsPayload(
            exportedAt = now(),
            foods = foods.map {
                validatePersonalFood(it)
                PersonalFoodExport(
                    name = it.name.trim(),
                    servingName = it.servingName.trim(),
                    calories = it.calories,
                    proteinG = it.proteinG,
                    carbsG = it.carbsG,
                    fatG = it.fatG,
                )
            },
        ),
    )

    fun decode(rawJson: String): DecodedPersonalFoods {
        val payload = json.decodeFromString(PersonalFoodsPayload.serializer(), rawJson)
        require(payload.version == SUPPORTED_VERSION) {
            "Unsupported personal-food JSON version: ${payload.version}"
        }
        return DecodedPersonalFoods(
            version = payload.version,
            exportedAt = payload.exportedAt,
            foods = payload.foods.map {
                FoodImportCandidate(
                    name = it.name,
                    servingName = it.servingName,
                    calories = it.calories,
                    proteinG = it.proteinG,
                    carbsG = it.carbsG,
                    fatG = it.fatG,
                ).also(::validatePersonalFood)
            },
        )
    }

    private companion object {
        const val SUPPORTED_VERSION = 1
    }
}

@Serializable
private data class PersonalFoodsPayload(
    val version: Int = 1,
    val exportedAt: String,
    val foods: List<PersonalFoodExport>,
)

@Serializable
private data class PersonalFoodExport(
    val name: String,
    val servingName: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

fun validatePersonalFood(food: FoodImportCandidate) {
    require(food.name.isNotBlank()) { "Personal food name cannot be blank." }
    require(food.servingName.isNotBlank()) { "Personal food '${food.name}' has a blank serving name." }
    require(food.calories >= 0 && food.proteinG >= 0 && food.carbsG >= 0 && food.fatG >= 0) {
        "Personal food '${food.name}' has negative macro values."
    }
}
