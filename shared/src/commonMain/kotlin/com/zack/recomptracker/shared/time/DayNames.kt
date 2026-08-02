package com.zack.recomptracker.shared.time

import kotlinx.datetime.DayOfWeek

/**
 * Replaces `DayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)`, which is JVM-only.
 * The app renders these US-English names in insight copy; there is no localisation today.
 */
fun DayOfWeek.fullNameEnglish(): String = when (this) {
    DayOfWeek.MONDAY -> "Monday"
    DayOfWeek.TUESDAY -> "Tuesday"
    DayOfWeek.WEDNESDAY -> "Wednesday"
    DayOfWeek.THURSDAY -> "Thursday"
    DayOfWeek.FRIDAY -> "Friday"
    DayOfWeek.SATURDAY -> "Saturday"
    DayOfWeek.SUNDAY -> "Sunday"
}
