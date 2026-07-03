package com.zack.recomptracker.data.coach

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.StreakRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.domain.coach.CoachContext
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * Thin I/O layer over [CoachContextAssembler] (docs/ai-redesign/08-technical-architecture.md §6).
 * Reads every source as a **suspend one-shot** — `.first()` on each flow, never an 8-way live
 * `combine` — to sidestep the documented first-emit hazard (`LogRepository.kt:51-55`) and the
 * `StreakRepository` per-emission recompute cost. All derivation lives in the pure assembler; this
 * class only gathers and delegates.
 *
 * Depends only on repositories / prefs / DateProvider and its own `data/coach` + `domain/coach`.
 */
class CoachContextBuilder(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val streakRepository: StreakRepository,
    private val userProfileStore: UserProfilePreferencesStore,
    private val dateProvider: DateProvider,
) {
    suspend fun build(): CoachContext {
        val today = dateProvider.today()
        val windowStart = today.minusDays((CoachContextAssembler.WINDOW_DAYS - 1).toLong())

        val plan = planRepository.preferences.first()
        val profile = userProfileStore.preferences.first()
        val dailyLogs = logRepository.observeDailyLogs().first()
        // Meals from the window start onward covers both the eaten window and any planned entries
        // dated today/future; unconfirmed-past plans within the window are included too.
        val meals = logRepository.observeMealEntriesSince(windowStart).first()
        val eatenMeals = meals.filterNot { it.planned }
        val plannedMeals = meals.filter { it.planned }
        // Windowed batched read: bound the training load to the assembler's window instead of
        // pulling all history. The assembler still windows in-memory, so behavior is unchanged.
        val completedSessions = workoutSessionRepository.getCompletedSessionsSince(windowStart)
        val streaks = streakRepository.streaks().first()
        val weeklyReviews = logRepository.observeWeeklyReviews().first().map { review ->
            WeeklyReviewInput(
                weekStart = LocalDate.parse(review.weekStart),
                verdict = review.verdict,
                signature = review.briefingSignature ?: review.weekStart,
            )
        }

        val windowDates = (0 until CoachContextAssembler.WINDOW_DAYS)
            .map { today.minusDays(it.toLong()) }
        val targetsByDate = planRepository.targetsByDate(windowDates)

        return CoachContextAssembler.assemble(
            CoachContextInputs(
                today = today,
                plan = plan,
                profile = profile,
                dailyLogs = dailyLogs,
                eatenMeals = eatenMeals,
                plannedMeals = plannedMeals,
                completedSessions = completedSessions,
                streaks = streaks,
                weeklyReviews = weeklyReviews,
                targetsByDate = targetsByDate,
            ),
        )
    }
}
