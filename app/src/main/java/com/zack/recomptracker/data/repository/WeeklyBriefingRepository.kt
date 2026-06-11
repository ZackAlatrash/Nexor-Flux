package com.zack.recomptracker.data.repository

import com.zack.recomptracker.ai.ActionBlock
import com.zack.recomptracker.domain.review.BriefingPhase
import com.zack.recomptracker.domain.review.SignalDirection
import com.zack.recomptracker.ai.SignalLine
import com.zack.recomptracker.ai.WeeklyBriefing
import com.zack.recomptracker.data.local.dao.WeeklyReviewDao
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Caches the generated [WeeklyBriefing] on the existing weekly_reviews row. A briefing is reused
 * only when the stored [WeeklyReviewEntity.briefingSignature] matches the requested signature.
 */
class WeeklyBriefingRepository(
    private val weeklyReviewDao: WeeklyReviewDao,
) {
    /**
     * Returns the cached briefing for [weekStart] when [signature] matches the stored one,
     * otherwise runs [generate], persists the result against [signature], and returns it.
     */
    suspend fun briefingFor(
        weekStart: String,
        signature: String,
        generate: suspend () -> WeeklyBriefing,
    ): WeeklyBriefing {
        val row = weeklyReviewDao.getByWeekStart(weekStart)
        val cached = row
            ?.takeIf { it.briefingSignature == signature }
            ?.briefingJson
            ?.let { runCatching { json.decodeFromString(BriefingDto.serializer(), it) }.getOrNull() }
        if (cached != null) return cached.toModel(weekStart)

        val fresh = generate()
        val base = weeklyReviewDao.getByWeekStart(weekStart) ?: WeeklyReviewEntity(
            weekStart = weekStart,
            verdict = "",
            recommendedCalorieChange = 0,
            reasonCodes = "",
            generatedAt = "",
        )
        weeklyReviewDao.upsert(
            base.copy(
                briefingJson = json.encodeToString(BriefingDto.serializer(), fresh.toDto()),
                briefingSignature = signature,
                briefingGeneratedAt = Instant.now().toString(),
            ),
        )
        return fresh
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class BriefingDto(
    val phase: String,
    val headline: String,
    val narrative: String,
    val signals: List<SignalDto>,
    val verdict: String,
    val rationale: String,
    val applyTargetCalories: Int?,
    val watchNext: String,
) {
    fun toModel(weekStart: String) = WeeklyBriefing(
        weekStart = weekStart,
        phase = BriefingPhase.valueOf(phase),
        headline = headline,
        narrative = narrative,
        signals = signals.map { SignalLine(it.label, it.value, SignalDirection.valueOf(it.direction), it.interpretation) },
        action = ActionBlock(verdict, rationale, applyTargetCalories),
        watchNext = watchNext,
    )
}

@Serializable
private data class SignalDto(
    val label: String,
    val value: String,
    val direction: String,
    val interpretation: String,
)

private fun WeeklyBriefing.toDto() = BriefingDto(
    phase = phase.name,
    headline = headline,
    narrative = narrative,
    signals = signals.map { SignalDto(it.label, it.value, it.direction.name, it.interpretation) },
    verdict = action.verdict,
    rationale = action.rationale,
    applyTargetCalories = action.applyTargetCalories,
    watchNext = watchNext,
)
