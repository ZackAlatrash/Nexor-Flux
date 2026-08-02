package com.zack.recomptracker.shared.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * ISO-8601 week stamp, e.g. "2026-W27". Replaces
 * `date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)` + `WEEK_BASED_YEAR`, which are JVM-only.
 *
 * ISO rules: weeks start Monday; week 1 is the week containing the first Thursday of the year.
 * Consequence: early-January dates can belong to week 52/53 of the *previous* week-based year,
 * and late-December dates to week 1 of the *next* one. Pinned by GoldenFormatTest.
 */
fun isoWeek(date: LocalDate): String {
    // The Thursday of this date's week determines both the week-based year and the week number.
    val monday = date.minus(date.mondayFirstIndex, DateTimeUnit.DAY)
    val thursday = monday.plus(3, DateTimeUnit.DAY)

    val weekBasedYear = thursday.year
    val jan1 = LocalDate(weekBasedYear, 1, 1)
    val week1Monday = jan1
        .minus(jan1.mondayFirstIndex, DateTimeUnit.DAY)
        .let { if (it.plus(3, DateTimeUnit.DAY).year < weekBasedYear) it.plus(7, DateTimeUnit.DAY) else it }

    val week = week1Monday.daysUntil(monday) / 7 + 1

    return "${weekBasedYear.toString().padStart(4, '0')}-W${week.toString().padStart(2, '0')}"
}

/**
 * 0 for Monday … 6 for Sunday. Deliberately NOT named `isoDayNumber` — kotlinx-datetime ships an
 * extension property by that name and shadowing it produces confusing resolution errors.
 */
private val LocalDate.mondayFirstIndex: Int
    get() = when (dayOfWeek) {
        DayOfWeek.MONDAY -> 0
        DayOfWeek.TUESDAY -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY -> 3
        DayOfWeek.FRIDAY -> 4
        DayOfWeek.SATURDAY -> 5
        else -> 6
    }
