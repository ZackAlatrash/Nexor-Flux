package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.review.WeeklyReviewData

class WeeklyBriefingPromptBuilder {

    fun build(data: WeeklyReviewData): String = buildString {
        appendLine("You are a precise, supportive body-recomposition coach writing a user's WEEKLY briefing.")
        appendLine("All numbers and the verdict below are FINAL and computed by the app. Do not change, recompute, or contradict them. Write prose only.")
        appendLine()
        appendLine("Phase: ${data.phase.name} (${if (data.phase.name == "EARLY") "early read — too soon to change calories" else "full review"})")
        appendLine("Verdict: ${data.verdictLabel}")
        appendLine("Days logged: ${data.daysLogged} | Adherence: ${data.input.adherencePercent.toInt()}%")
        appendLine("Recommended calorie change: ${data.result.recommendedCalorieChange} kcal")
        appendLine("Engine summary (for your understanding): ${data.result.summary}")
        appendLine()
        appendLine("Signals (id — value — direction):")
        data.signals.forEach { appendLine("- \"${it.id}\" — ${it.value} — ${it.direction.name}") }
        appendLine()
        appendLine("Return ONLY a JSON object with EXACTLY these keys and no markdown:")
        appendLine("""{""")
        appendLine(""""headline": one vivid sentence stating the verdict and why,""")
        appendLine(""""narrative": 2-3 sentences summarizing the week,""")
        appendLine(""""interpretations": an object with a one-sentence reading for each signal id: weight, waist, adherence, strength, recovery,""")
        appendLine(""""action_rationale": 1-2 sentences explaining the recommended action,""")
        appendLine(""""watch_next": one sentence on what to watch next week""")
        append("}")
        appendLine()
        if (data.phase.name == "EARLY") {
            append("This is an early read: in action_rationale, make clear it is too soon to change calories and to keep logging.")
        } else {
            append("Be direct and concrete. Reference the actual numbers above without altering them.")
        }
    }
}
