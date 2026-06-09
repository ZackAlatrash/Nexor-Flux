package com.zack.recomptracker.ai

data class RecoveryInsightContext(
    val sleepHours: Double?,
    val energyScore: Int?,
    val hungerScore: Int?,
    val sorenessScore: Int?,
    val trained: Boolean,
) {
    val hasSufficientData: Boolean
        get() = sleepHours != null || energyScore != null ||
            hungerScore != null || sorenessScore != null

    fun key(): String =
        "${sleepHours ?: -1.0}|${energyScore ?: -1}|${hungerScore ?: -1}|${sorenessScore ?: -1}|$trained"
}
