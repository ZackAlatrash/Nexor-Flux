package com.zack.recomptracker.data.coach

import com.zack.recomptracker.domain.coach.SignalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive unit tests for the pure append/cap/idempotence/recurrence/narrative logic backing
 * [CoachJourneyStore]. No Android, no DataStore. Mirrors [CoachInboxSerializationTest].
 */
class CoachJourneySerializationTest {

    private fun fired(
        kind: SignalKind = SignalKind.TRAINING_PLATEAU,
        dedupKey: String = "TRAINING_PLATEAU|bench",
        week: String = "2026-W27|...",
        date: String = "2026-07-01",
    ) = FiredSignalRecord(kind = kind, dedupKey = dedupKey, weekSignature = week, dateIso = date)

    private fun verdict(
        week: String = "2026-W27|HOLD",
        end: String = "2026-07-05",
        text: String = "Hold calories.",
    ) = WeeklyVerdictRecord(weekSignature = week, weekEndDateIso = end, verdict = text)

    // ── Fired-history append + cap ───────────────────────────────────────────

    @Test
    fun `appendFired keeps insertion order and caps to the newest entries`() {
        var history = emptyList<FiredSignalRecord>()
        repeat(CoachJourneySerialization.FIRED_HISTORY_CAP + 5) { i ->
            history = CoachJourneySerialization.appendFired(history, fired(date = "2026-07-%02d".format(i + 1)))
        }
        assertEquals(CoachJourneySerialization.FIRED_HISTORY_CAP, history.size)
        // Oldest 5 dropped; newest retained (last appended is last in list).
        assertEquals("2026-07-06", history.first().dateIso)
        assertEquals("2026-07-%02d".format(CoachJourneySerialization.FIRED_HISTORY_CAP + 5), history.last().dateIso)
    }

    @Test
    fun `fired history round-trips through JSON`() {
        val history = listOf(
            fired(kind = SignalKind.NEW_PR, dedupKey = "NEW_PR|squat", week = "2026-W26|X", date = "2026-06-25"),
            fired(),
        )
        val decoded = CoachJourneySerialization.decodeFired(CoachJourneySerialization.encodeFired(history))
        assertEquals(history, decoded)
    }

    @Test
    fun `decodeFired tolerates blank and malformed input`() {
        assertEquals(emptyList<FiredSignalRecord>(), CoachJourneySerialization.decodeFired(null))
        assertEquals(emptyList<FiredSignalRecord>(), CoachJourneySerialization.decodeFired(""))
        assertEquals(emptyList<FiredSignalRecord>(), CoachJourneySerialization.decodeFired("{not json"))
    }

    // ── Weekly verdict append + idempotence + cap ────────────────────────────

    @Test
    fun `appendVerdict ignores a duplicate week signature (idempotent)`() {
        var verdicts = emptyList<WeeklyVerdictRecord>()
        verdicts = CoachJourneySerialization.appendVerdict(verdicts, verdict(week = "2026-W27|HOLD"))
        verdicts = CoachJourneySerialization.appendVerdict(verdicts, verdict(week = "2026-W27|HOLD", text = "different"))
        assertEquals(1, verdicts.size)
        assertEquals("Hold calories.", verdicts.single().verdict)
    }

    @Test
    fun `appendVerdict caps to the newest weekly verdicts`() {
        var verdicts = emptyList<WeeklyVerdictRecord>()
        repeat(CoachJourneySerialization.VERDICT_CAP + 3) { i ->
            verdicts = CoachJourneySerialization.appendVerdict(verdicts, verdict(week = "week-$i", end = "2026-07-%02d".format(i + 1)))
        }
        assertEquals(CoachJourneySerialization.VERDICT_CAP, verdicts.size)
        assertEquals("week-3", verdicts.first().weekSignature)
        assertEquals("week-${CoachJourneySerialization.VERDICT_CAP + 2}", verdicts.last().weekSignature)
    }

    @Test
    fun `verdict history round-trips through JSON`() {
        val verdicts = listOf(verdict(week = "a"), verdict(week = "b", text = "Cut 100 kcal."))
        val decoded = CoachJourneySerialization.decodeVerdicts(CoachJourneySerialization.encodeVerdicts(verdicts))
        assertEquals(verdicts, decoded)
    }

    // ── Recurrence detection ─────────────────────────────────────────────────

    @Test
    fun `hasRecurred is false when the kind fired only once`() {
        val history = listOf(fired(kind = SignalKind.TRAINING_PLATEAU, week = "2026-W25|x"))
        assertFalse(CoachJourneySerialization.hasRecurred(history, SignalKind.TRAINING_PLATEAU))
    }

    @Test
    fun `hasRecurred is false when the kind fired in consecutive weeks (no gap)`() {
        val history = listOf(
            fired(kind = SignalKind.TRAINING_PLATEAU, week = "2026-W25|x", date = "2026-06-16"),
            fired(kind = SignalKind.TRAINING_PLATEAU, week = "2026-W26|x", date = "2026-06-23"),
        )
        assertFalse(CoachJourneySerialization.hasRecurred(history, SignalKind.TRAINING_PLATEAU))
    }

    @Test
    fun `hasRecurred is true when the kind fired, went quiet a week, then fired again`() {
        val history = listOf(
            fired(kind = SignalKind.TRAINING_PLATEAU, week = "2026-W24|x", date = "2026-06-09"),
            // gap: W25 has no plateau fire
            fired(kind = SignalKind.LOW_ADHERENCE, week = "2026-W25|x", date = "2026-06-16"),
            fired(kind = SignalKind.TRAINING_PLATEAU, week = "2026-W26|x", date = "2026-06-23"),
        )
        assertTrue(CoachJourneySerialization.hasRecurred(history, SignalKind.TRAINING_PLATEAU))
    }

    @Test
    fun `recurredKinds lists only the kinds that recurred`() {
        val history = listOf(
            fired(kind = SignalKind.TRAINING_PLATEAU, week = "2026-W24|x"),
            fired(kind = SignalKind.LOW_ADHERENCE, week = "2026-W25|x"),
            fired(kind = SignalKind.TRAINING_PLATEAU, week = "2026-W26|x"),
        )
        assertEquals(listOf(SignalKind.TRAINING_PLATEAU), CoachJourneySerialization.recurredKinds(history))
    }

    // ── Narrative ────────────────────────────────────────────────────────────

    @Test
    fun `narrative is empty below the two-verdict threshold`() {
        assertEquals("", CoachJourneySerialization.narrative(emptyList(), emptyList()))
        assertEquals("", CoachJourneySerialization.narrative(listOf(verdict()), emptyList()))
    }

    @Test
    fun `narrative summarizes recent verdicts and notes a recurring signal`() {
        val verdicts = listOf(
            verdict(week = "2026-W25|x", end = "2026-06-21", text = "Hold calories."),
            verdict(week = "2026-W26|x", end = "2026-06-28", text = "Cut 100 kcal."),
            verdict(week = "2026-W27|x", end = "2026-07-05", text = "Hold calories."),
        )
        val history = listOf(
            fired(kind = SignalKind.TRAINING_PLATEAU, week = "2026-W24|x"),
            fired(kind = SignalKind.LOW_ADHERENCE, week = "2026-W25|x"),
            fired(kind = SignalKind.TRAINING_PLATEAU, week = "2026-W26|x"),
        )
        val text = CoachJourneySerialization.narrative(verdicts, history)
        assertTrue("expected non-empty narrative", text.isNotBlank())
        // References the most recent verdict text.
        assertTrue("should mention latest verdict", text.contains("Hold calories."))
        // Notes the recurrence (only from stored facts — a kind that recurred).
        assertTrue("should note the recurring plateau", text.contains("TRAINING_PLATEAU", ignoreCase = true))
    }

    @Test
    fun `narrative invents no numbers absent from stored verdicts`() {
        val verdicts = listOf(verdict(week = "a", text = "Hold."), verdict(week = "b", text = "Hold."))
        val text = CoachJourneySerialization.narrative(verdicts, emptyList())
        // The only digits allowed are those already present in the stored strings (none here).
        assertFalse("narrative must not introduce numbers", text.any { it.isDigit() })
    }
}
