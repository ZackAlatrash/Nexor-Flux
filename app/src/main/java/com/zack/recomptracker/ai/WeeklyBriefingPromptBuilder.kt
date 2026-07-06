package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.review.WeeklyReviewData

/**
 * Supporting "what the coach noticed this week" note, surfaced from the deterministic proactive
 * engine's WEEKLY-surface winner (see docs/ai-redesign D45). Colour only: the briefing verdict,
 * targets, and every number stay authoritative in [WeeklyReviewData]. The prompt must never let this
 * override or introduce a number.
 */
data class WeeklyCoachNote(val statement: String, val rationale: String?)

class WeeklyBriefingPromptBuilder {

    fun build(data: WeeklyReviewData, coachNote: WeeklyCoachNote? = null): String = buildString {
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
        // SUPPORTING cross-domain training context (Q1b: narrative only — never a new number/verdict).
        data.training?.let { t ->
            appendLine("Training this week (SUPPORTING context — do not change the verdict or invent numbers):")
            appendLine("- ${t.summary}")
            appendLine()
        }
        // SUPPORTING note from the deterministic coach engine's weekly winner (D45): colour only.
        coachNote?.let { note ->
            appendLine("What the coach noticed this week (SUPPORTING colour only — the verdict, targets, and every number above are FINAL; do not override or add numbers from this):")
            appendLine("- ${note.statement}")
            if (!note.rationale.isNullOrBlank()) appendLine("  (${note.rationale})")
            appendLine()
        }
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
