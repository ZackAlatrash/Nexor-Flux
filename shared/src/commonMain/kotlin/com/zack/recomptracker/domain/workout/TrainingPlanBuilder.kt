package com.zack.recomptracker.domain.workout

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/**
 * The pure "brain" behind the training-planning surfaces (Train-home "Today" card + the Stats-tab
 * weekly view). It answers, deterministically, from data the app already logs:
 *
 *  - **Train or rest today?** — from training-day cadence (weekly count vs target, consecutive days,
 *    trained-today) plus recovery (passed in as two booleans the recovery mapper already computes).
 *  - **What to train next?** — the muscle categories least-trained this week / longest since hit.
 *  - **Which routine?** — the user's routine that best covers that focus.
 *  - **The weekly breakdown** — sets-this-week + days-since-last-hit per category (for the Stats tab).
 *
 * No AI, no Android/Room deps. Recovery scoring stays in [TrainingReadinessMapper]; this builder only
 * consumes its verdict (recoveryLow / deloadSuggested) so there is a single source of recovery truth.
 */
object TrainingPlanBuilder {

    enum class Verdict { TRAIN, REST }

    /** Per-category weekly training, for the Stats-tab "This week" view. */
    data class CategoryWeek(
        val category: MuscleCategory,
        val setsThisWeek: Int,
        /** Days since this category was last trained (any completed set); null = never. */
        val daysSinceLastHit: Int?,
    )

    data class TrainingPlan(
        val verdict: Verdict,
        val reason: String,
        /** Muscle groups to prioritise next (least-trained this week); may be shown even when resting. */
        val focus: List<MuscleCategory>,
        val recommendedRoutineId: Long?,
        val recommendedRoutineName: String?,
        /** True when we recommend training but recovery is down → "keep it light". */
        val recoveryTempered: Boolean,
        val weekly: List<CategoryWeek>,
        val sessionsThisWeek: Int,
        val weeklyTarget: Int?,
        val daysSinceLastSession: Int?,
    )

    private const val CONSECUTIVE_REST_TRIGGER = 3

    fun build(
        history: List<WorkoutSession>,
        library: List<Exercise>,
        routines: List<WorkoutTemplate>,
        today: LocalDate,
        weeklyTarget: Int? = null,
        recoveryLow: Boolean = false,
        deloadSuggested: Boolean = false,
    ): TrainingPlan {
        val weekStart = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY) // Monday of this week
        val musclesById: Map<Long, List<String>> = library.associate { it.id to it.primaryMuscles }

        // Per-category weekly sets + last-hit date (over all history).
        val weeklySets = mutableMapOf<MuscleCategory, Int>()
        val lastHit = mutableMapOf<MuscleCategory, LocalDate>()
        val sessionDates = mutableSetOf<LocalDate>()

        for (session in history) {
            val date = runCatching { LocalDate.parse(session.date) }.getOrNull() ?: continue
            if (date > today) continue
            sessionDates += date
            val inThisWeek = date >= weekStart
            for (ex in session.exercises) {
                val completed = ex.sets.count { it.completed }
                if (completed == 0) continue
                val category = muscleCategoryFor(musclesById[ex.exerciseId] ?: emptyList()) ?: continue
                if (inThisWeek) weeklySets[category] = (weeklySets[category] ?: 0) + completed
                val prev = lastHit[category]
                if (prev == null || date > prev) lastHit[category] = date
            }
        }

        val weekly = MuscleCategory.entries.map { cat ->
            CategoryWeek(
                category = cat,
                setsThisWeek = weeklySets[cat] ?: 0,
                daysSinceLastHit = lastHit[cat]?.let { it.daysUntil(today) },
            )
        }

        // Cadence.
        val lastSession = sessionDates.maxOrNull()
        val trainedToday = lastSession == today
        val daysSinceLastSession = lastSession?.let { it.daysUntil(today) }
        val sessionsThisWeek = sessionDates.count { it >= weekStart }
        val consecutive = consecutiveDays(sessionDates, lastSession, today)

        // Focus: least-trained-this-week categories (ties → longest since hit → enum order).
        val maxWeekly = weekly.maxOf { it.setsThisWeek }
        val focus = weekly
            .sortedWith(
                compareBy<CategoryWeek> { it.setsThisWeek }
                    .thenByDescending { it.daysSinceLastHit ?: Int.MAX_VALUE }
                    .thenBy { it.category.ordinal },
            )
            .filter { it.setsThisWeek < maxWeekly || maxWeekly == 0 }
            .take(2)
            .map { it.category }

        // Verdict + reason.
        val (verdict, reason) = when {
            deloadSuggested ->
                Verdict.REST to "Recovery's been low while training hard — a lighter week banks the fatigue."
            trainedToday ->
                Verdict.REST to "You've trained today — recover well and come back tomorrow."
            weeklyTarget != null && weeklyTarget > 0 && sessionsThisWeek >= weeklyTarget ->
                Verdict.REST to "You've hit your $weeklyTarget sessions this week — rest is productive."
            consecutive >= CONSECUTIVE_REST_TRIGGER ->
                Verdict.REST to "That's $consecutive days straight — a rest day would help."
            else -> Verdict.TRAIN to trainReason(focus)
        }

        val (routineId, routineName) =
            if (verdict == Verdict.TRAIN) recommendRoutine(routines, musclesById, focus) else null to null

        return TrainingPlan(
            verdict = verdict,
            reason = reason,
            focus = focus,
            recommendedRoutineId = routineId,
            recommendedRoutineName = routineName,
            recoveryTempered = verdict == Verdict.TRAIN && recoveryLow,
            weekly = weekly,
            sessionsThisWeek = sessionsThisWeek,
            weeklyTarget = weeklyTarget,
            daysSinceLastSession = daysSinceLastSession,
        )
    }

    /** Consecutive calendar days with a session, ending at the current streak (today or yesterday). */
    private fun consecutiveDays(dates: Set<LocalDate>, last: LocalDate?, today: LocalDate): Int {
        if (last == null || last < today.minus(1, DateTimeUnit.DAY)) return 0 // already resting
        var day: LocalDate = last
        var count = 0
        while (dates.contains(day)) {
            count++
            day = day.minus(1, DateTimeUnit.DAY)
        }
        return count
    }

    private fun trainReason(focus: List<MuscleCategory>): String = when {
        focus.isEmpty() -> "Good day to train — you're balanced across muscle groups."
        else -> "Train today — ${focus.joinToString(" & ") { it.displayName.lowercase() }} " +
            "${if (focus.size == 1) "is" else "are"} your lowest volume this week."
    }

    /** The routine whose muscle coverage overlaps the focus most (specific ties win). Null if none overlap. */
    private fun recommendRoutine(
        routines: List<WorkoutTemplate>,
        musclesById: Map<Long, List<String>>,
        focus: List<MuscleCategory>,
    ): Pair<Long?, String?> {
        if (routines.isEmpty() || focus.isEmpty()) return null to null
        val focusSet = focus.toSet()
        data class Scored(val id: Long, val name: String, val overlap: Int, val breadth: Int)
        val scored = routines.map { r ->
            val cats = r.exercises.mapNotNull { muscleCategoryFor(it.exercise.primaryMuscles) }.toSet()
            Scored(r.id, r.name, cats.count { it in focusSet }, cats.size)
        }
        val best = scored
            .filter { it.overlap > 0 }
            .sortedWith(compareByDescending<Scored> { it.overlap }.thenBy { it.breadth }.thenBy { it.id })
            .firstOrNull() ?: return null to null
        return best.id to best.name
    }
}
