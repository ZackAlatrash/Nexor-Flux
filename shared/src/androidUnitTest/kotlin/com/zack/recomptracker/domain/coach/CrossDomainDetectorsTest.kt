package com.zack.recomptracker.domain.coach

import com.zack.recomptracker.domain.coach.CoachContextFixtures.datedSeries
import com.zack.recomptracker.domain.coach.CoachContextFixtures.recoverySeries
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.domain.workout.SessionStatus
import com.zack.recomptracker.domain.workout.WorkoutSession
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-4 cross-domain link detectors: [DeloadDueDetector] (RIR falling × recovery POOR) and
 * [SleepHungerLinkDetector] (poor-sleep days run hungrier). Pure objects, no mocks.
 */
class CrossDomainDetectorsTest {

    // ── DeloadDueDetector ──────────────────────────────────────────────────────

    /** Body context whose sleep/energy/soreness force RecoveryTrend.POOR. */
    private fun poorRecoveryBody() = CoachContextFixtures.body(
        sleepSeries = recoverySeries(5.2),
        energySeries = recoverySeries(4.0),
        sorenessSeries = recoverySeries(8.0),
    )

    @Test
    fun `deload due fires when RIR is low and falling and recovery is POOR`() {
        val ctx = CoachContextFixtures.context(
            body = poorRecoveryBody(),
            // Newest-first (the real contract): recent sessions at RIR 1, older at RIR 3 = falling.
            training = CoachContextFixtures.training(recentRir = listOf(1, 1, 2, 3)),
        )
        val s = DeloadDueDetector().detect(ctx)!!
        assertEquals(SignalKind.DELOAD_DUE, s.kind)
        assertEquals(SignalTier.P1, s.tier)
        assertEquals(SignalCategory.RECOVERY, s.category)
        assertEquals(CoachSurface.TODAY, s.surface)
        assertEquals(CoachActionType.OPEN_TRAINING, s.action.type)
        assertTrue("verdict recommends a deload", s.verdict.contains("deload", ignoreCase = true))
        assertTrue(s.verdict.isNotBlank())
        assertTrue(s.fallbackText.isNotBlank())
        // Facts come from the RIR + recovery numbers only.
        assertTrue(s.facts.values.containsKey("recentAvgRir"))
        assertEquals("1.0", s.facts.values["recentAvgRir"])
        assertTrue(s.facts.values.containsKey("avgSleepHours"))
        assertTrue(s.dedupKey.startsWith("DELOAD_DUE|2026-W27"))
        assertTrue(s.severity in 0..100)
    }

    @Test
    fun `deload due does NOT fire when recovery is fine even if RIR is low`() {
        val ctx = CoachContextFixtures.context(
            // default body has good recovery (sleep 7.5, energy 8, soreness 3); RIR is low+falling
            // (newest-first: recent 0/1, older 2), so recovery is the deciding gate.
            training = CoachContextFixtures.training(recentRir = listOf(0, 1, 1, 2)),
        )
        assertNull(DeloadDueDetector().detect(ctx))
    }

    @Test
    fun `deload due does NOT fire when RIR is healthy even if recovery is POOR`() {
        val ctx = CoachContextFixtures.context(
            body = poorRecoveryBody(),
            training = CoachContextFixtures.training(recentRir = listOf(3, 3, 3, 3)),
        )
        assertNull(DeloadDueDetector().detect(ctx))
    }

    @Test
    fun `deload due does NOT fire when RIR is low but rising (recovering)`() {
        val ctx = CoachContextFixtures.context(
            body = poorRecoveryBody(),
            // Newest-first: recent sessions at RIR 3, older at 0 = rising/recovering, not falling.
            training = CoachContextFixtures.training(recentRir = listOf(3, 2, 1, 0)),
        )
        assertNull(DeloadDueDetector().detect(ctx))
    }

    @Test
    fun `deload due does NOT fire when there is no RIR data`() {
        val ctx = CoachContextFixtures.context(
            body = poorRecoveryBody(),
            training = CoachContextFixtures.training(recentRir = emptyList()),
        )
        assertNull(DeloadDueDetector().detect(ctx))
    }

    // ── DeloadDue through the real producer (TrainingDerivations) ───────────────
    // These build recentRir the way the app actually does — via TrainingDerivations.recentRir, which
    // returns sets NEWEST-SESSION-FIRST — instead of a hand-built chronological list. That ordering
    // is exactly what the hand-built fixtures above got backwards, hiding the bug where the detector
    // read newest-first data as if it were chronological (review P1-6).

    // Only the newest DEFAULT_RECENT_SESSIONS (3) sessions contribute RIR, so give each two sets to
    // clear the detector's 4-reading minimum.
    private var idSeq = 0L
    private fun sessionOn(date: LocalDate, rir: Int): Pair<LocalDate, WorkoutSession> = date to WorkoutSession(
        id = idSeq++, workoutId = null, workoutName = "Session", date = date.toString(),
        startedAt = "${date}T09:00:00Z", completedAt = "${date}T10:00:00Z",
        status = SessionStatus.COMPLETED, note = null, durationSeconds = 3600,
        exercises = listOf(
            SessionExercise(
                id = idSeq++, exerciseId = 1, exerciseName = "Bench", sortOrder = 0, note = null,
                sets = (1..2).map { SessionSet(id = idSeq++, setNumber = it, reps = 5, weightKg = 100.0, rir = rir, completed = true) },
            ),
        ),
    )

    @Test
    fun `deload due fires for a genuinely falling RIR history built through TrainingDerivations`() {
        // Six days ago the user had RIR 3 to spare; the two most recent sessions ground down to RIR 1
        // — real overreaching. TrainingDerivations returns this newest-session-first.
        val sessions = listOf(
            sessionOn(CoachContextFixtures.TODAY.minus(6, DateTimeUnit.DAY), rir = 3),
            sessionOn(CoachContextFixtures.TODAY.minus(3, DateTimeUnit.DAY), rir = 1),
            sessionOn(CoachContextFixtures.TODAY.minus(1, DateTimeUnit.DAY), rir = 1),
        )
        val ctx = CoachContextFixtures.context(
            body = poorRecoveryBody(),
            training = CoachContextFixtures.training(recentRir = TrainingDerivations.recentRir(sessions)),
        )
        val s = DeloadDueDetector().detect(ctx)
        assertNotNull("a genuinely-falling RIR history (newest-first) must recommend a deload", s)
        assertEquals("1.0", s!!.facts.values["recentAvgRir"])
    }

    @Test
    fun `deload due stays silent for a recovered RIR history built through TrainingDerivations`() {
        // Was grinding at RIR 1; the two most recent sessions backed off to RIR 3 — already recovered.
        val sessions = listOf(
            sessionOn(CoachContextFixtures.TODAY.minus(6, DateTimeUnit.DAY), rir = 1),
            sessionOn(CoachContextFixtures.TODAY.minus(3, DateTimeUnit.DAY), rir = 3),
            sessionOn(CoachContextFixtures.TODAY.minus(1, DateTimeUnit.DAY), rir = 3),
        )
        val ctx = CoachContextFixtures.context(
            body = poorRecoveryBody(),
            training = CoachContextFixtures.training(recentRir = TrainingDerivations.recentRir(sessions)),
        )
        assertNull(DeloadDueDetector().detect(ctx))
    }

    // ── SleepHungerLinkDetector ────────────────────────────────────────────────

    @Test
    fun `sleep-hunger link fires when poor-sleep days run hungrier`() {
        // 4 good-sleep days (>=7h) with low hunger, 4 poor-sleep days (<6h) with high hunger.
        // Paired by date: alternate good/poor so both groups have >=3 days.
        val sleep = datedSeries(8.0, 5.0, 8.0, 5.0, 8.0, 5.0, 8.0, 5.0)
        val hunger = datedSeries(3.0, 7.0, 3.0, 7.0, 3.0, 7.0, 3.0, 7.0)
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(sleepSeries = sleep, hungerSeries = hunger),
        )
        val s = SleepHungerLinkDetector().detect(ctx)!!
        assertEquals(SignalKind.SLEEP_HUNGER_LINK, s.kind)
        assertEquals(SignalTier.P2, s.tier)
        assertEquals(SignalCategory.RECOVERY, s.category)
        assertEquals(CoachSurface.TODAY, s.surface)
        assertTrue(s.verdict.isNotBlank())
        assertTrue("verdict mentions sleep", s.verdict.contains("sleep", ignoreCase = true))
        assertTrue(s.fallbackText.isNotBlank())
        // Facts = the two group averages, engine-computed.
        assertEquals("7.0", s.facts.values["poorSleepHunger"])
        assertEquals("3.0", s.facts.values["goodSleepHunger"])
        assertTrue(s.dedupKey.startsWith("SLEEP_HUNGER_LINK|"))
        assertTrue(s.dedupKey.contains("2026-W27"))
        assertTrue(s.severity in 0..100)
    }

    @Test
    fun `sleep-hunger link does NOT fire when the hunger gap is too small`() {
        val sleep = datedSeries(8.0, 5.0, 8.0, 5.0, 8.0, 5.0, 8.0, 5.0)
        // Gap of only 1.0 (< 1.5 min gap).
        val hunger = datedSeries(4.0, 5.0, 4.0, 5.0, 4.0, 5.0, 4.0, 5.0)
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(sleepSeries = sleep, hungerSeries = hunger),
        )
        assertNull(SleepHungerLinkDetector().detect(ctx))
    }

    @Test
    fun `sleep-hunger link does NOT fire without enough days per group`() {
        // Only 2 poor-sleep days (< 3 min per group).
        val sleep = datedSeries(8.0, 8.0, 8.0, 8.0, 5.0, 5.0)
        val hunger = datedSeries(3.0, 3.0, 3.0, 3.0, 7.0, 7.0)
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(sleepSeries = sleep, hungerSeries = hunger),
        )
        assertNull(SleepHungerLinkDetector().detect(ctx))
    }

    @Test
    fun `sleep-hunger link only fires the helpful direction (poor sleep hungrier)`() {
        // Reversed: poor-sleep days are LESS hungry — not the helpful direction, must stay silent.
        val sleep = datedSeries(8.0, 5.0, 8.0, 5.0, 8.0, 5.0, 8.0, 5.0)
        val hunger = datedSeries(7.0, 3.0, 7.0, 3.0, 7.0, 3.0, 7.0, 3.0)
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(sleepSeries = sleep, hungerSeries = hunger),
        )
        assertNull(SleepHungerLinkDetector().detect(ctx))
    }

    @Test
    fun `sleep-hunger link does NOT fire when a day lacks a hunger pairing`() {
        // Sleep present but no hunger series at all → no pairs → silent.
        val sleep = datedSeries(8.0, 5.0, 8.0, 5.0, 8.0, 5.0)
        val ctx = CoachContextFixtures.context(
            body = CoachContextFixtures.body(sleepSeries = sleep, hungerSeries = emptyList()),
        )
        assertNull(SleepHungerLinkDetector().detect(ctx))
    }
}
