package com.zack.recomptracker.data.coach

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.domain.coach.CoachContext
import com.zack.recomptracker.domain.coach.CoachContextFixtures
import com.zack.recomptracker.domain.coach.CoachDetector
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.CoachSignalEngine
import com.zack.recomptracker.domain.coach.CoachSurface
import com.zack.recomptracker.domain.coach.Confidence
import com.zack.recomptracker.domain.coach.SignalCategory
import com.zack.recomptracker.domain.coach.SignalFacts
import com.zack.recomptracker.domain.coach.SignalKind
import com.zack.recomptracker.domain.coach.SignalRationale
import com.zack.recomptracker.domain.coach.SignalSelector
import com.zack.recomptracker.domain.coach.SignalTier
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
        contextProvider: suspend () -> CoachContext = { CoachContextFixtures.context(asOf = dateProvider.today()) },
    ) = CoachDigestCoordinator(
        contextProvider = contextProvider,
        engine = engine,
        selector = SignalSelector(),
        inbox = inbox,
        aiEnabledFlow = MutableStateFlow(aiEnabled),
        dateProvider = dateProvider,
        appScope = scope,
    )

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
