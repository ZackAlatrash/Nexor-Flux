package com.zack.recomptracker.ui.onboarding

import java.time.LocalDate
import java.time.Period

private const val CM_PER_INCH = 2.54
private const val KG_PER_POUND = 0.45359237
private const val MIN_AGE = 13
private const val MAX_AGE = 120

/** Height in canonical centimetres from raw input. Metric = cm; imperial = whole inches. */
internal fun parseHeightCm(input: String, metric: Boolean): Int? {
    val value = input.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    val cm = if (metric) value else value * CM_PER_INCH
    return Math.round(cm).toInt()
}

/** Weight in canonical kilograms from raw input. Metric = kg; imperial = pounds. */
internal fun parseWeightKg(input: String, metric: Boolean): Double? {
    val value = input.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    return if (metric) value else value * KG_PER_POUND
}

/** Waist in canonical centimetres from raw input. Metric = cm; imperial = inches. Optional → null. */
internal fun parseWaistCm(input: String, metric: Boolean): Double? {
    val value = input.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    return if (metric) value else value * CM_PER_INCH
}

/** Whole years from an ISO `yyyy-MM-dd` birth date, or null if unset/unparseable/future. */
internal fun ageYearsFrom(birthDate: String?, today: LocalDate): Int? {
    val dob = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    if (dob.isAfter(today)) return null
    return Period.between(dob, today).years
}

/** A birth date that parses, is not in the future, and yields a plausible age. */
internal fun isValidBirthDate(birthDate: String?, today: LocalDate): Boolean {
    val age = ageYearsFrom(birthDate, today) ?: return false
    return age in MIN_AGE..MAX_AGE
}
