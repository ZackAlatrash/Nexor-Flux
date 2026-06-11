package com.zack.recomptracker.ai

import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.domain.review.WeeklyReviewData

/**
 * Cloud-only generator. Asks the model for prose (BriefingNarration), then merges that prose onto
 * the deterministic [WeeklyReviewData] skeleton so numbers/verdict can never be altered by the model.
 */
class WeeklyBriefingGenerator(
    private val client: OpenAiCompatClient,
    private val promptBuilder: WeeklyBriefingPromptBuilder = WeeklyBriefingPromptBuilder(),
) {
    /** Returns the merged briefing. Uses AI prose when available, deterministic engine summary as fallback. */
    suspend fun generate(config: CloudConfig, data: WeeklyReviewData): WeeklyBriefing? {
        val prompt = promptBuilder.build(data)
        val narration = requestNarration(config, prompt) ?: requestNarration(config, prompt)
        return merge(data, narration)
    }

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
