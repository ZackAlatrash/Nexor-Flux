package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.adjustment.AdjustmentResult

class InsightPromptBuilder {

    fun buildWeeklySummaryPrompt(result: AdjustmentResult): String = buildString {
        appendLine("You are a concise nutrition coach explaining a weekly calorie verdict to an athlete.")
        appendLine("Write exactly 2–3 sentences in plain English. Do not change the verdict.")
        appendLine("Explain the reasoning behind it — do not repeat the raw numbers already shown on screen.")
        appendLine("Be specific about which signals drove the decision. Keep the tone calm and direct.")
        appendLine()
        appendLine("Verdict: ${result.verdict.name}")
        appendLine("Reason codes: ${result.reasonCodes.joinToString()}")
    }
}
