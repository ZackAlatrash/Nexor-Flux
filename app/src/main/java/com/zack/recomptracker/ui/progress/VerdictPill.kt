package com.zack.recomptracker.ui.progress

/**
 * Status of a chart's trend verdict, rendered as a colored pill on the Trends screen.
 *
 * CAUTION is reserved for future "slightly over/under target" logic — no current input
 * produces it, but it is kept in the enum (and given a color) so the rendering path is
 * ready when that signal is added.
 */
enum class PillStatus { GOOD, CAUTION, OFF_TRACK, NEUTRAL }

/**
 * Pure mapping from a [ChartSeries] trend signal to a [PillStatus].
 *
 * @param trendIsGood whether the trend direction is favourable for this metric.
 * @param isNeutral whether there is no meaningful trend to report (no data / flat /
 *   blank trend label). Derived at the call site from the existing [ChartSeries] fields
 *   (the series has no dedicated flat flag).
 */
fun pillStatus(trendIsGood: Boolean, isNeutral: Boolean): PillStatus = when {
    isNeutral -> PillStatus.NEUTRAL
    trendIsGood -> PillStatus.GOOD
    else -> PillStatus.OFF_TRACK
}
