package com.zack.recomptracker.ui.train

/**
 * Pure, deterministic formatting for the session-summary "Live PR Callout". Turns an already-flagged
 * PR exercise (see [ExerciseRecap.isPr], computed in [SessionSummaryViewModel]) into a forward-looking
 * line — not just a trophy. No AI, no Android imports; reuses the recap data as-is.
 */
object PrCalloutFormatter {

    /**
     * @param exerciseName the exercise that set a record (e.g. "Bench Press").
     * @param topSet the recap's heaviest-set label (e.g. "80×5"), possibly blank.
     * @return a headline + forward action line for the callout.
     */
    fun format(exerciseName: String, topSet: String): PrCallout {
        val record = topSet.takeIf { it.isNotBlank() }
        val headline = if (record != null) {
            "New $exerciseName record — $record"
        } else {
            "New $exerciseName record"
        }
        val forwardLine = "Logged. It'll raise next week's target for this lift."
        return PrCallout(headline = headline, forwardLine = forwardLine)
    }

    data class PrCallout(val headline: String, val forwardLine: String)
}
