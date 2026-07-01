package com.zack.recomptracker.data.coach

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.domain.coach.CoachContext
import com.zack.recomptracker.domain.coach.CoachContextFixtures
import com.zack.recomptracker.domain.coach.CoachDetector
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.CoachSignalEngine
import com.zack.recomptracker.domain.coach.CoachSurface
import com.zack.recomptracker.domain.coach.Confidence
import com.zack.recomptracker.domain.coach.HistoryContext
import com.zack.recomptracker.domain.coach.SignalCategory
import com.zack.recomptracker.domain.coach.SignalFacts
import com.zack.recomptracker.domain.coach.SignalKind
import com.zack.recomptracker.domain.coach.SignalRationale
import com.zack.recomptracker.domain.coach.SignalSelector
import com.zack.recomptracker.domain.coach.SignalTier
import com.zack.recomptracker.domain.coach.WeeklyReviewSnapshot
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoachDigestCoordinatorTest {

    private val today = LocalDate.of(2026, 7, 1)

    private class MutableDateProvider(var day: LocalDate) : DateProvider {
        override fun today(): LocalDate = day
    }

    /** In-memory [CoachInbox] fake — records every call so tests can assert the digest's writes. */
    private class FakeInbox(
        private var lastRun: LocalDate? = null,
        seed: Map<String, LocalDate> = emptyMap(),
    ) : CoachInbox {
        var staged: CoachSignal? = null
        var stageCalls = 0
        val seen = seed.toMutableMap()
        private val currentFlow = MutableStateFlow<CoachSignal?>(null)

        override val current = currentFlow

        override suspend fun stage(signal: CoachSignal?) {
            staged = signal
            currentFlow.value = signal
            stageCalls++
        }

        override suspend fun dismissCurrent() {
            staged = null
            currentFlow.value = null
        }

        override suspend fun markSeen(dedupKey: String, date: LocalDate) {
            seen[dedupKey] = date
        }

        override suspend fun seenLedger(): Map<String, LocalDate> = seen.toMap()

        override suspend fun lastRunDate(): LocalDate? = lastRun

        override suspend fun setLastRunDate(date: LocalDate) {
            lastRun = date
        }
    }

    private fun signal(
        kind: SignalKind = SignalKind.LOW_ADHERENCE,
        tier: SignalTier = SignalTier.P1,
        severity: Int = 50,
        dedupKey: String = "LOW_ADHERENCE|w27",
        surface: CoachSurface = CoachSurface.TODAY,
    ) = CoachSignal(
        kind = kind,
        tier = tier,
        category = SignalCategory.NUTRITION,
        severity = severity,
        facts = SignalFacts(),
        verdict = "Tighten up your logging this week.",
        rationale = SignalRationale(confidence = Confidence.HIGH),
        dedupKey = dedupKey,
        surface = surface,
        fallbackText = "Adherence dipped — aim to log every meal.",
    )

    /** In-memory [CoachJourney] fake — records every write so tests can assert the digest's memory. */
    private class FakeJourney : CoachJourney {
        val firedSignals = mutableListOf<Pair<CoachSignal, String>>()
        val verdicts = mutableListOf<Triple<String, String, String>>()

        override suspend fun recordFiredSignal(signal: CoachSignal, weekSignature: String) {
            firedSignals += signal to weekSignature
        }

        override suspend fun recordWeeklyVerdict(weekSignature: String, weekEndDateIso: String, verdict: String) {
            verdicts += Triple(weekSignature, weekEndDateIso, verdict)
        }

        override suspend fun journeyNarrative(): String = ""
    }

    /** A detector that always fires [emit]; `null` = never fires. */
    private fun detectorOf(emit: CoachSignal?) = CoachDetector { emit }

    private fun engineWith(vararg signals: CoachSignal?) =
        CoachSignalEngine(signals.map { detectorOf(it) })

    private fun coordinator(
        inbox: CoachInbox,
        engine: CoachSignalEngine,
        aiEnabled: Boolean = true,
        dateProvider: DateProvider = MutableDateProvider(today),
        scope: CoroutineScope,
        journey: CoachJourney = FakeJourney(),
        contextProvider: suspend () -> CoachContext = { CoachContextFixtures.context(asOf = dateProvider.today()) },
    ) = CoachDigestCoordinator(
        contextProvider = contextProvider,
        engine = engine,
        selector = SignalSelector(),
        inbox = inbox,
        aiEnabledFlow = MutableStateFlow(aiEnabled),
        dateProvider = dateProvider,
        appScope = scope,
        journey = journey,
    )

    private fun contextWithReview(
        weekStart: LocalDate = LocalDate.of(2026, 6, 22),
        verdict: String = "Hold calories",
        signature: String = "2026-06-22|2400|HOLD",
    ): suspend () -> CoachContext = {
        CoachContextFixtures.context(
            asOf = today,
            history = HistoryContext(
                weeklyReviews = listOf(WeeklyReviewSnapshot(weekStart, verdict, signature)),
            ),
        )
    }

    // ── push fakes (Phase 5) ─────────────────────────────────────────────────────────

    private class FakeNotifier(private val shows: Boolean = true) : CoachNotifier {
        val shown = mutableListOf<CoachPushPayload>()
        override fun ensureChannels() = Unit
        override fun show(payload: CoachPushPayload): Boolean { shown += payload; return shows }
    }

    private class FakeHistory : PushHistory {
        val recorded = mutableListOf<com.zack.recomptracker.domain.coach.PushEvent>()
        override suspend fun record(event: com.zack.recomptracker.domain.coach.PushEvent) { recorded += event }
        override suspend fun recentPushes() = recorded.toList()
    }

    private class FakeNotifPrefs(
        private val weekly: Boolean = true,
        private val ambient: Boolean = true,
        var lastWeeklySig: String = "",
    ) : CoachNotifierPreferences {
        override val weeklyCheckInPushEnabled = MutableStateFlow(weekly)
        override val ambientNudgesEnabled = MutableStateFlow(ambient)
        override suspend fun quietHours() = com.zack.recomptracker.domain.coach.QuietHours()
        override suspend fun setWeeklyCheckInPushEnabled(enabled: Boolean) = Unit
        override suspend fun setAmbientNudgesEnabled(enabled: Boolean) = Unit
        override suspend fun setQuietHours(startHour: Int, endHour: Int) = Unit
        override suspend fun lastPushedWeeklySignature() = lastWeeklySig
        override suspend fun setLastPushedWeeklySignature(signature: String) { lastWeeklySig = signature }
    }

    // Mid-day mid-week so quiet-hours/consecutive-day gates pass by default.
    private val nowInstant = java.time.LocalDateTime.of(2026, 7, 1, 13, 0)

    // ── push wiring ──────────────────────────────────────────────────────────────────

    @Test
    fun `a P0 today winner is pushed via the ambient channel`() = runTest {
        val inbox = FakeInbox()
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val prefs = FakeNotifPrefs(ambient = true)
        val emitter = CoachPushEmitter(notifier, history, prefs) { nowInstant }
        val p0 = signal(
            kind = SignalKind.FAT_GAIN_WARNING, tier = SignalTier.P0, dedupKey = "FGW|w27",
        ).copy(action = com.zack.recomptracker.domain.coach.CoachAction(
            com.zack.recomptracker.domain.coach.CoachActionType.LOG_WEIGHT, "Log weight"))
        val coordinator = CoachDigestCoordinator(
            contextProvider = { CoachContextFixtures.context(asOf = today) },
            engine = engineWith(p0), selector = SignalSelector(), inbox = inbox,
            aiEnabledFlow = MutableStateFlow(true), dateProvider = MutableDateProvider(today),
            appScope = this, pushEmitter = emitter, notificationPreferences = prefs,
        )

        coordinator.run()

        assertEquals(1, notifier.shown.size)
        assertEquals(CoachPushChannel.COACHING, notifier.shown.first().channel)
        assertEquals(1, history.recorded.size)
    }

    @Test
    fun `two concurrent run calls push and record only once`() = runTest {
        // The WorkManager worker and the foreground path can call run() concurrently. runLock must
        // serialise them so the same P0 winner isn't double-pushed before either records.
        val inbox = FakeInbox()
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val prefs = FakeNotifPrefs(ambient = true)
        val emitter = CoachPushEmitter(notifier, history, prefs) { nowInstant }
        val p0 = signal(
            kind = SignalKind.FAT_GAIN_WARNING, tier = SignalTier.P0, dedupKey = "FGW|w27",
        ).copy(action = com.zack.recomptracker.domain.coach.CoachAction(
            com.zack.recomptracker.domain.coach.CoachActionType.LOG_WEIGHT, "Log weight"))
        val coordinator = CoachDigestCoordinator(
            contextProvider = { CoachContextFixtures.context(asOf = today) },
            engine = engineWith(p0), selector = SignalSelector(), inbox = inbox,
            aiEnabledFlow = MutableStateFlow(true), dateProvider = MutableDateProvider(today),
            appScope = this, pushEmitter = emitter, notificationPreferences = prefs,
        )

        val a = launch { coordinator.run() }
        val b = launch { coordinator.run() }
        a.join()
        b.join()

        assertEquals("winner pushed exactly once despite two concurrent runs", 1, notifier.shown.size)
        assertEquals("exactly one push recorded", 1, history.recorded.size)
    }

    @Test
    fun `a non-P0 today winner is not pushed`() = runTest {
        val inbox = FakeInbox()
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val prefs = FakeNotifPrefs(ambient = true)
        val emitter = CoachPushEmitter(notifier, history, prefs) { nowInstant }
        val coordinator = CoachDigestCoordinator(
            contextProvider = { CoachContextFixtures.context(asOf = today) },
            engine = engineWith(signal(tier = SignalTier.P2)), selector = SignalSelector(), inbox = inbox,
            aiEnabledFlow = MutableStateFlow(true), dateProvider = MutableDateProvider(today),
            appScope = this, pushEmitter = emitter, notificationPreferences = prefs,
        )

        coordinator.run()

        assertTrue("P2 does not clear the tier gate", notifier.shown.isEmpty())
    }

    @Test
    fun `weekly check-in fires once for a new signature and not again`() = runTest {
        val notifier = FakeNotifier()
        val history = FakeHistory()
        val prefs = FakeNotifPrefs(weekly = true)
        val emitter = CoachPushEmitter(notifier, history, prefs) { nowInstant }
        val weeklySignal = signal(
            kind = SignalKind.WEEKLY_VERDICT, tier = SignalTier.P2, dedupKey = "WV|w25",
            surface = CoachSurface.WEEKLY,
        ).copy(action = com.zack.recomptracker.domain.coach.CoachAction(
            com.zack.recomptracker.domain.coach.CoachActionType.OPEN_WEEKLY_REVIEW, "Open review"))
        fun makeCoordinator(inbox: CoachInbox) = CoachDigestCoordinator(
            contextProvider = contextWithReview(signature = "2026-06-22|2400|HOLD"),
            engine = engineWith(weeklySignal), selector = SignalSelector(), inbox = inbox,
            aiEnabledFlow = MutableStateFlow(true), dateProvider = MutableDateProvider(today),
            appScope = this, pushEmitter = emitter, notificationPreferences = prefs,
        )

        makeCoordinator(FakeInbox()).run()
        assertEquals("weekly push fired for the new signature", 1, notifier.shown.size)
        assertEquals(CoachPushChannel.WEEKLY_CHECK_IN, notifier.shown.first().channel)
        assertEquals("2026-06-22|2400|HOLD", prefs.lastWeeklySig)

        // Re-run same week -> no second weekly push (idempotent per signature).
        makeCoordinator(FakeInbox()).run()
        assertEquals("same signature -> no second weekly push", 1, notifier.shown.size)
    }

    @Test
    fun `no push emitter injected leaves the digest a pure stager`() = runTest {
        val inbox = FakeInbox()
        val coordinator = coordinator(inbox = inbox, engine = engineWith(signal(tier = SignalTier.P0)), scope = this)

        coordinator.run() // must not throw with no emitter/prefs

        assertEquals(1, inbox.stageCalls)
    }

    // ── run() ────────────────────────────────────────────────────────────────────

    @Test
    fun `AI disabled stages null and never builds or evaluates`() = runTest {
        val inbox = FakeInbox()
        var built = false
        val coordinator = coordinator(
            inbox = inbox,
            engine = engineWith(signal()),
            aiEnabled = false,
            scope = this,
            contextProvider = { built = true; CoachContextFixtures.context(asOf = today) },
        )

        coordinator.run()

        assertEquals(1, inbox.stageCalls)
        assertNull(inbox.staged)
        assertTrue("contextProvider must not run when AI is disabled", !built)
        assertNull("lastRunDate not stamped when disabled", inbox.lastRunDate())
    }

    @Test
    fun `a firing detector stages the selector winner and marks it seen`() = runTest {
        val inbox = FakeInbox()
        val winner = signal(dedupKey = "LOW_ADHERENCE|w27")
        val coordinator = coordinator(inbox = inbox, engine = engineWith(winner), scope = this)

        coordinator.run()

        assertEquals(winner, inbox.staged)
        assertEquals(today, inbox.seen["LOW_ADHERENCE|w27"])
        assertEquals(today, inbox.lastRunDate())
    }

    @Test
    fun `a staged winner is recorded into the journey under the current week signature`() = runTest {
        val inbox = FakeInbox()
        val journey = FakeJourney()
        val winner = signal(dedupKey = "LOW_ADHERENCE|w27")
        val coordinator = coordinator(
            inbox = inbox,
            engine = engineWith(winner),
            scope = this,
            journey = journey,
            contextProvider = contextWithReview(signature = "2026-06-22|2400|HOLD"),
        )

        coordinator.run()

        assertEquals(1, journey.firedSignals.size)
        assertEquals(winner, journey.firedSignals.first().first)
        assertEquals("2026-06-22|2400|HOLD", journey.firedSignals.first().second)
    }

    @Test
    fun `silence records no fired signal into the journey`() = runTest {
        val inbox = FakeInbox()
        val journey = FakeJourney()
        val coordinator = coordinator(
            inbox = inbox,
            engine = engineWith(null, null),
            scope = this,
            journey = journey,
            contextProvider = contextWithReview(),
        )

        coordinator.run()

        assertTrue("no winner -> nothing fired-recorded", journey.firedSignals.isEmpty())
    }

    @Test
    fun `each weekly review verdict in context is recorded into the journey`() = runTest {
        val inbox = FakeInbox()
        val journey = FakeJourney()
        val coordinator = coordinator(
            inbox = inbox,
            engine = engineWith(null),
            scope = this,
            journey = journey,
            contextProvider = contextWithReview(
                weekStart = LocalDate.of(2026, 6, 22),
                verdict = "Hold calories",
                signature = "2026-06-22|2400|HOLD",
            ),
        )

        coordinator.run()

        assertEquals(1, journey.verdicts.size)
        val (sig, weekEndIso, verdict) = journey.verdicts.first()
        assertEquals("2026-06-22|2400|HOLD", sig)
        assertEquals("Hold calories", verdict)
        assertEquals("2026-06-22", weekEndIso)
    }

    @Test
    fun `nothing fires stages null silence and still stamps lastRunDate`() = runTest {
        val inbox = FakeInbox()
        val coordinator = coordinator(inbox = inbox, engine = engineWith(null, null), scope = this)

        coordinator.run()

        assertEquals(1, inbox.stageCalls)
        assertNull(inbox.staged)
        assertTrue("no dedup keys marked when silent", inbox.seen.isEmpty())
        assertEquals(today, inbox.lastRunDate())
    }

    @Test
    fun `a signal on cooldown in the seen ledger is not re-staged`() = runTest {
        val inbox = FakeInbox(seed = mapOf("LOW_ADHERENCE|w27" to today.minusDays(3)))
        val winner = signal(dedupKey = "LOW_ADHERENCE|w27")
        val coordinator = coordinator(inbox = inbox, engine = engineWith(winner), scope = this)

        coordinator.run()

        assertNull("within 7-day cooldown -> suppressed -> silence", inbox.staged)
        assertEquals(today, inbox.lastRunDate())
    }

    @Test
    fun `a WEEKLY-surface signal does not leak into the Today slot`() = runTest {
        val inbox = FakeInbox()
        // A high-priority P0 WEEKLY signal (e.g. the recomp verdict) must NOT be staged to Today —
        // it belongs to the weekly check-in surface.
        val weeklyOnly = signal(
            kind = SignalKind.RECOMP_WIN,
            tier = SignalTier.P0,
            dedupKey = "RECOMP_WIN|w27",
            surface = CoachSurface.WEEKLY,
        )
        val coordinator = coordinator(inbox = inbox, engine = engineWith(weeklyOnly), scope = this)

        coordinator.run()

        assertNull("WEEKLY signal must not reach the Today slot", inbox.staged)
        assertEquals(today, inbox.lastRunDate())
    }

    @Test
    fun `a TODAY signal is staged even when a higher-tier WEEKLY signal also fires`() = runTest {
        val inbox = FakeInbox()
        val weekly = signal(kind = SignalKind.RECOMP_WIN, tier = SignalTier.P0, dedupKey = "RECOMP_WIN|w27", surface = CoachSurface.WEEKLY)
        val todaySignal = signal(kind = SignalKind.QUIET_WEIGH_INS, tier = SignalTier.P2, dedupKey = "QUIET|w27", surface = CoachSurface.TODAY)
        val coordinator = coordinator(inbox = inbox, engine = engineWith(weekly, todaySignal), scope = this)

        coordinator.run()

        assertEquals("the TODAY signal wins the Today slot", todaySignal, inbox.staged)
    }

    // ── runIfDue() ─────────────────────────────────────────────────────────────────

    @Test
    fun `runIfDue is a no-op when lastRunDate equals today`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val inbox = FakeInbox(lastRun = today)
        val coordinator = coordinator(
            inbox = inbox,
            engine = engineWith(signal()),
            dateProvider = MutableDateProvider(today),
            scope = scope,
        )

        coordinator.runIfDue()
        runCurrent()

        assertEquals("already ran today -> skipped", 0, inbox.stageCalls)
    }

    @Test
    fun `runIfDue runs when not yet run today`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val inbox = FakeInbox(lastRun = today.minusDays(1))
        val winner = signal()
        val coordinator = coordinator(
            inbox = inbox,
            engine = engineWith(winner),
            dateProvider = MutableDateProvider(today),
            scope = scope,
        )

        coordinator.runIfDue()
        runCurrent()

        assertEquals(1, inbox.stageCalls)
        assertEquals(winner, inbox.staged)
        assertEquals(today, inbox.lastRunDate())
    }

    // ── background scheduler delegation ─────────────────────────────────────────────

    @Test
    fun `enable and disable delegate to the scheduler`() = runTest {
        val enables = AtomicInteger(0)
        val disables = AtomicInteger(0)
        val scheduler = object : CoachDigestScheduler {
            override fun enable() { enables.incrementAndGet() }
            override fun disable() { disables.incrementAndGet() }
        }
        val coordinator = CoachDigestCoordinator(
            contextProvider = { CoachContextFixtures.context(asOf = today) },
            engine = engineWith(),
            selector = SignalSelector(),
            inbox = FakeInbox(),
            aiEnabledFlow = MutableStateFlow(true),
            dateProvider = MutableDateProvider(today),
            appScope = this,
            scheduler = scheduler,
        )

        coordinator.enableBackgroundDigest()
        coordinator.disableBackgroundDigest()

        assertEquals(1, enables.get())
        assertEquals(1, disables.get())
    }
}
