package com.zack.recomptracker.domain.foodimport

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoricalFoodImporterTest {
    private val yogurt = FoodImportCandidate(null, "Greek yogurt", "100g", 160, 25.0, 10.0, 2.0)
    private val oats = FoodImportCandidate(null, "Oats", "100g", 370, 13.0, 60.0, 7.0)

    @Test
    fun returnsOnlyUniqueFoodsMissingFromPersonalLibrary() {
        val candidates = HistoricalFoodImporter.reviewCandidates(
            existing = listOf(yogurt),
            history = listOf(yogurt.copy(name = " greek  yogurt "), oats, oats.copy(name = "OATS")),
        )

        assertEquals(listOf(oats), candidates)
    }

    @Test
    fun ignoresBlankNames() {
        val candidates = HistoricalFoodImporter.reviewCandidates(
            existing = emptyList(),
            history = listOf(oats.copy(name = "  ")),
        )

        assertEquals(emptyList<FoodImportCandidate>(), candidates)
    }
}
