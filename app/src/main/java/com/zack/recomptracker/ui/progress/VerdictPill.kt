package com.zack.recomptracker.ui.progress

import com.zack.recomptracker.ui.component.PillStatus

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
