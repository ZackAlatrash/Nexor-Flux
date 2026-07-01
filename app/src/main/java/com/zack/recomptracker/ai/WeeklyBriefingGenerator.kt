package com.zack.recomptracker.ai

import com.zack.recomptracker.ai.knowledge.KnowledgeInjector
import com.zack.recomptracker.ai.knowledge.NoOpKnowledgeInjector
import com.zack.recomptracker.data.coach.CoachJourney
import com.zack.recomptracker.data.coach.NoopCoachJourney
import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.domain.review.WeeklyReviewData

/**
 * Cloud-only generator. Asks the model for prose (BriefingNarration), then merges that prose onto
 * the deterministic [WeeklyReviewData] skeleton so numbers/verdict can never be altered by the model.
 *
 * The narration prompt is grounded in the knowledge corpus the same way chat is: a REFERENCE block
 * (from [knowledgeInjector]) is prepended to the prompt so the model can phrase from the corpus. The
 * deterministic skeleton — numbers and verdict — is untouched; grounding only shapes the prose.
 */
class WeeklyBriefingGenerator(
    private val client: OpenAiCompatClient,
    private val promptBuilder: WeeklyBriefingPromptBuilder = WeeklyBriefingPromptBuilder(),
    private val knowledgeInjector: KnowledgeInjector = NoOpKnowledgeInjector,
    private val journey: CoachJourney = NoopCoachJourney,
) {
    /** Returns the merged briefing. Uses AI prose when available, deterministic engine summary as fallback. */
    suspend fun generate(config: CloudConfig, data: WeeklyReviewData): WeeklyBriefing? {
        val reference = knowledgeInjector.referenceBlock(knowledgeQuery(data))
        val journeyBlock = journeyBlock(journey.journeyNarrative())
        val prompt = buildString {
            if (reference.isNotBlank()) append(reference).append("\n\n")
            if (journeyBlock.isNotBlank()) append(journeyBlock).append("\n\n")
            append(promptBuilder.build(data))
        }
        val narration = requestNarration(config, prompt) ?: requestNarration(config, prompt)
        return merge(data, narration)
    }

    /** The multi-week narrative as a delimited grounding block, or "" when the store has none. */
    private fun journeyBlock(narrative: String): String =
        if (narrative.isBlank()) "" else "=== YOUR JOURNEY SO FAR ===\n$narrative\n=== END JOURNEY ==="

    /** Grounding query: the verdict plus each signal label, so retrieval matches the week's themes. */
    private fun knowledgeQuery(data: WeeklyReviewData): String =
        "recomposition ${data.verdictLabel} " + data.signals.joinToString(" ") { it.label }

    private suspend fun requestNarration(config: CloudConfig, prompt: String): BriefingNarration? {
        return try {
            val response = client.completion(
                config = config,
                messages = listOf(
                    ChatRequestMessage(role = "system", content = SYSTEM_PROMPT),
                    ChatRequestMessage(role = "user", content = prompt),
                ),
                toolSchemasJson = emptyList(),
            )
            parseBriefingNarration(response.text)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun merge(data: WeeklyReviewData, narration: BriefingNarration?): WeeklyBriefing {
        val signals = data.signals.map { s ->
            SignalLine(
                label = s.label,
                value = s.value,
                direction = s.direction,
                interpretation = narration?.interpretations?.get(s.id).orEmpty(),
            )
        }
        return WeeklyBriefing(
            weekStart = data.weekStart,
            phase = data.phase,
            headline = narration?.headline?.takeIf { it.isNotBlank() } ?: data.verdictLabel,
            narrative = narration?.narrative?.takeIf { it.isNotBlank() } ?: data.result.summary,
            signals = signals,
            action = ActionBlock(
                verdict = data.verdictLabel,
                rationale = narration?.actionRationale?.takeIf { it.isNotBlank() } ?: data.result.summary,
                applyTargetCalories = data.applyTargetCalories,
            ),
            watchNext = narration?.watchNext.orEmpty(),
        )
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "You are a precise body-recomposition coach. Output only the requested JSON. Never invent numbers."
    }
}
