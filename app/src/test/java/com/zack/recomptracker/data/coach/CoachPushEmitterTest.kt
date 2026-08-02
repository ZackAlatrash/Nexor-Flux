package com.zack.recomptracker.data.coach

import com.zack.recomptracker.domain.coach.CoachAction
import com.zack.recomptracker.domain.coach.CoachActionType
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.CoachSurface
import com.zack.recomptracker.domain.coach.Confidence
import com.zack.recomptracker.domain.coach.PushEvent
import com.zack.recomptracker.domain.coach.QuietHours
import com.zack.recomptracker.domain.coach.RateLimiter
import com.zack.recomptracker.domain.coach.SignalCategory
import com.zack.recomptracker.domain.coach.SignalFacts
import com.zack.recomptracker.domain.coach.SignalKind
import com.zack.recomptracker.domain.coach.SignalRationale
import com.zack.recomptracker.domain.coach.SignalTier
import java.time.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachPushEmitterTest {

    // A mid-day, mid-week instant so the RateLimiter's quiet-hours and consecutive-day gates pass by
    // default (Wednesday 13:00, no recent pushes).
    private val now = LocalDateTime.of(2026, 7, 1, 13, 0)

    /** In-memory [CoachNotifier] fake — records shows and can simulate a platform refusal. */
    private class FakeNotifier(private val shows: Boolean = true) : CoachNotifier {
        val shown = mutableListOf<CoachPushPayload>()
        var ensureChannelsCalls = 0
        override fun ensureChannels() { ensureChannelsCalls++ }
        override fun show(payload: CoachPushPayload): Boolean {
            shown += payload
            return shows
        }
    }

    /** In-memory [PushHistory] fake. */
    private class FakeHistory(seed: List<PushEvent> = emptyList()) : PushHistory {
        val recorded = seed.toMutableList()
        override suspend fun record(event: PushEvent) { recorded += event }
        override suspend fun recentPushes(): List<PushEvent> = recorded.toList()
    }

    /** In-memory [CoachNotifierPreferences] fake with directly-set flags. */
    private class FakePrefs(
        private val weekly: Boolean,
        private val ambient: Boolean,
        private val quiet: QuietHours = QuietHours(LocalTime(22, 0), LocalTime(7, 0)),
    ) : CoachNotifierPreferences {
        override val weeklyCheckInPushEnabled: Flow<Boolean> = flowOf(weekly)
        override val ambientNudgesEnabled: Flow<Boolean> = flowOf(ambient)
        override suspend fun quietHours(): QuietHours = quiet
        override suspend fun setWeeklyCheckInPushEnabled(enabled: Boolean) = Unit
        override suspend fun setAmbientNudgesEnabled(enabled: Boolean) = Unit
        override suspend fun setQuietHours(startHour: Int, endHour: Int) = Unit
        override suspend fun lastPushedWeeklySignature(): String = ""
        override suspend fun setLastPushedWeeklySignature(signature: String) = Unit
    }

    private fun signal(
        kind: SignalKind = SignalKind.FAT_GAIN_WARNING,
        tier: SignalTier = SignalTier.P0,
        action: CoachAction = CoachAction(CoachActionType.LOG_WEIGHT, "Log weight"),
        verdict: String = "Weight and waist both up — check your intake.",
        fallbackText: String = "Both trending up; log a weigh-in and tighten calories.",
    ) = CoachSignal(
        kind = kind,
        tier = tier,
        category = SignalCategory.BODY,
        severity = 60,
        facts = SignalFacts(),
        verdict = verdict,
        action = action,
        rationale = SignalRationale(confidence = Confidence.HIGH),
        dedupKey = "$kind|w27",
        surface = CoachSurface.PUSH,
        fallbackText = fallbackText,
    )

    private fun emitter(
        notifier: CoachNotifier,
        history: PushHistory,
        prefs: CoachNotifierPreferences,
        rateLimiter: RateLimiter = RateLimiter(),
        at: LocalDateTime = now,
    ) = CoachPushEmitter(notifier, history, prefs, rateLimiter) { at }

    // ── Preference gate ──────────────────────────────────────────────────────────────

    @Test
    fun `P0 not pushed when ambient nudges disabled`() = runTest {
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val emitter = emitter(notifier, history, FakePrefs(weekly = true, ambient = false))

        val shown = emitter.emit(signal(tier = SignalTier.P0), isWeeklyCheckIn = false)

        assertFalse(shown)
        assertTrue("notifier never called when ambient off", notifier.shown.isEmpty())
        assertTrue("nothing recorded", history.recorded.isEmpty())
    }

    @Test
    fun `weekly check-in not pushed when weekly pref disabled`() = runTest {
        val notifier = FakeNotifier()
        val history = FakeHistory()
        // ambient on, weekly off — the weekly path must consult the weekly pref only.
        val emitter = emitter(notifier, history, FakePrefs(weekly = false, ambient = true))

        val shown = emitter.emit(signal(kind = SignalKind.LOW_ADHERENCE, tier = SignalTier.P2), isWeeklyCheckIn = true)

        assertFalse(shown)
        assertTrue(notifier.shown.isEmpty())
    }

    // ── Allow path ───────────────────────────────────────────────────────────────────

    @Test
    fun `P0 with ambient on and RateLimiter-allow shows once and records`() = runTest {
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val emitter = emitter(notifier, history, FakePrefs(weekly = false, ambient = true))

        val shown = emitter.emit(signal(tier = SignalTier.P0), isWeeklyCheckIn = false)

        assertTrue(shown)
        assertEquals(1, notifier.shown.size)
        assertEquals(CoachPushChannel.COACHING, notifier.shown.first().channel)
        assertEquals(1, history.recorded.size)
        assertEquals(now.toKotlinLocalDateTime(), history.recorded.first().timestamp)
    }

    @Test
    fun `weekly check-in with weekly pref on and allow uses the weekly channel`() = runTest {
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val emitter = emitter(notifier, history, FakePrefs(weekly = true, ambient = false))

        // A P2 weekly check-in signal — only pushable because it is the weekly check-in.
        val shown = emitter.emit(
            signal(kind = SignalKind.LOW_ADHERENCE, tier = SignalTier.P2,
                action = CoachAction(CoachActionType.OPEN_WEEKLY_REVIEW, "Open review")),
            isWeeklyCheckIn = true,
        )

        assertTrue(shown)
        assertEquals(CoachPushChannel.WEEKLY_CHECK_IN, notifier.shown.single().channel)
        assertEquals(CoachActionType.OPEN_WEEKLY_REVIEW, notifier.shown.single().target)
        assertEquals(1, history.recorded.size)
    }

    // ── Restraint gate ───────────────────────────────────────────────────────────────

    @Test
    fun `RateLimiter reject (weekly cap) does not show or record`() = runTest {
        val notifier = FakeNotifier()
        // Two pushes already this rolling week -> weekly cap reached.
        val history = FakeHistory(
            listOf(
                PushEvent(now.minusDays(3).toKotlinLocalDateTime(), isCelebration = false),
                PushEvent(now.minusDays(5).toKotlinLocalDateTime(), isCelebration = false),
            ),
        )
        val emitter = emitter(notifier, history, FakePrefs(weekly = false, ambient = true))

        val shown = emitter.emit(signal(tier = SignalTier.P0), isWeeklyCheckIn = false)

        assertFalse(shown)
        assertTrue("RateLimiter rejected -> notifier not called", notifier.shown.isEmpty())
        assertEquals("no new event recorded", 2, history.recorded.size)
    }

    @Test
    fun `quiet hours suppress the push`() = runTest {
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val emitter = emitter(notifier, history, FakePrefs(weekly = false, ambient = true),
            at = LocalDateTime.of(2026, 7, 1, 23, 30)) // inside 22:00–07:00

        val shown = emitter.emit(signal(tier = SignalTier.P0), isWeeklyCheckIn = false)

        assertFalse(shown)
        assertTrue(notifier.shown.isEmpty())
    }

    @Test
    fun `a non-P0 non-weekly signal is rejected by the tier gate`() = runTest {
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val emitter = emitter(notifier, history, FakePrefs(weekly = true, ambient = true))

        val shown = emitter.emit(signal(kind = SignalKind.LOW_ADHERENCE, tier = SignalTier.P2), isWeeklyCheckIn = false)

        assertFalse(shown)
        assertTrue(notifier.shown.isEmpty())
    }

    // ── Decision-attached gate ───────────────────────────────────────────────────────

    @Test
    fun `a signal missing an action is blocked even when RateLimiter allows`() = runTest {
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val emitter = emitter(notifier, history, FakePrefs(weekly = false, ambient = true))

        // Tier + prefs + RateLimiter would all pass; the missing action (NONE) must still block it.
        val shown = emitter.emit(signal(action = CoachAction.None), isWeeklyCheckIn = false)

        assertFalse("no destination -> no push", shown)
        assertTrue(notifier.shown.isEmpty())
        assertTrue(history.recorded.isEmpty())
    }

    // ── Show-refusal (permission denied) ─────────────────────────────────────────────

    @Test
    fun `platform refusal to show is not recorded as a push`() = runTest {
        val notifier = FakeNotifier(shows = false) // e.g. notifications disabled
        val history = FakeHistory()
        val emitter = emitter(notifier, history, FakePrefs(weekly = false, ambient = true))

        val shown = emitter.emit(signal(tier = SignalTier.P0), isWeeklyCheckIn = false)

        assertFalse(shown)
        assertEquals("show was attempted", 1, notifier.shown.size)
        assertTrue("but nothing recorded — the slot is not burned", history.recorded.isEmpty())
    }

    // ── Celebration channel ──────────────────────────────────────────────────────────

    @Test
    fun `a celebration P0 records isCelebration on the ledger`() = runTest {
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val emitter = emitter(notifier, history, FakePrefs(weekly = false, ambient = true))

        val shown = emitter.emit(
            signal(kind = SignalKind.NEW_PR, tier = SignalTier.P0,
                action = CoachAction(CoachActionType.OPEN_TRAINING, "Open training")),
            isWeeklyCheckIn = false,
        )

        assertTrue(shown)
        assertTrue("celebration flagged on the recorded event", history.recorded.single().isCelebration)
    }

    @Test
    fun `a weekly check-in celebration is recorded as a non-celebration`() = runTest {
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val emitter = emitter(notifier, history, FakePrefs(weekly = true, ambient = false))

        // A RECOMP_WIN weekly winner is a celebration signal, but the weekly-report channel must not
        // consume the ambient celebration budget — record it with isCelebration = false.
        val shown = emitter.emit(
            signal(kind = SignalKind.RECOMP_WIN, tier = SignalTier.P0,
                action = CoachAction(CoachActionType.OPEN_WEEKLY_REVIEW, "Open review")),
            isWeeklyCheckIn = true,
        )

        assertTrue(shown)
        assertEquals(CoachPushChannel.WEEKLY_CHECK_IN, notifier.shown.single().channel)
        assertFalse("weekly push must not burn the celebration slot", history.recorded.single().isCelebration)
    }
}
