package com.zack.recomptracker.domain.foodimport

import java.util.Locale

object FoodNameNormalizer {
    fun normalize(value: String): String = value
        .trim()
        .trim { it.isWhitespace() || it in SEPARATOR_PUNCTUATION }
        .lowercase(Locale.ROOT)
        .replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
    private val SEPARATOR_PUNCTUATION = setOf(',', ';', ':', '-', '_', '|')
}
