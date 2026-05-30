package com.zack.recomptracker.domain.foodimport

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodNameNormalizerTest {
    @Test
    fun normalizesWhitespaceCaseAndSeparatorPunctuation() {
        assertEquals("greek yogurt", FoodNameNormalizer.normalize("  Greek   Yogurt, "))
    }

    @Test
    fun identityRoundsMacrosToOneDecimalPlace() {
        assertEquals(
            FoodIdentity("greek yogurt", 160, 25.0, 10.0, 2.0),
            FoodImportCandidate(
                externalId = null,
                name = "Greek yogurt",
                servingName = "100g",
                calories = 160,
                proteinG = 25.04,
                carbsG = 10.01,
                fatG = 2.0,
            ).identity(),
        )
    }
}
