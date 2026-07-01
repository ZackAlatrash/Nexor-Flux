package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.domain.workout.SessionStatus
import com.zack.recomptracker.domain.workout.WorkoutSession
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for the Phase-3 read-only coach tools get_training_summary and get_body_trends. Uses a real
 * (subclassed) [WorkoutSessionRepository] returning fixture sessions plus a Mockito [LogRepository]
 * for daily logs — every asserted number is produced by the pure calculators, never invented.
 */
class CoachToolExecutorTrainingToolsTest {

    private val fixedDate: LocalDate = LocalDate.of(2026, 6, 5)
    private val fixedDateProvider = object : DateProvider {
        override fun today(): LocalDate = fixedDate
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────

    /** Fake repo: returns [sessions] for any getCompletedSessionsSince(start) call. */
    private class FakeWorkoutSessionRepository(
        private val sessions: List<WorkoutSession>,
    ) : WorkoutSessionRepository(sessionDao = mock<WorkoutSessionDao>()) {
        override suspend fun getCompletedSessionsSince(start: LocalDate): List<WorkoutSession> =
            sessions.filter { !LocalDate.parse(it.date).isBefore(start) }
    }

    private fun set(reps: Int, weightKg: Double?, rir: Int?, completed: Boolean = true) =
        SessionSet(id = 0L, setNumber = 1, reps = reps, weightKg = weightKg, rir = rir, completed = completed)

    private fun session(date: LocalDate, exerciseName: String, sets: List<SessionSet>) =
        WorkoutSession(
            id = 0L,
            workoutId = null,
            workoutName = "Test",
            date = date.toString(),
            startedAt = date.toString(),
            completedAt = date.toString(),
            status = SessionStatus.COMPLETED,
            note = null,
            durationSeconds = null,
            exercises = listOf(
                SessionExercise(id = 0L, exerciseId = 0L, exerciseName = exerciseName, sortOrder = 0, note = null, sets = sets),
            ),
        )

    private fun logRepoWith(logs: List<DailyLogEntity>): LogRepository {
        val logRepo = mock<LogRepository>()
        whenever(logRepo.observeDailyLogs()).thenReturn(flowOf(logs))
        return logRepo
    }

    private fun executor(
        logRepo: LogRepository,
        sessions: List<WorkoutSession>,
    ) = CoachToolExecutor(
        logRepository = logRepo,
        planRepository = mock<PlanRepository>(),
        dateProvider = fixedDateProvider,
        workoutSessionRepository = FakeWorkoutSessionRepository(sessions),
    )

    // ── get_training_summary ─────────────────────────────────────────────────────

    @Test
    fun `get_training_summary reports per-lift latest e1RM, trend, sessions_per_week and RIR`() = runTest {
        // Bench e1RM climbs over three weeks: 100→105→110kg @5 reps → trend up.
        val sessions = listOf(
            session(fixedDate.minusDays(14), "Bench", listOf(set(5, 100.0, 3))),
            session(fixedDate.minusDays(7), "Bench", listOf(set(5, 105.0, 2))),
            session(fixedDate.minusDays(0), "Bench", listOf(set(5, 110.0, 2))),
        )
        val logs = listOf(DailyLogEntity(date = fixedDate.toString(), trained = true))
        val result = executor(logRepoWith(logs), sessions).execute("get_training_summary", emptyMap())

        assertTrue("window", result.contains("\"window_days\":28"))
        assertTrue("lift name", result.contains("\"name\":\"Bench\""))
        // Latest = 110 × (1 + 5/30) = 128.33 kg.
        assertTrue("latest e1RM", result.contains("\"latest_e1rm_kg\":128.3"))
        assertTrue("trend up", result.contains("\"trend\":\"up\""))
        // 3 distinct training days in the trailing 4 weeks → 3/4 = 0.75 sessions/week.
        assertTrue("sessions/week", result.contains("\"sessions_per_week\":0.8") || result.contains("\"sessions_per_week\":0.7"))
        // Recent RIR list present with the newest sessions' values.
        assertTrue("recent_rir present", result.contains("\"recent_rir\":["))
        assertTrue("recent_rir has a value", result.contains("2"))
    }

    @Test
    fun `get_training_summary surfaces recent soreness from daily logs`() = runTest {
        val sessions = listOf(session(fixedDate, "Squat", listOf(set(5, 140.0, 2))))
        val logs = listOf(
            DailyLogEntity(date = fixedDate.minusDays(1).toString(), sorenessScore = 3, trained = true),
            DailyLogEntity(date = fixedDate.toString(), sorenessScore = 5, trained = true),
        )
        val result = executor(logRepoWith(logs), sessions).execute("get_training_summary", emptyMap())

        assertTrue("recent_soreness present", result.contains("\"recent_soreness\":["))
        // Newest-first: today's 5 then yesterday's 3.
        assertTrue("soreness values", result.contains("5") && result.contains("3"))
    }

    @Test
    fun `get_training_summary returns note when no completed sessions in window`() = runTest {
        val result = executor(logRepoWith(emptyList()), emptyList()).execute("get_training_summary", emptyMap())

        assertTrue("window", result.contains("\"window_days\":28"))
        assertTrue("empty lifts", result.contains("\"lifts\":[]"))
        assertTrue("note", result.contains("no completed training sessions logged in this window"))
        assertTrue("zero frequency", result.contains("\"sessions_per_week\":0"))
    }

    // ── get_body_trends ──────────────────────────────────────────────────────────

    @Test
    fun `get_body_trends reports weight and waist trends, latest and 7-day average`() = runTest {
        // Weight drops 0.2kg/week; waist drops 0.15cm/week (roughly).
        val logs = listOf(
            DailyLogEntity(date = fixedDate.minusDays(14).toString(), bodyWeightKg = 80.5, waistCm = 84.3),
            DailyLogEntity(date = fixedDate.minusDays(7).toString(), bodyWeightKg = 80.3, waistCm = 84.15),
            DailyLogEntity(date = fixedDate.toString(), bodyWeightKg = 80.1, waistCm = 84.0),
        )
        val result = executor(logRepoWith(logs), emptyList()).execute("get_body_trends", emptyMap())

        assertTrue("window", result.contains("\"window_days\":28"))
        assertTrue("weight trend negative", result.contains("\"weight_trend_kg_per_week\":-0.2"))
        assertTrue("waist trend present & negative", result.contains("\"waist_trend_cm_per_week\":-0.1"))
        assertTrue("latest weight", result.contains("\"latest_weight_kg\":80.1"))
        assertTrue("latest waist", result.contains("\"latest_waist_cm\":84.0"))
        // Only the single in-7d weigh-in (today) counts → avg = 80.1.
        assertTrue("avg 7d weight", result.contains("\"avg_weight_7d\":80.1"))
    }

    @Test
    fun `get_body_trends is null-safe when a metric was never logged`() = runTest {
        // Weight logged; waist and skinfold never logged.
        val logs = listOf(
            DailyLogEntity(date = fixedDate.minusDays(7).toString(), bodyWeightKg = 79.0),
            DailyLogEntity(date = fixedDate.toString(), bodyWeightKg = 78.8),
        )
        val result = executor(logRepoWith(logs), emptyList()).execute("get_body_trends", emptyMap())

        assertTrue("waist trend null", result.contains("\"waist_trend_cm_per_week\":null"))
        assertTrue("skinfold trend null", result.contains("\"skinfold_trend_mm_per_week\":null"))
        assertTrue("latest waist null", result.contains("\"latest_waist_cm\":null"))
        assertTrue("weight trend not null", result.contains("\"weight_trend_kg_per_week\":-0.2"))
        assertFalse("latest weight not null", result.contains("\"latest_weight_kg\":null"))
    }
}
