package com.zack.recomptracker.ui.train.component

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Small wrapper that fires the three reorder-drag haptics through the host [View].
 * All constants used are available since API 23 (minSdk is 26).
 */
class DragHaptics(private val view: View) {
    /** Long-press engaged — drag begins. */
    fun start() = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

    /** Item crossed a neighbour during the drag. */
    fun move() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    /** Item dropped. */
    fun end() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

@Composable
fun rememberDragHaptics(): DragHaptics {
    val view = LocalView.current
    return remember(view) { DragHaptics(view) }
}
