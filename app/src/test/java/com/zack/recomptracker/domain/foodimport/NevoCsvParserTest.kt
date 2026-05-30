package com.zack.recomptracker.domain.foodimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NevoCsvParserTest {
    private val parser = NevoCsvParser()

    @Test
    fun parsesSemicolonCsvWithDutchHeadersAndDecimalCommas() {
        val foods = parser.parse(
            "NEVO-code;Voedingsmiddelnaam Nederlands;ENERCC kcal;PROT g;CHO g;FAT g\n" +
                "123;Halfvolle melk;47;3,5;4,8;1,5",
        )

        assertEquals(
            FoodImportCandidate("123", "Halfvolle melk", "100g", 47, 3.5, 4.8, 1.5),
            foods.single(),
        )
    }

    @Test
    fun parsesCommaCsvWithQuotedNameAndDecimalPoints() {
        val foods = parser.parse(
            "NEVO code,Food name Dutch,Energy kcal,Protein g,Carbohydrates g,Fat g\n" +
                "456,\"Yoghurt, halfvol\",60,4.1,5.2,2.4",
        )

        assertEquals("Yoghurt, halfvol", foods.single().name)
    }

    @Test
    fun skipsPreambleMetadataRowsBeforeHeader() {
        val foods = parser.parse(
            "NEVO online 2025/9.0\n" +
                "Gegenereerd op 2026-05-30\n" +
                "\n" +
                "NEVO-code;Voedingsmiddelnaam Nederlands;ENERCC kcal;PROT g;CHO g;FAT g\n" +
                "123;Halfvolle melk;47;3,5;4,8;1,5",
        )

        assertEquals(
            FoodImportCandidate("123", "Halfvolle melk", "100g", 47, 3.5, 4.8, 1.5),
            foods.single(),
        )
    }

    @Test
    fun stripsByteOrderMarkFromFirstColumn() {
        val foods = parser.parse(
            "﻿NEVO-code;Voedingsmiddelnaam Nederlands;ENERCC kcal;PROT g;CHO g;FAT g\n" +
                "123;Halfvolle melk;47;3,5;4,8;1,5",
        )

        assertEquals("123", foods.single().externalId)
    }

    @Test
    fun reportsDiagnosticWhenNoHeaderRowFound() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parser.parse("just some text\nmore text\nnothing useful here")
        }

        assertEquals(true, error.message!!.contains("header row not found", ignoreCase = true))
    }

    @Test
    fun parsesNevo2025LongFormatWithPipeDelimiterAndDutchDecimals() {
        val header = "\"NEVO-versie/NEVO-version\"|\"Voedingsmiddelgroep\"|\"Food group\"|" +
            "\"NEVO-code\"|\"Voedingsmiddelnaam/Dutch food name\"|\"Engelse naam/Food name\"|" +
            "\"Hoeveelheid/Quantity\"|\"Voedingsstofgroep\"|\"Component group\"|" +
            "\"Nutrient-code\"|\"Voedingsstof\"|\"Component\"|\"Gehalte/Value\"|\"Eenheid/Unit\""
        val row = { code: String, nc: String, value: String ->
            "\"NEVO-Online 2025 9.0\"|\"Aardappelen\"|\"Potatoes\"|\"$code\"|\"Aardappel rauw\"|" +
                "\"Potato raw\"|\"per 100g\"|\"Energie\"|\"Energy\"|\"$nc\"|\"n\"|\"n\"|\"$value\"|\"g\""
        }
        val csv = listOf(
            header,
            row("1", "ENERCC", "\"88\""),
            row("1", "PROT",   "\"2\""),
            row("1", "CHO",    "\"19\""),
            row("1", "FAT",    "\"0\""),
            row("1", "WATER",  "\"77\""),   // ignored nutrient
        ).joinToString("\n")

        val foods = parser.parse(csv)

        assertEquals(1, foods.size)
        with(foods.single()) {
            assertEquals("1",           externalId)
            assertEquals("Aardappel rauw", name)
            assertEquals(88,             calories)
            assertEquals(2.0,            proteinG, 0.01)
            assertEquals(19.0,           carbsG,   0.01)
            assertEquals(0.0,            fatG,     0.01)
        }
    }

    @Test
    fun rejectsMissingRequiredHeaders() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parser.parse("NEVO-code;Voedingsmiddelnaam Nederlands\n123;Melk")
        }

        assertEquals("NEVO CSV is missing required columns: calories, protein, carbohydrates, fat", error.message)
    }

    @Test
    fun rejectsMalformedNumericRows() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parser.parse(
                "NEVO-code;Voedingsmiddelnaam Nederlands;ENERCC kcal;PROT g;CHO g;FAT g\n" +
                    "123;Halfvolle melk;not-a-number;3,5;4,8;1,5",
            )
        }

        assertEquals("NEVO CSV row 2 has invalid calories: not-a-number", error.message)
    }

    @Test
    fun rejectsDuplicateNevoCodes() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parser.parse(
                "NEVO-code;Voedingsmiddelnaam Nederlands;ENERCC kcal;PROT g;CHO g;FAT g\n" +
                    "123;Halfvolle melk;47;3,5;4,8;1,5\n" +
                    "123;Halfvolle melk;47;3,5;4,8;1,5",
            )
        }

        assertEquals("NEVO CSV contains duplicate NEVO code: 123", error.message)
    }
}
