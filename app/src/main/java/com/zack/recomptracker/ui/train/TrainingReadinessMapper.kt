package com.zack.recomptracker.ui.train

/**
 * Pure, deterministic mapper for the Train-home "Training readiness" card. It answers the right
 * question for the user's current situation via a small decision tree (checked in priority order):
 *
 *  1. **Already trained today** → a recovery / tomorrow message, not a "should I train" verdict.
 *  2. **Deload** — recent recovery has been poor AND they've been training frequently → suggest a
 *     lighter week.
 *  3. **Rest day** — they've already hit their weekly session target → rest is productive.
 *  4. **Train** — today's recovery verdict (falling back to the recent average when today isn't
 *     logged yet, and softening a single bad day against a strong recent trend).
 *
 * No AI, no Android imports — reuses the recovery + session data the app already collects. It never
 * fires a push or an engine signal; it's a Train-open-moment prompt whose action seeds the coach.
 */
object TrainingReadinessMapper {

    /** Recovery quality — drives the card's accent colour. */
    enum class Level { LOW, MODERATE, GOOD }

    /** The situation the card is speaking to — determines its framing + action. */
    enum class Mode { TRAIN, REST_DONE, REST_DAY, DELOAD }

    /** Rendered state for the readiness card. `coachSeed` seeds the coach handoff on the action tap. */
    data class Readiness(
        val mode: Mode,
        val level: Level,
        val headline: String,
        val adaptHint: String,
        val loadNote: String?,
        /** True when today's recovery wasn't logged and we read from the recent average instead. */
        val usedBaseline: Boolean,
        /** Mode-appropriate action-button label (e.g. "Adapt session", "Plan recovery"). */
        val actionLabel: String,
        val coachSeed: String,
    )

    /** Today's logged recovery. Any field may be missing. Scores 1–10 (higher soreness = worse,
     *  higher energy = better); sleep in hours. */
    data class RecoveryToday(
        val sorenessScore: Int? = null,
        val sleepHours: Double? = null,
        val energyScore: Int? = null,
    ) {
        val hasAnySignal: Boolean
            get() = sorenessScore != null || sleepHours != null || energyScore != null
    }

    /** Recent recovery averages (last ~2 weeks, excluding today) — the trend used for the deload
     *  check, the fallback when today isn't logged, and softening a single bad day. */
    data class RecoveryBaseline(
        val avgSorenessScore: Int? = null,
        val avgSleepHours: Double? = null,
        val avgEnergyScore: Int? = null,
    ) {
        val hasAnySignal: Boolean
            get() = avgSorenessScore != null || avgSleepHours != null || avgEnergyScore != null
    }

    // Per-signal bands (whole-body). A "penalty" is a clear deficit; a "flag" is a milder one.
    private const val SORENESS_HIGH = 7
    private const val SORENESS_MODERATE = 5
    private const val SLEEP_LOW = 6.0
    private const val SLEEP_MODERATE = 7.0
    private const val ENERGY_LOW = 4
    private const val ENERGY_MODERATE = 6

    // Deload heuristic: recent recovery poor AND the user has been training frequently.
    private const val DELOAD_SORENESS = 6
    private const val DELOAD_ENERGY = 4
    private const val DELOAD_SLEEP = 6.0
    private const val DELOAD_MIN_SESSIONS = 3

    /**
     * @param recovery today's logged recovery signals (may be empty).
     * @param baseline recent recovery averages (may be null / empty).
     * @param daysSinceLastSession 0 = trained today; null = unknown / no history.
     * @param sessionsThisWeek completed sessions in the current week.
     * @param weeklyTarget the profile's weekly gym-session target (null / 0 = not set).
     * @return the card state, or null when there is genuinely nothing useful to say (hide gate).
     */
    fun build(
        recovery: RecoveryToday,
        baseline: RecoveryBaseline? = null,
        daysSinceLastSession: Int? = null,
        sessionsThisWeek: Int = 0,
        weeklyTarget: Int? = null,
    ): Readiness? {
        // 1. Already trained today → recovery + tomorrow framing (shown even without recovery logged).
        if (daysSinceLastSession == 0) return restDone(recovery, sessionsThisWeek, weeklyTarget)

        // 2. Sustained under-recovery + frequent training → suggest a lighter week.
        if (isDeload(baseline, sessionsThisWeek)) return deload(baseline!!, sessionsThisWeek)

        // 3. Weekly target already met → rest is productive.
        if (weeklyTarget != null && weeklyTarget > 0 && sessionsThisWeek >= weeklyTarget) {
            return restDay(sessionsThisWeek, weeklyTarget)
        }

        // 4. Train mode — today's recovery, falling back to the recent average when unlogged.
        val usedBaseline = !recovery.hasAnySignal
        val source: Signals = when {
            recovery.hasAnySignal ->
                Signals(recovery.sorenessScore, recovery.sleepHours, recovery.energyScore)
            baseline?.hasAnySignal == true ->
                Signals(baseline.avgSorenessScore, baseline.avgSleepHours, baseline.avgEnergyScore)
            else -> return null // nothing logged today or recently → hide
        }
        var level = levelOf(source)
        // Trend softening: a single bad day against a strong recent baseline reads MODERATE, not LOW.
        if (level == Level.LOW && recovery.hasAnySignal && baseline?.hasAnySignal == true &&
            baselineIsStrong(baseline)
        ) {
            level = Level.MODERATE
        }
        return train(level, recovery, daysSinceLastSession, usedBaseline)
    }

    // ── Modes ────────────────────────────────────────────────────────────────

    private fun restDone(recovery: RecoveryToday, sessions: Int, target: Int?): Readiness = Readiness(
        mode = Mode.REST_DONE,
        level = Level.GOOD,
        headline = "Trained today",
        adaptHint = "Focus on recovery — protein and a good night's sleep set up tomorrow's session.",
        loadNote = target?.takeIf { it > 0 }?.let { "That's $sessions of $it sessions this week" },
        usedBaseline = false,
        actionLabel = "Plan recovery",
        coachSeed = buildString {
            append("The user has already trained today and opened the Train screen. ")
            val signals = signalList(recovery)
            if (signals.isNotEmpty()) append("Today's recovery: $signals. ")
            append(
                "Help them recover well and set up tomorrow's session — practical guidance on " +
                    "post-workout nutrition, protein, and sleep in 2-3 sentences, respecting their " +
                    "memory (goals, injuries).",
            )
        },
    )

    private fun deload(baseline: RecoveryBaseline, sessions: Int): Readiness = Readiness(
        mode = Mode.DELOAD,
        level = Level.LOW,
        headline = "Under-recovered lately",
        adaptHint = "A lighter week would bank the fatigue and let you come back stronger.",
        loadNote = "$sessions sessions this week while recovery's been low",
        usedBaseline = false,
        actionLabel = "Plan a deload",
        coachSeed = buildString {
            append("The user opened the Train screen. Their recent recovery has been poor ")
            append("(${baselineList(baseline)}) while training frequently ($sessions sessions this week). ")
            append(
                "Advise whether to take a deload / lighter week and how to structure it in 2-3 " +
                    "sentences, respecting their memory (goals, injuries).",
            )
        },
    )

    private fun restDay(sessions: Int, target: Int): Readiness = Readiness(
        mode = Mode.REST_DAY,
        level = Level.GOOD,
        headline = "You've hit $target sessions this week",
        adaptHint = "Rest is productive — train only if you feel fresh.",
        loadNote = "$sessions of $target this week",
        usedBaseline = false,
        actionLabel = "Ask coach",
        coachSeed = "The user has already hit their weekly training target ($sessions of $target " +
            "sessions this week) and opened the Train screen. Advise whether to rest or squeeze in " +
            "another session based on their recovery, in 2-3 sentences, respecting their memory.",
    )

    private fun train(
        level: Level,
        recovery: RecoveryToday,
        daysSinceLastSession: Int?,
        usedBaseline: Boolean,
    ): Readiness {
        val headline = when (level) {
            Level.LOW -> "Recovery's low"
            Level.MODERATE -> "Recovery's a bit down"
            Level.GOOD -> "You're recovered"
        }
        val adaptHint = when (level) {
            Level.LOW -> "Keep it light or swap to mobility"
            Level.MODERATE -> "Train, but ease off the heaviest sets"
            Level.GOOD -> "Train as planned"
        }
        val loadNote = if (usedBaseline) {
            "Based on your recent average — log today for a sharper read"
        } else {
            daysSinceLastSession?.let { d ->
                when {
                    d == 1 -> "Last session was yesterday"
                    d >= 2 -> "It's been $d days since your last session"
                    else -> null
                }
            }
        }
        return Readiness(
            mode = Mode.TRAIN,
            level = level,
            headline = headline,
            adaptHint = adaptHint,
            loadNote = loadNote,
            usedBaseline = usedBaseline,
            actionLabel = "Adapt session",
            coachSeed = trainSeed(level, recovery, loadNote),
        )
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    private data class Signals(val soreness: Int?, val sleep: Double?, val energy: Int?)

    private fun levelOf(s: Signals): Level {
        var penalty = 0
        var flags = 0
        s.soreness?.let {
            when {
                it >= SORENESS_HIGH -> penalty++
                it >= SORENESS_MODERATE -> flags++
            }
        }
        s.sleep?.let {
            when {
                it < SLEEP_LOW -> penalty++
                it < SLEEP_MODERATE -> flags++
            }
        }
        s.energy?.let {
            when {
                it <= ENERGY_LOW -> penalty++
                it <= ENERGY_MODERATE -> flags++
            }
        }
        return when {
            penalty >= 2 -> Level.LOW
            penalty == 1 || flags >= 2 -> Level.MODERATE
            else -> Level.GOOD
        }
    }

    /** Every present baseline field sits in the "good" zone — i.e. the recent trend is solid. */
    private fun baselineIsStrong(b: RecoveryBaseline): Boolean =
        b.hasAnySignal &&
            (b.avgSorenessScore == null || b.avgSorenessScore < SORENESS_MODERATE) &&
            (b.avgSleepHours == null || b.avgSleepHours >= SLEEP_MODERATE) &&
            (b.avgEnergyScore == null || b.avgEnergyScore > ENERGY_MODERATE)

    private fun isDeload(b: RecoveryBaseline?, sessionsThisWeek: Int): Boolean {
        if (b == null || sessionsThisWeek < DELOAD_MIN_SESSIONS) return false
        val soreHigh = b.avgSorenessScore != null && b.avgSorenessScore >= DELOAD_SORENESS
        val energyLow = b.avgEnergyScore != null && b.avgEnergyScore <= DELOAD_ENERGY
        val sleepLow = b.avgSleepHours != null && b.avgSleepHours < DELOAD_SLEEP
        return soreHigh && (energyLow || sleepLow)
    }

    // ── Coach-seed helpers ───────────────────────────────────────────────────

    private fun trainSeed(level: Level, recovery: RecoveryToday, loadNote: String?): String {
        val signals = signalList(recovery)
        val verdict = when (level) {
            Level.LOW -> "Their recovery looks low"
            Level.MODERATE -> "Their recovery is a bit down"
            Level.GOOD -> "Their recovery looks good"
        }
        val loadClause = loadNote?.let { " $it." } ?: ""
        val recoveryClause = if (signals.isNotEmpty()) "Today's recovery: $signals. " else ""
        return "The user tapped 'Adapt session' on the Train screen before working out. " +
            "$recoveryClause$verdict.$loadClause " +
            "Help them adapt today's planned session to that recovery — suggest concrete adjustments " +
            "(intensity, volume, or a mobility swap) in 2-3 sentences, respecting their memory (goals, injuries)."
    }

    private fun signalList(recovery: RecoveryToday): String = buildList {
        recovery.sorenessScore?.let { add("whole-body soreness $it/10") }
        recovery.sleepHours?.let { add("${trimHours(it)}h sleep") }
        recovery.energyScore?.let { add("energy $it/10") }
    }.joinToString(", ")

    private fun baselineList(b: RecoveryBaseline): String = buildList {
        b.avgSorenessScore?.let { add("avg soreness $it/10") }
        b.avgSleepHours?.let { add("avg ${trimHours(it)}h sleep") }
        b.avgEnergyScore?.let { add("avg energy $it/10") }
    }.joinToString(", ")

    private fun trimHours(h: Double): String =
        if (h == h.toLong().toDouble()) h.toLong().toString() else "%.1f".format(h)
}
