package com.zack.recomptracker.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** EARLY = 7–13 logged days or no actionable verdict; FULL = 14+ days with a real verdict. */
enum class BriefingPhase { EARLY, FULL }

enum class SignalDirection { UP, DOWN, FLAT }

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
