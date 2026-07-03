package com.zack.recomptracker.ai

import com.zack.recomptracker.domain.review.BriefingPhase
import com.zack.recomptracker.domain.review.SignalDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One per-signal row. value/direction are deterministic; interpretation is AI prose. */
data class SignalLine(
    val label: String,
    val value: String,
    val direction: SignalDirection,
    val interpretation: String,
)

/** verdict/applyTargetCalories are deterministic; rationale is AI prose. */
data class ActionBlock(
    val verdict: String,
    val rationale: String,
    val applyTargetCalories: Int?,
)

/** The fully merged briefing the UI renders. */
data class WeeklyBriefing(
    val weekStart: String,
    val phase: BriefingPhase,
    val headline: String,
    val narrative: String,
    val signals: List<SignalLine>,
    val action: ActionBlock,
    val watchNext: String,
    /**
     * Deterministic Weekly Pattern Spotlight facts (top two), rendered verbatim — the relocated
     * Weekly Pattern card (Phase 2D). Empty when no pattern fires. Never model-generated.
     */
    val patternSpotlight: List<String> = emptyList(),
)

/** Prose-only payload the model returns. No numbers, no verdict. */
data class BriefingNarration(
    val headline: String,
    val narrative: String,
    val interpretations: Map<String, String>,
    val actionRationale: String,
    val watchNext: String,
)

@Serializable
private data class BriefingNarrationDto(
    val headline: String = "",
    val narrative: String = "",
    val interpretations: Map<String, String> = emptyMap(),
    @SerialName("action_rationale") val actionRationale: String = "",
    @SerialName("watch_next") val watchNext: String = "",
)

private val briefingJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Strips ``` fences, isolates the outermost {...}, and parses. Returns null on any failure. */
fun parseBriefingNarration(raw: String): BriefingNarration? {
    val unfenced = raw.trim()
        .removePrefix("```json").removePrefix("```").removeSuffix("```")
        .trim()
    val start = unfenced.indexOf('{')
    val end = unfenced.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val slice = unfenced.substring(start, end + 1)
    return try {
        val dto = briefingJson.decodeFromString(BriefingNarrationDto.serializer(), slice)
        if (dto.headline.isBlank() && dto.narrative.isBlank()) return null
        BriefingNarration(
            headline = dto.headline,
            narrative = dto.narrative,
            interpretations = dto.interpretations,
            actionRationale = dto.actionRationale,
            watchNext = dto.watchNext,
        )
    } catch (e: Exception) {
        null
    }
}
