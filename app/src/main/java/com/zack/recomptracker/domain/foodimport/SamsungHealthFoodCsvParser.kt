package com.zack.recomptracker.domain.foodimport

import java.io.BufferedReader
import java.io.InputStream
import java.util.Locale
import kotlin.math.roundToInt

class SamsungHealthFoodCsvParser {

    fun parse(input: InputStream): List<FoodImportCandidate> = parse(input.bufferedReader())

    fun parse(rawCsv: String): List<FoodImportCandidate> = parse(rawCsv.reader().buffered())

    fun parse(reader: BufferedReader): List<FoodImportCandidate> {
        var lineNumber = 0
        var delimiter = ','
        var columns: Map<Column, Int>? = null

        // Samsung Health exports may include comment/title rows before the real
        // header, and the file is always UTF-8 (often with a BOM).
        while (columns == null) {
            val raw = reader.readLine() ?: break
            lineNumber++
            val line = raw.removeSuffix("\r")
                .let { if (lineNumber == 1) it.removePrefix(BOM) else it }
            if (line.isBlank()) continue

            val candidateDelimiter = detectDelimiter(line)
            val tokens = parseLine(line, candidateDelimiter)
            val indices = ALL_COLUMNS.associateWith { col ->
                tokens.indexOfFirst { normalizeHeader(it) in col.aliases }
            }
            if (indices.getValue(Name) >= 0 && indices.getValue(Calories) >= 0) {
                delimiter = candidateDelimiter
                columns = indices
            } else {
                require(lineNumber <= MAX_SCAN_LINES) {
                    "Samsung Health food CSV: header row not found in the first " +
                        "$MAX_SCAN_LINES lines. Make sure you selected the " +
                        "\"com.samsung.health.food_info.T.csv\" file from your Samsung Health export."
                }
            }
        }

        val cols = requireNotNull(columns) {
            "Samsung Health food CSV: file appears to be empty or has no recognisable header."
        }

        val seenIds = mutableSetOf<String>()
        val foods = mutableListOf<FoodImportCandidate>()

        while (true) {
            val raw = reader.readLine() ?: break
            lineNumber++
            val line = raw.removeSuffix("\r")
            if (line.isBlank()) continue

            val fields = parseLine(line, delimiter)

            val name = fields.getOrNull(cols.getValue(Name))?.trim().orEmpty()
            if (name.isBlank()) continue

            val caloriesRaw = fields.getOrNull(cols.getValue(Calories))?.trim().orEmpty()
            val calories100g = caloriesRaw.toDoubleOrNull()?.roundToInt() ?: continue

            // Samsung Health food_info.T.csv stores all macros per 100 g already.
            // The metric_serving_amount column is the default logging portion
            // (e.g. "1 serving") and must NOT be used as a normalisation factor.
            val protein = nutrient(fields, cols.getValue(Protein))
            val carbs = nutrient(fields, cols.getValue(Carbs))
            val fat = nutrient(fields, cols.getValue(Fat))

            if (calories100g <= 0 && protein == 0.0 && carbs == 0.0 && fat == 0.0) continue

            // Deduplicate by name (case-insensitive) within this import batch.
            val key = name.lowercase(Locale.ROOT)
            if (!seenIds.add(key)) continue

            foods.add(
                FoodImportCandidate(
                    name = name,
                    servingName = "100g",
                    calories = calories100g,
                    proteinG = protein,
                    carbsG = carbs,
                    fatG = fat,
                ),
            )
        }
        return foods
    }

    private fun nutrient(fields: List<String>, index: Int): Double =
        if (index < 0) 0.0
        else fields.getOrNull(index)?.trim()?.toDoubleOrNull() ?: 0.0

    private fun detectDelimiter(line: String): Char =
        listOf(';', ',', '\t').maxByOrNull { d -> line.count { it == d } }
            ?.takeIf { line.contains(it) } ?: ','

    private fun parseLine(line: String, delimiter: Char): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val ch = line[index]) {
                '"' -> if (quoted && line.getOrNull(index + 1) == '"') {
                    current.append('"'); index++
                } else quoted = !quoted
                delimiter -> if (quoted) current.append(ch) else { values += current.toString(); current.clear() }
                else -> current.append(ch)
            }
            index++
        }
        values += current.toString()
        return values
    }

    private fun normalizeHeader(value: String): String =
        value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    private sealed class Column(vararg aliases: String) {
        val aliases: Set<String> = aliases.map { it.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit) }.toSet()
    }

    private object Name : Column(
        "name", "food_name", "title", "item_name", "food name", "item name",
    )
    private object Calories : Column(
        "calorie", "calories", "energy", "kcal", "energy_kcal", "calorie_kcal",
    )
    private object Protein : Column(
        "protein", "proteins", "protein_g", "prot",
    )
    private object Carbs : Column(
        "total_carbohydrate", "carbohydrate", "carbohydrates", "carbs", "cho", "carb",
    )
    private object Fat : Column(
        "total_fat", "fat", "fats", "fat_g",
    )

    private companion object {
        val ALL_COLUMNS = listOf(Name, Calories, Protein, Carbs, Fat)
        const val MAX_SCAN_LINES = 50
        const val BOM = "\uFEFF"
    }
}
