package com.zack.recomptracker.core.time

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

interface DateProvider {
    fun today(): LocalDate

    /**
     * The current local date as a stream: emits immediately, then a fresh value each time the
     * calendar day rolls over, so long-lived collectors (tab ViewModels that outlive midnight)
     * advance instead of freezing on the day they were created.
     *
     * The default is a single emission of [today] — correct for a provider that cannot observe
     * time passing (e.g. a fixed clock in tests). [SystemDateProvider] overrides it with a real
     * midnight ticker.
     */
    fun todayFlow(): Flow<LocalDate> = flowOf(today())
}

class SystemDateProvider(
    // Injectable so tests can drive the wall clock; the default matches the previous LocalDate.now().
    private val clock: Clock = Clock.systemDefaultZone(),
) : DateProvider {
    override fun today(): LocalDate = LocalDate.now(clock)

    /**
     * Emits the current local date, then sleeps until just past the next local midnight and emits
     * the new date — repeating forever. Each iteration re-reads the live clock, so DST shifts (via
     * [ZonedDateTime.atStartOfDay]) resolve correctly. [distinctUntilChanged] guards against a wake
     * that lands a hair early re-emitting the same day.
     */
    override fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            val now = ZonedDateTime.now(clock)
            emit(now.toLocalDate())
            delay(millisUntilNextDay(now) + WAKE_BUFFER_MS)
        }
    }.distinctUntilChanged()

    private companion object {
        // Wake a beat after midnight so the clock has definitely rolled before we re-read it.
        const val WAKE_BUFFER_MS = 1_000L
    }
}

/** Milliseconds from [now] until the next local midnight (start of the following day in [now]'s zone). */
internal fun millisUntilNextDay(now: ZonedDateTime): Long {
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    return Duration.between(now, nextMidnight).toMillis()
}
