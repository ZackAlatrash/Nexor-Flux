package com.zack.recomptracker.core.util

import java.util.Locale
import kotlin.math.roundToInt

fun Double.formatOneDecimal(): String = String.format(Locale.US, "%.1f", this)

fun Double.formatSignedOneDecimal(): String = String.format(Locale.US, "%+.1f", this)

fun Double.formatPercent(): String = "${roundToInt()}%"

fun String.toNullableDouble(): Double? = trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()

fun String.toNullableInt(): Int? = trim().takeIf { it.isNotBlank() }?.toIntOrNull()
