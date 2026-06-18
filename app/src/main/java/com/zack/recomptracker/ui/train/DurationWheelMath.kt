package com.zack.recomptracker.ui.train

/** Maximum hours selectable on the duration wheel. */
const val MAX_DURATION_HOURS = 12

/**
 * Splits a duration in seconds into whole (hours, minutes), dropping any
 * sub-minute remainder and clamping hours to [MAX_DURATION_HOURS].
 */
fun durationToHm(seconds: Int): Pair<Int, Int> {
    val safe = seconds.coerceAtLeast(0)
    val hours = (safe / 3600).coerceIn(0, MAX_DURATION_HOURS)
    val minutes = (safe % 3600) / 60
    return hours to minutes
}

/**
 * Combines wheel (hours, minutes) into a duration in seconds, clamping each
 * field to its valid range (hours 0..[MAX_DURATION_HOURS], minutes 0..59).
 */
fun hmToSeconds(hours: Int, minutes: Int): Int =
    (hours.coerceIn(0, MAX_DURATION_HOURS) * 60 + minutes.coerceIn(0, 59)) * 60
