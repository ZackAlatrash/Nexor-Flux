package com.zack.recomptracker.ui.train

import com.zack.recomptracker.ui.train.TrainingReadinessMapper.Level
import com.zack.recomptracker.ui.train.TrainingReadinessMapper.Mode
import com.zack.recomptracker.ui.train.TrainingReadinessMapper.RecoveryBaseline
import com.zack.recomptracker.ui.train.TrainingReadinessMapper.RecoveryToday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingReadinessMapperTest {

    @Test fun `hidden when no recovery is logged today`() {
        assertNull(TrainingReadinessMapper.build(RecoveryToday()))
    }

    @Test fun `low readiness when two or more penalties`() {
        // High soreness + low sleep = 2 penalties → LOW.
        val r = TrainingReadinessMapper.build(
            RecoveryToday(sorenessScore = 8, sleepHours = 5.0, energyScore = 7),
        )
        assertNotNull(r)
        assertEquals(Level.LOW, r!!.level)
        assertEquals("Recovery's low", r.headline)
        assertEquals("Keep it light or swap to mobility", r.adaptHint)
    }

    @Test fun `good readiness when signals are strong`() {
        val r = TrainingReadinessMapper.build(
            RecoveryToday(sorenessScore = 2, sleepHours = 8.0, energyScore = 9),
        )
        assertNotNull(r)
        assertEquals(Level.GOOD, r!!.level)
        assertEquals("You're recovered", r.headline)
        assertEquals("Train as planned", r.adaptHint)
    }

    @Test fun `moderate readiness on a single penalty`() {
        // Only low sleep = 1 penalty → MODERATE.
        val r = TrainingReadinessMapper.build(
            RecoveryToday(sorenessScore = 3, sleepHours = 5.5, energyScore = 8),
        )
        assertNotNull(r)
        assertEquals(Level.MODERATE, r!!.level)
    }

    @Test fun `moderate readiness on two soft flags`() {
        // Soreness 5 (moderate) + sleep 6.5 (moderate) = 2 soft flags, 0 penalties → MODERATE.
        val r = TrainingReadinessMapper.build(
            RecoveryToday(sorenessScore = 5, sleepHours = 6.5, energyScore = 9),
        )
        assertNotNull(r)
        assertEquals(Level.MODERATE, r!!.level)
    }

    @Test fun `card shows with only one signal present`() {
        val r = TrainingReadinessMapper.build(RecoveryToday(energyScore = 3))
        assertNotNull(r)
        assertEquals(Level.MODERATE, r!!.level) // energy ≤4 = 1 penalty
    }

    @Test fun `load note describes days since last session (train mode)`() {
        val yesterday = TrainingReadinessMapper.build(
            RecoveryToday(sleepHours = 8.0), daysSinceLastSession = 1,
        )
        assertEquals(Mode.TRAIN, yesterday!!.mode)
        assertEquals("Last session was yesterday", yesterday.loadNote)

        val threeDays = TrainingReadinessMapper.build(
            RecoveryToday(sleepHours = 8.0), daysSinceLastSession = 3,
        )
        assertEquals("It's been 3 days since your last session", threeDays!!.loadNote)

        val none = TrainingReadinessMapper.build(RecoveryToday(sleepHours = 8.0))
        assertNull(none!!.loadNote)
    }

    @Test fun `coach seed ends in an action and lists the logged signals`() {
        val r = TrainingReadinessMapper.build(
            RecoveryToday(sorenessScore = 8, sleepHours = 5.0, energyScore = 3),
        )!!
        assertEquals(Mode.TRAIN, r.mode)
        assertEquals("Adapt session", r.actionLabel)
        assertTrue(r.coachSeed.contains("Adapt session"))
        assertTrue(r.coachSeed.contains("soreness 8/10"))
        assertTrue(r.coachSeed.contains("5h sleep"))
        assertTrue(r.coachSeed.contains("energy 3/10"))
    }

    // ── Already trained today → post-session / tomorrow mode ──────────────────
    @Test fun `already trained today switches to a recovery-and-tomorrow mode`() {
        val r = TrainingReadinessMapper.build(
            RecoveryToday(sorenessScore = 2, sleepHours = 8.0, energyScore = 9),
            daysSinceLastSession = 0,
        )
        assertNotNull(r)
        assertEquals(Mode.REST_DONE, r!!.mode)
        // Must NOT tell them to "train as planned" — it's forward-looking recovery advice.
        assertFalse(r.adaptHint.contains("Train as planned"))
        assertTrue(r.headline.contains("Trained today"))
        assertEquals("Plan recovery", r.actionLabel)
    }

    @Test fun `trained today shows even when no recovery is logged`() {
        val r = TrainingReadinessMapper.build(RecoveryToday(), daysSinceLastSession = 0)
        assertNotNull(r)
        assertEquals(Mode.REST_DONE, r!!.mode)
    }

    // ── Rest-day awareness (weekly target met) ────────────────────────────────
    @Test fun `rest day when the weekly session target is met`() {
        val r = TrainingReadinessMapper.build(
            RecoveryToday(energyScore = 7),
            sessionsThisWeek = 4,
            weeklyTarget = 4,
        )
        assertNotNull(r)
        assertEquals(Mode.REST_DAY, r!!.mode)
        assertEquals("Ask coach", r.actionLabel)
    }

    @Test fun `not a rest day when under the weekly target`() {
        val r = TrainingReadinessMapper.build(
            RecoveryToday(energyScore = 7),
            sessionsThisWeek = 2,
            weeklyTarget = 4,
        )
        assertEquals(Mode.TRAIN, r!!.mode)
    }

    // ── Deload: sustained under-recovery + frequent training ──────────────────
    @Test fun `deload when recent recovery is poor and training frequently`() {
        val r = TrainingReadinessMapper.build(
            RecoveryToday(energyScore = 5),
            baseline = RecoveryBaseline(avgSorenessScore = 7, avgSleepHours = 5.5, avgEnergyScore = 4),
            sessionsThisWeek = 4,
        )
        assertNotNull(r)
        assertEquals(Mode.DELOAD, r!!.mode)
        assertEquals(Level.LOW, r.level)
        assertEquals("Plan a deload", r.actionLabel)
    }

    @Test fun `no deload when trained little even if recovery is poor`() {
        val r = TrainingReadinessMapper.build(
            RecoveryToday(energyScore = 5),
            baseline = RecoveryBaseline(avgSorenessScore = 7, avgSleepHours = 5.5, avgEnergyScore = 4),
            sessionsThisWeek = 1,
        )
        assertEquals(Mode.TRAIN, r!!.mode)
    }

    // ── Trend: fallback + one-bad-day softening ───────────────────────────────
    @Test fun `falls back to the recent baseline when today has no recovery`() {
        val r = TrainingReadinessMapper.build(
            RecoveryToday(),
            baseline = RecoveryBaseline(avgSorenessScore = 8, avgSleepHours = 5.0, avgEnergyScore = 7),
        )
        assertNotNull(r)
        assertEquals(Mode.TRAIN, r!!.mode)
        assertEquals(Level.LOW, r.level) // sore 8 + sleep 5 = 2 penalties
        assertTrue(r.usedBaseline)
    }

    @Test fun `one bad day is softened when the recent baseline is strong`() {
        val r = TrainingReadinessMapper.build(
            // Today reads LOW (soreness 8 + sleep 5 = 2 penalties)…
            RecoveryToday(sorenessScore = 8, sleepHours = 5.0, energyScore = 7),
            // …but the recent trend has been solid → soften to MODERATE.
            baseline = RecoveryBaseline(avgSorenessScore = 2, avgSleepHours = 8.0, avgEnergyScore = 8),
        )
        assertNotNull(r)
        assertEquals(Mode.TRAIN, r!!.mode)
        assertEquals(Level.MODERATE, r.level)
        assertFalse(r.usedBaseline) // today's data was used, baseline only softened it
    }
}
