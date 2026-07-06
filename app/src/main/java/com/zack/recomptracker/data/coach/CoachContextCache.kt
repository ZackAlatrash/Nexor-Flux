package com.zack.recomptracker.data.coach

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.domain.coach.CoachContext
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A small memo over [CoachContextBuilder] (docs/ai-redesign/08-technical-architecture.md §7):
 * caches the built [CoachContext] and rebuilds only when the day rolls over (the snapshot's `asOf`
 * day changes) or a caller forces it. No time-of-day TTL — the daily-readiness signals recompute
 * once per day on open, which the day-key already captures.
 *
 * The mutex makes concurrent [get] calls share a single build rather than racing the builder.
 *
 * Takes the build as a suspend lambda (defaulting to [CoachContextBuilder.build]) so the memo logic
 * is unit-testable without wiring the full repository graph.
 */
class CoachContextCache(
    private val dateProvider: DateProvider,
    private val build: suspend () -> CoachContext,
) {
    constructor(builder: CoachContextBuilder, dateProvider: DateProvider) :
        this(dateProvider, builder::build)

    private val mutex = Mutex()
    private var cachedDay: LocalDate? = null
    private var cached: CoachContext? = null

    /**
     * Returns the cached snapshot, rebuilding when the day changed since the last build or when
     * [forceRefresh] is set. Thread-safe.
     */
    suspend fun get(forceRefresh: Boolean = false): CoachContext = mutex.withLock {
        val today = dateProvider.today()
        val current = cached
        if (!forceRefresh && current != null && cachedDay == today) {
            return current
        }
        val fresh = build()
        cached = fresh
        cachedDay = today
        fresh
    }

    /** Invalidate the memo so the next [get] rebuilds (e.g. after a material data change). */
    suspend fun refresh(): CoachContext = get(forceRefresh = true)

    /** Drop any cached snapshot without rebuilding. */
    suspend fun invalidate() = mutex.withLock {
        cached = null
        cachedDay = null
    }
}
