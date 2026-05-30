package com.zack.recomptracker.domain.foodimport

object HistoricalFoodImporter {
    fun reviewCandidates(
        existing: List<FoodImportCandidate>,
        history: List<FoodImportCandidate>,
    ): List<FoodImportCandidate> = PersonalFoodMerger.newFoods(
        existing = existing,
        incoming = history.filter { it.name.isNotBlank() },
    ).foodsToInsert
}
