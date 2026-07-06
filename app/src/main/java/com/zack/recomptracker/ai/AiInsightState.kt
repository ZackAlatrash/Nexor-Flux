package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult

sealed class AiInsightState {
    object Disabled : AiInsightState()
    object ModelReady : AiInsightState()
    object LoadingModel : AiInsightState()
    data class Generating(val partialText: String) : AiInsightState()
    data class Ready(val text: String) : AiInsightState()
    data class Error(val message: String) : AiInsightState()
}

fun AdjustmentResult.key(): String =
    "${verdict.name}|${reasonCodes.joinToString(",")}|$recommendedCalorieChange"
