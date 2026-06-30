package com.zack.recomptracker.ai

/**
 * Pre-computed context for the daily activity (NEAT) insight card: today's steps against the
 * user's goal and recent average. Fully derived before the card is requested, so the card is
 * single-turn with no tool calls.
 */
data class ActivityInsightContext(
    val steps: Int?,
    val stepGoal: Int?,
    val averageDailySteps7: Int?,
) {
    /** Needs a goal to compare against and at least today's steps. */
    val hasSufficientData: Boolean
        get() = stepGoal != null && stepGoal > 0 && steps != null

    /** Bucketed to ~500 steps so the card doesn't regenerate on every minor step update. */
    fun key(): String {
        val s = (steps ?: 0) / 500
        val a = (averageDailySteps7 ?: -1) / 500
        return "$s|${stepGoal ?: -1}|$a"
    }
}
