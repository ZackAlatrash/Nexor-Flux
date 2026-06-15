package com.zack.recomptracker.ai

import kotlin.math.roundToInt

data class RecoveryInsightContext(
    val sleepHours: Double?,
    val energyScore: Int?,
    val hungerScore: Int?,
    val sorenessScore: Int?,
    val trained: Boolean,
    /** The user's recent personal averages, so today reads as a deviation, not an absolute. */
    val avgSleepHours: Double? = null,
    val avgEnergyScore: Int? = null,
) {
    val hasSufficientData: Boolean
        get() = sleepHours != null || energyScore != null ||
            hungerScore != null || sorenessScore != null

    fun key(): String {
        val sleep = sleepHours?.let { (it * 10).roundToInt() } ?: -1
        return "$sleep|${energyScore ?: -1}|${hungerScore ?: -1}|${sorenessScore ?: -1}|$trained"
    }
}
