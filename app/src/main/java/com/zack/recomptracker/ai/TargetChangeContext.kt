package com.zack.recomptracker.ai

/**
 * Explains why this week's verdict moves the calorie target — the MacroFactor-style
 * "why did my target change?" card.
 *
 * old/new are computed live: [oldTarget] is the current plan target and [newTarget] is
 * `oldTarget + recommendedCalorieChange`. No target history is persisted, so this needs no
 * Room migration. Fires only when the verdict actually changes the target ([hasSufficientData]).
 */
data class TargetChangeContext(
    val oldTarget: Int,
    val newTarget: Int,
    val weightTrendKgPerWeek: Double,
    val adherencePercent: Double,
    val reasonCodes: List<String>,
    val desiredWeeklyRateKg: Double? = null,
) {
    val hasSufficientData: Boolean get() = oldTarget != newTarget

    fun key(): String = "$oldTarget|$newTarget"
}
