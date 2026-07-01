package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.coach.CoachSignal

/**
 * Builds the cloud phrasing prompt for a single [CoachSignal]. The deterministic engine has already
 * decided *what* to say and computed *every* number; this prompt asks the model to do exactly one
 * job — rephrase the verdict in warm, plain language — and forbids it from inventing or changing any
 * figure. See `docs/ai-redesign/08-technical-architecture.md` §3/§4 ("engine facts in, phrasing out;
 * the model never introduces a number absent from the payload").
 *
 * Pure string assembly — no Android, no network. Deliberately references no `Gemma*`/`Routing*`/
 * `ModelVariant`/`AiBackend` class (boundary rule, invariant #7).
 */
internal object CoachPhrasingPromptBuilder {

    const val SYSTEM_PROMPT: String =
        "You are a supportive fitness coach. Rewrite the given decision in 1-2 short, warm, plain " +
            "sentences. Use ONLY the numbers provided; never introduce a number that isn't given; " +
            "never change the recommendation. Reply with ONLY the sentence(s), no preamble."

    /** Assembles the user prompt: the verdict, the labelled engine facts, and the cross-domain why. */
    fun buildUserPrompt(signal: CoachSignal): String = buildString {
        appendLine("Decision to rephrase:")
        appendLine(signal.verdict)

        if (signal.facts.values.isNotEmpty()) {
            appendLine()
            appendLine("Numbers you may use (do not add any others):")
            signal.facts.values.forEach { (label, value) ->
                appendLine("- $label: $value")
            }
        }

        val causes = signal.rationale.primaryCauseByDomain
        if (causes.isNotEmpty()) {
            appendLine()
            appendLine("Why (context, do not add numbers):")
            causes.forEach { (domain, cause) ->
                appendLine("- $domain: $cause")
            }
        }
    }.trim()
}
