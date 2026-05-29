package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult

class InsightPromptBuilder {
    fun buildWeeklySummaryPrompt(result: AdjustmentResult): String {
        return buildString {
            appendLine("Explain this deterministic calorie verdict without changing it.")
            appendLine("Verdict: ${result.verdict}")
            appendLine("Recommended calorie change: ${result.recommendedCalorieChange}")
            appendLine("Reason codes: ${result.reasonCodes.joinToString()}")
            appendLine("Summary: ${result.summary}")
        }
    }
}
