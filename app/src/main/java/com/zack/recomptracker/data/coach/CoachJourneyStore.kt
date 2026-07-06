package com.zack.recomptracker.data.coach

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.SignalKind
import kotlinx.coroutines.flow.first

private val Context.coachJourneyDataStore by preferencesDataStore(name = "coach_journey")

/**
 * The minimal record/read surface the coach depends on for its multi-week memory, extracted so
 * callers ([CoachDigestCoordinator], the briefing generator, the chat prompt) can be unit-tested
 * against a simple in-memory fake instead of the concrete DataStore class. [CoachJourneyStore] is
 * the production implementation. Mirrors [CoachInbox] / [CoachInboxRepository].
 */
interface CoachJourney {
    /** Append a fired [signal] under [weekSignature], stamped with today's date (capped, rolling). */
    suspend fun recordFiredSignal(signal: CoachSignal, weekSignature: String)

    /** Append a weekly [verdict] for [weekSignature] / [weekEndDateIso]. Idempotent per signature. */
    suspend fun recordWeeklyVerdict(weekSignature: String, weekEndDateIso: String, verdict: String)

    /**
     * A compact, deterministic multi-week narrative for prompts, built ONLY from stored facts. Empty
     * string when there isn't enough history. Decoration text — safe to drop.
     */
    suspend fun journeyNarrative(): String
}

/** Inert [CoachJourney] for tests / Context-free construction: records nothing, narrates nothing. */
object NoopCoachJourney : CoachJourney {
    override suspend fun recordFiredSignal(signal: CoachSignal, weekSignature: String) = Unit
    override suspend fun recordWeeklyVerdict(weekSignature: String, weekEndDateIso: String, verdict: String) = Unit
    override suspend fun journeyNarrative(): String = ""
}

/**
 * Append-only, offline-first local ledger giving the coach memory across weeks. Persists a rolling
 * fired-signal history (for recurrence detection) and a rolling list of the last few weekly verdicts
 * (for a multi-week narrative), so prompts can reference the journey — e.g. "3 weeks ago your bench
 * stalled; it's moving again." See `docs/ai-redesign/08-technical-architecture.md` §10 and roadmap
 * Phase 5.
 *
 * Mirrors [CoachInboxRepository]: a `preferencesDataStore` delegate, `.edit{}` suspend setters, typed
 * [Keys], and kotlinx.serialization JSON for the compound values. All value-carrying logic
 * (append/cap/idempotence/recurrence/narrative) lives in the pure [CoachJourneySerialization] object
 * and is unit-tested there; this class is thin I/O only. Every read/write is deterministic in "today"
 * via the injected [DateProvider] (never `LocalDate.now()`).
 *
 * This store does NOT re-persist data that already lives in Room (plan-version history, weekly-review
 * rows). Plan/phase facts needed for a narrative are passed in as parameters by the caller.
 *
 * Boundary rule (§5, guard-tested): imports nothing from the legacy `ai/local` stack.
 */
class CoachJourneyStore(
    private val dataStore: DataStore<Preferences>,
    private val dateProvider: DateProvider,
) : CoachJourney {

    /** Production constructor — binds to the app-scoped `coach_journey` DataStore. */
    constructor(context: Context, dateProvider: DateProvider) :
        this(context.coachJourneyDataStore, dateProvider)

    /**
     * Append a fired [signal] under [weekSignature], stamped with today's date. Trimmed to the newest
     * [CoachJourneySerialization.FIRED_HISTORY_CAP] entries.
     */
    override suspend fun recordFiredSignal(signal: CoachSignal, weekSignature: String) {
        val record = FiredSignalRecord(
            kind = signal.kind,
            dedupKey = signal.dedupKey,
            weekSignature = weekSignature,
            dateIso = dateProvider.today().toString(),
        )
        dataStore.edit { prefs ->
            val existing = CoachJourneySerialization.decodeFired(prefs[Keys.FiredHistory])
            prefs[Keys.FiredHistory] = CoachJourneySerialization.encodeFired(
                CoachJourneySerialization.appendFired(existing, record),
            )
        }
    }

    /** The fired-signal history, oldest first. */
    suspend fun firedHistory(): List<FiredSignalRecord> =
        CoachJourneySerialization.decodeFired(
            dataStore.data.first()[Keys.FiredHistory],
        )

    /**
     * Append a weekly [verdict] for [weekSignature] / [weekEndDateIso]. Idempotent per week signature
     * (recording the same week twice yields one entry) and trimmed to the newest
     * [CoachJourneySerialization.VERDICT_CAP] entries.
     */
    override suspend fun recordWeeklyVerdict(weekSignature: String, weekEndDateIso: String, verdict: String) {
        val record = WeeklyVerdictRecord(weekSignature, weekEndDateIso, verdict)
        dataStore.edit { prefs ->
            val existing = CoachJourneySerialization.decodeVerdicts(prefs[Keys.WeeklyVerdicts])
            prefs[Keys.WeeklyVerdicts] = CoachJourneySerialization.encodeVerdicts(
                CoachJourneySerialization.appendVerdict(existing, record),
            )
        }
    }

    /** The stored weekly verdicts, oldest first. */
    suspend fun weeklyVerdicts(): List<WeeklyVerdictRecord> =
        CoachJourneySerialization.decodeVerdicts(
            dataStore.data.first()[Keys.WeeklyVerdicts],
        )

    /**
     * True when [kind] fired, went quiet for ≥1 week, then fired again — the recurrence signal the
     * engine consumes. See [CoachJourneySerialization.hasRecurred].
     */
    suspend fun hasRecurred(kind: SignalKind): Boolean =
        CoachJourneySerialization.hasRecurred(firedHistory(), kind)

    /**
     * A compact, deterministic multi-week narrative for prompts, built ONLY from stored facts. Empty
     * string when there isn't enough history (fewer than [CoachJourneySerialization.NARRATIVE_MIN_VERDICTS]
     * weekly verdicts). Decoration text — safe to drop.
     */
    override suspend fun journeyNarrative(): String {
        val prefs = dataStore.data.first()
        val verdicts = CoachJourneySerialization.decodeVerdicts(prefs[Keys.WeeklyVerdicts])
        val history = CoachJourneySerialization.decodeFired(prefs[Keys.FiredHistory])
        return CoachJourneySerialization.narrative(verdicts, history)
    }

    private object Keys {
        val FiredHistory = stringPreferencesKey("fired_history")
        val WeeklyVerdicts = stringPreferencesKey("weekly_verdicts")
    }
}
