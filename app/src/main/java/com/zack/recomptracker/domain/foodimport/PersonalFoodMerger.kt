package com.zack.recomptracker.domain.foodimport

data class PersonalFoodMergeResult(
    val foodsToInsert: List<FoodImportCandidate>,
    val duplicateCount: Int,
)

object PersonalFoodMerger {
    fun newFoods(
        existing: List<FoodImportCandidate>,
        incoming: List<FoodImportCandidate>,
    ): PersonalFoodMergeResult {
        val identities = existing.onEach(::validatePersonalFood)
            .mapTo(mutableSetOf(), FoodImportCandidate::identity)
        val foodsToInsert = mutableListOf<FoodImportCandidate>()
        var duplicateCount = 0
        incoming.forEach { food ->
            validatePersonalFood(food)
            if (identities.add(food.identity())) {
                foodsToInsert += food
            } else {
                duplicateCount++
            }
        }
        return PersonalFoodMergeResult(foodsToInsert, duplicateCount)
    }
}
