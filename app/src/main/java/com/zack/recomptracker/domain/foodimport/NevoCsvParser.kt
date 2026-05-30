package com.zack.recomptracker.domain.foodimport

import java.io.BufferedReader
import java.io.InputStream
import java.util.Locale
import kotlin.math.roundToInt

class NevoCsvParser {
    fun parse(rawCsv: String): List<FoodImportCandidate> = parse(rawCsv.reader().buffered())

    fun parse(input: InputStream): List<FoodImportCandidate> = parse(input.bufferedReader())

    fun parse(reader: BufferedReader): List<FoodImportCandidate> {
        var lineNumber = 0
        val examinedLines = mutableListOf<String>()

        // Scan forward (skipping BOM / metadata rows) until we find the header.
        // NEVO 2025 v9.0 uses a pipe-delimited long/tidy format where each row is
        // one nutrient for one food.  Older or custom exports may use wide format
        // (one row per food, one column per nutrient).  We detect the format by
        // the presence of a "Nutrient-code" column in the header.
        while (true) {
            val raw = reader.readLine() ?: break
            lineNumber++
            val line = raw.removeSuffix("\r")
                .let { if (lineNumber == 1) it.removePrefix(BOM) else it }
            if (line.isBlank()) continue

            val delimiter = detectDelimiter(line)
            val tokens = parseLine(line, delimiter)

            val nevoCodeIdx = tokens.indexOfNormalized(ExternalIdAliases)
            val nameIdx     = tokens.indexOfNormalized(NameAliases)

            if (nevoCodeIdx < 0 || nameIdx < 0) {
                if (examinedLines.size < HEADER_DIAGNOSTIC_LINES) examinedLines += line
                require(lineNumber <= MAX_HEADER_SCAN_LINES) {
                    headerNotFoundMessage(examinedLines)
                }
                continue
            }

            // Found header — decide format.
            val nutrientCodeIdx = tokens.indexOfNormalized(NutrientCodeAliases)

            return if (nutrientCodeIdx >= 0) {
                // Long format (NEVO 2025 Details): every row is one nutrient value.
                val valueIdx = tokens.indexOfNormalized(NutrientValueAliases)
                require(valueIdx >= 0) {
                    "NEVO long-format CSV has a Nutrient-code column but no value column " +
                        "(expected e.g. \"Gehalte/Value\")."
                }
                parseLongFormat(reader, lineNumber, delimiter, nevoCodeIdx, nameIdx, nutrientCodeIdx, valueIdx)
            } else {
                // Wide format: one row per food, separate column per nutrient.
                val caloriesIdx = tokens.indexOfNormalized(CaloriesAliases)
                val proteinIdx  = tokens.indexOfNormalized(ProteinAliases)
                val carbsIdx    = tokens.indexOfNormalized(CarbsAliases)
                val fatIdx      = tokens.indexOfNormalized(FatAliases)
                val missing = buildList {
                    if (caloriesIdx < 0) add("calories")
                    if (proteinIdx < 0)  add("protein")
                    if (carbsIdx < 0)    add("carbohydrates")
                    if (fatIdx < 0)      add("fat")
                }
                require(missing.isEmpty()) {
                    "NEVO CSV is missing required columns: ${missing.joinToString()}"
                }
                parseWideFormat(reader, lineNumber, delimiter,
                    nevoCodeIdx, nameIdx, caloriesIdx, proteinIdx, carbsIdx, fatIdx)
            }
        }

        throw IllegalArgumentException(headerNotFoundMessage(examinedLines))
    }

    // ---------------------------------------------------------------------------
    // Long format (NEVO 2025 v9.0 Details)
    // ---------------------------------------------------------------------------

    private fun parseLongFormat(
        reader: BufferedReader,
        startLine: Int,
        delimiter: Char,
        nevoCodeIdx: Int,
        nameIdx: Int,
        nutrientCodeIdx: Int,
        valueIdx: Int,
    ): List<FoodImportCandidate> {
        // Accumulate nutrient values keyed by food code.
        // LinkedHashMap preserves insertion order so output order matches the file.
        val foodNames   = LinkedHashMap<String, String>()
        val nutrientMap = HashMap<String, HashMap<String, Double>>() // code -> (nutrientCode -> value)
        var lineNumber  = startLine

        reader.forEachLine { rawLine ->
            lineNumber++
            val line = rawLine.removeSuffix("\r")
            if (line.isBlank()) return@forEachLine

            val fields = parseLine(line, delimiter)
            val code   = fields.getOrNull(nevoCodeIdx)?.trim() ?: return@forEachLine
            val name   = fields.getOrNull(nameIdx)?.trim()     ?: return@forEachLine
            if (code.isBlank() || name.isBlank()) return@forEachLine

            val nc    = fields.getOrNull(nutrientCodeIdx)?.trim() ?: return@forEachLine
            val raw   = fields.getOrNull(valueIdx)?.trim()        ?: return@forEachLine
            // Values may be quoted ("88") and use Dutch decimal commas ("1,8").
            val value = raw.replace(',', '.').toDoubleOrNull()     ?: return@forEachLine

            foodNames.putIfAbsent(code, name)
            nutrientMap.getOrPut(code) { HashMap() }[nc] = value
        }

        require(foodNames.isNotEmpty()) { "NEVO CSV contains no foods." }

        val foods = mutableListOf<FoodImportCandidate>()
        for ((code, name) in foodNames) {
            val n        = nutrientMap[code] ?: continue
            val calories = n[NC_ENERCC]                                    ?: continue
            val protein  = n[NC_PROT]  ?: n[NC_PROTPL]                    ?: 0.0
            val carbs    = n[NC_CHO]   ?: n[NC_CHOAVL]                    ?: 0.0
            val fat      = n[NC_FAT]                                       ?: 0.0

            foods.add(
                FoodImportCandidate(
                    externalId  = code,
                    name        = name,
                    servingName = "100g",
                    calories    = calories.roundToInt(),
                    proteinG    = protein,
                    carbsG      = carbs,
                    fatG        = fat,
                ),
            )
        }
        require(foods.isNotEmpty()) { "NEVO CSV contains no foods with the required nutrients." }
        return foods
    }

    // ---------------------------------------------------------------------------
    // Wide format (older NEVO or custom exports)
    // ---------------------------------------------------------------------------

    private fun parseWideFormat(
        reader: BufferedReader,
        startLine: Int,
        delimiter: Char,
        nevoCodeIdx: Int,
        nameIdx: Int,
        caloriesIdx: Int,
        proteinIdx: Int,
        carbsIdx: Int,
        fatIdx: Int,
    ): List<FoodImportCandidate> {
        val seenCodes  = mutableSetOf<String>()
        val foods      = mutableListOf<FoodImportCandidate>()
        var lineNumber = startLine

        reader.forEachLine { rawLine ->
            lineNumber++
            val line = rawLine.removeSuffix("\r")
            if (line.isBlank()) return@forEachLine

            val fields = parseLine(line, delimiter)
            val code   = fields.requireField(nevoCodeIdx, lineNumber, "NEVO code").trim()
            val name   = fields.requireField(nameIdx,     lineNumber, "food name").trim()
            require(code.isNotBlank()) { "NEVO CSV row $lineNumber has a blank NEVO code." }
            require(name.isNotBlank()) { "NEVO CSV row $lineNumber has a blank food name." }
            require(seenCodes.add(code)) { "NEVO CSV contains duplicate NEVO code: $code" }

            val calories = fields.requireNumber(caloriesIdx, lineNumber, "calories")
            val protein  = fields.parseNumber(proteinIdx)  ?: 0.0
            val carbs    = fields.parseNumber(carbsIdx)    ?: 0.0
            val fat      = fields.parseNumber(fatIdx)      ?: 0.0

            foods.add(
                FoodImportCandidate(
                    externalId  = code,
                    name        = name,
                    servingName = "100g",
                    calories    = calories.roundToInt(),
                    proteinG    = protein,
                    carbsG      = carbs,
                    fatG        = fat,
                ),
            )
        }
        require(foods.isNotEmpty()) { "NEVO CSV contains no foods." }
        return foods
    }

    private fun List<String>.requireField(index: Int, row: Int, label: String): String =
        getOrNull(index) ?: throw IllegalArgumentException("NEVO CSV row $row is missing $label.")

    private fun List<String>.requireNumber(index: Int, row: Int, label: String): Double {
        val raw = requireField(index, row, label).trim()
        return raw.replace(',', '.').toDoubleOrNull()
            ?.also { require(it >= 0.0) { "NEVO CSV row $row has negative $label: $raw" } }
            ?: throw IllegalArgumentException("NEVO CSV row $row has invalid $label: $raw")
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun List<String>.indexOfNormalized(aliases: Set<String>): Int =
        indexOfFirst { normalizeHeader(it) in aliases }

    private fun List<String>.parseNumber(index: Int): Double? {
        val raw = getOrNull(index)?.trim() ?: return null
        return raw.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 }
    }

    private fun headerNotFoundMessage(examinedLines: List<String>): String {
        val preview = examinedLines.joinToString(" | ") { it.take(80) }
        return "NEVO CSV header row not found. Expected a row with \"NEVO-code\" and a food-name " +
            "column. First lines seen: $preview"
    }

    private fun detectDelimiter(line: String): Char =
        DELIMITERS.maxByOrNull { d -> line.count { it == d } }
            ?.takeIf { line.contains(it) } ?: ','

    private fun parseLine(line: String, delimiter: Char): List<String> {
        val values  = mutableListOf<String>()
        val current = StringBuilder()
        var quoted  = false
        var index   = 0
        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                    current.append('"'); index++
                }
                ch == '"'               -> quoted = !quoted
                ch == delimiter && !quoted -> { values += current.toString(); current.clear() }
                else                    -> current.append(ch)
            }
            index++
        }
        values += current.toString()
        return values
    }

    private fun normalizeHeader(value: String): String =
        value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    private companion object {
        // Nutrient codes used in NEVO long-format files.
        const val NC_ENERCC = "ENERCC"   // energy kcal
        const val NC_PROT   = "PROT"     // protein (total)
        const val NC_PROTPL = "PROTPL"   // protein (plant, fallback)
        const val NC_CHO    = "CHO"      // carbohydrates
        const val NC_CHOAVL = "CHOAVL"   // available carbohydrates (fallback)
        const val NC_FAT    = "FAT"      // total fat

        // Wide-format column aliases (normalised — all lowercase, letters+digits only).
        private fun aliases(vararg raw: String) =
            raw.map { it.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit) }.toSet()

        val ExternalIdAliases = aliases("NEVO-code", "NEVO code", "NEVO_CODE", "NEVOcode")
        val NameAliases = aliases(
            "Voedingsmiddelnaam Nederlands",
            "Voedingsmiddelnaam",
            "Voedingsmiddelnaam/Dutch food name",
            "Food name Dutch",
            "Food name English",
            "Food name",
            "Naam voedingsmiddel",
            "Engelse naam",
            "Engelse naam/Food name",
        )
        val NutrientCodeAliases = aliases(
            "Nutrient-code", "Nutrient code", "NutrientCode",
            "Nutrientcode", "Nutrient_code",
        )
        val NutrientValueAliases = aliases(
            "Gehalte/Value", "Gehalte", "Value", "Waarde",
        )

        // Wide-format nutrient column aliases.
        val CaloriesAliases = aliases(
            "ENERCC kcal", "Energy kcal", "Energie kcal",
            "Calories", "kcal", "ENERCC",
        )
        val ProteinAliases = aliases(
            "PROT g", "Protein g", "Eiwit g", "PROCNT g",
            "Protein", "Eiwit", "PROT", "PROCNT",
        )
        val CarbsAliases = aliases(
            "CHO g", "Carbohydrates g", "Koolhydraten g",
            "CHOAVL g", "Carbohydrates", "Koolhydraten", "CHO", "CHOAVL",
        )
        val FatAliases = aliases(
            "FAT g", "Fat g", "Vet g",
            "Fat", "Vet", "FAT",
        )

        val DELIMITERS = listOf('|', ';', ',', '\t')
        const val BOM = "\uFEFF"
        const val MAX_HEADER_SCAN_LINES = 100
        const val HEADER_DIAGNOSTIC_LINES = 5
    }
}
