package com.zack.recomptracker.domain.coach

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exhaustive unit tests for the pure serialization + dedup-ledger logic. No Android, no DataStore. */
class CoachInboxSerializationTest {

    private fun fullSignal() = CoachSignal(
        kind = SignalKind.RECOMP_WIN,
        tier = SignalTier.P1,
        category = SignalCategory.BODY,
        severity = 73,
        facts = SignalFacts(
            mapOf(
                "weightTrendKgPerWk" to "-0.02",
                "waistTrendCmPerWk" to "-0.30",
            ),
        ),
        verdict = "Hold calories — recomposition is working.",
        action = CoachAction(CoachActionType.OPEN_WEEKLY_REVIEW, "See review"),
        rationale = SignalRationale(
            primaryCauseByDomain = mapOf("body" to "waist down while weight flat"),
            behaviorToOutcome = "High adherence → recomp",
            confidence = Confidence.HIGH,
        ),
        dedupKey = "RECOMP_WIN|2026-W27",
        surface = CoachSurface.WEEKLY,
        fallbackText = "Weight flat, waist down — keep going.",
    )

    // ── Signal round-trip ──

    @Test
    fun `fully populated signal round-trips`() {
        val original = fullSignal()
        val decoded = CoachInboxSerialization.decodeSignal(CoachInboxSerialization.encodeSignal(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `signal with default action and empty rationale maps round-trips`() {
        val minimal = fullSignal().copy(
            action = CoachAction.None,
            facts = SignalFacts(),
            rationale = SignalRationale(confidence = Confidence.INSUFFICIENT),
        )
        val decoded = CoachInboxSerialization.decodeSignal(CoachInboxSerialization.encodeSignal(minimal))
        assertEquals(minimal, decoded)
    }

    @Test
    fun `blank or null signal string decodes to null`() {
        assertNull(CoachInboxSerialization.decodeSignal(null))
        assertNull(CoachInboxSerialization.decodeSignal(""))
        assertNull(CoachInboxSerialization.decodeSignal("   "))
    }

    @Test
    fun `malformed signal json decodes to null not throw`() {
        assertNull(CoachInboxSerialization.decodeSignal("{ not valid json"))
        assertNull(CoachInboxSerialization.decodeSignal("{\"kind\":\"NOT_A_KIND\"}"))
        assertNull(CoachInboxSerialization.decodeSignal("[]"))
    }

    // ── Ledger round-trip ──

    @Test
    fun `ledger round-trips`() {
        val ledger = mapOf(
            "RECOMP_WIN|2026-W27" to LocalDate(2026, 7, 1),
            "NEW_PR|bench|2026-W27" to LocalDate(2026, 6, 28),
        )
        val decoded = CoachInboxSerialization.decodeLedger(CoachInboxSerialization.encodeLedger(ledger))
        assertEquals(ledger, decoded)
    }

    @Test
    fun `empty ledger round-trips`() {
        val decoded = CoachInboxSerialization.decodeLedger(CoachInboxSerialization.encodeLedger(emptyMap()))
        assertEquals(emptyMap<String, LocalDate>(), decoded)
    }

    @Test
    fun `blank or malformed ledger decodes to empty map`() {
        assertEquals(emptyMap<String, LocalDate>(), CoachInboxSerialization.decodeLedger(null))
        assertEquals(emptyMap<String, LocalDate>(), CoachInboxSerialization.decodeLedger(""))
        assertEquals(emptyMap<String, LocalDate>(), CoachInboxSerialization.decodeLedger("{ broken"))
    }

    @Test
    fun `ledger entries with unparseable dates are dropped, valid ones kept`() {
        val raw = "{\"good\":\"2026-07-01\",\"bad\":\"not-a-date\"}"
        val decoded = CoachInboxSerialization.decodeLedger(raw)
        assertEquals(mapOf("good" to LocalDate(2026, 7, 1)), decoded)
    }

    // ── markSeen ──

    @Test
    fun `markSeen adds a new dedupKey`() {
        val date = LocalDate(2026, 7, 1)
        val result = CoachInboxSerialization.markSeen(emptyMap(), "RECOMP_WIN|2026-W27", date)
        assertEquals(mapOf("RECOMP_WIN|2026-W27" to date), result)
    }

    @Test
    fun `markSeen updates an existing dedupKey with the newer date (last write wins)`() {
        val start = mapOf("k" to LocalDate(2026, 6, 1))
        val newer = LocalDate(2026, 7, 1)
        val result = CoachInboxSerialization.markSeen(start, "k", newer)
        assertEquals(newer, result["k"])
        assertEquals(1, result.size)
    }

    @Test
    fun `markSeen prunes stale entries while adding`() {
        val today = LocalDate(2026, 7, 1)
        val start = mapOf("stale" to today.minus(40, DateTimeUnit.DAY), "recent" to today.minus(5, DateTimeUnit.DAY))
        val result = CoachInboxSerialization.markSeen(start, "new", today)
        assertEquals(setOf("recent", "new"), result.keys)
    }

    // ── prune ──

    @Test
    fun `prune drops entries older than the retention window`() {
        val ref = LocalDate(2026, 7, 1)
        val ledger = mapOf(
            "tooOld" to ref.minus(31, DateTimeUnit.DAY),
            "boundary" to ref.minus(30, DateTimeUnit.DAY),
            "recent" to ref.minus(1, DateTimeUnit.DAY),
        )
        val pruned = CoachInboxSerialization.prune(ledger, ref)
        assertEquals(setOf("boundary", "recent"), pruned.keys)
    }

    @Test
    fun `prune keeps future-dated entries`() {
        val ref = LocalDate(2026, 7, 1)
        val ledger = mapOf("future" to ref.plus(3, DateTimeUnit.DAY))
        assertEquals(ledger, CoachInboxSerialization.prune(ledger, ref))
    }

    @Test
    fun `prune on empty ledger is empty`() {
        assertTrue(CoachInboxSerialization.prune(emptyMap(), LocalDate(2026, 7, 1)).isEmpty())
    }
}
