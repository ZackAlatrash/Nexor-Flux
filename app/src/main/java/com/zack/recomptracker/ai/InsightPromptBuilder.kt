package com.zack.recomptracker.ai

import java.util.Locale

class InsightPromptBuilder {

    fun buildWeeklySummaryPrompt(context: InsightContext): String = buildString {
        appendLine("You are a concise nutrition coach explaining a weekly calorie verdict to an athlete.")
        appendLine("Write exactly 2–3 sentences in plain English. Do not change the verdict.")
        appendLine("Explain the reasoning — do not repeat raw numbers already shown on screen.")
        appendLine("Be specific about which signals drove the decision. Keep the tone calm and direct.")
        appendLine()
        val sign = if (context.result.recommendedCalorieChange >= 0) "+" else ""
        appendLine("Verdict: ${context.result.verdict.name} ($sign${context.result.recommendedCalorieChange} kcal)")
        appendLine("Reason: ${context.result.reasonCodes.joinToString()}")
        appendLine()
        appendLine("Signals this week:")
        appendLine("- Weight trend: ${String.format(Locale.US, "%.2f", context.input.weightTrendKgPerWeek)} kg/week")
        appendLine("- Waist trend: ${String.format(Locale.US, "%.2f", context.input.waistTrendCmPerWeek)} cm/week")
        appendLine("- Performance: ${context.input.performanceTrend}")
        appendLine("- Recovery: ${context.input.recoveryTrend}")
        appendLine("- Adherence: ${String.format(Locale.US, "%.0f", context.input.adherencePercent)}% over ${context.input.daysLogged} days")
        appendLine("- Weeks in current phase: ${context.input.weeksSincePhaseStart}")
        appendLine()
        appendLine("Calorie target: ${context.targetCalories} kcal | Protein target: ${context.targetProteinG}g")
    }
}
