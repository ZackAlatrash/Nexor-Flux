package com.zack.recomptracker.domain.activity

/**
 * Derived, display-ready activity figures shared across screens (computed via [ActivitySummary]).
 *
 * @param weeklyTrainingFrequency actual training sessions per week over the trailing 4 weeks.
 * @param weeklyGymSessionsTarget the user's gym-sessions-per-week target, or null if unset.
 * @param averageDailySteps7 mean steps over the last 7 days, or null if none logged.
 */
data class ActivityMetrics(
    val weeklyTrainingFrequency: Double = 0.0,
    val weeklyGymSessionsTarget: Int? = null,
    val averageDailySteps7: Int? = null,
)
