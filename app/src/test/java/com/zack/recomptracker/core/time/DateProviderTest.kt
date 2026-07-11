package com.zack.recomptracker.core.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A [Clock] whose instant can be moved forward by the test to simulate the wall clock advancing. */
private class MutableInstantClock(var current: Instant, private val zone: ZoneId) : Clock() {
    override fun instant(): Instant = current
    override fun getZone(): ZoneId = zone
    override fun withZone(z: ZoneId): Clock = MutableInstantClock(current, z)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DateProviderTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `millisUntilNextDay is one hour when an hour before midnight`() {
        val now = LocalDate.of(2026, 7, 11).atTime(23, 0).atZone(utc)
        assertEquals(60 * 60 * 1000L, millisUntilNextDay(now))
    }

    @Test
    fun `millisUntilNextDay is nearly a full day just after midnight`() {
        val now = LocalDate.of(2026, 7, 11).atTime(0, 0, 1).atZone(utc)
        assertEquals((24L * 3600 - 1) * 1000L, millisUntilNextDay(now))
    }

    @Test
    fun `today reads the injected clock`() {
        val clock = MutableInstantClock(
            LocalDate.of(2026, 7, 11).atTime(12, 0).atZone(utc).toInstant(),
            utc,
        )
        assertEquals(LocalDate.of(2026, 7, 11), SystemDateProvider(clock).today())
    }

    @Test
    fun `todayFlow emits current date, then advances when the clock crosses midnight`() = runTest {
        val clock = MutableInstantClock(
            LocalDate.of(2026, 7, 11).atTime(23, 0).atZone(utc).toInstant(),
            utc,
        )
        val provider = SystemDateProvider(clock)
        val seen = mutableListOf<LocalDate>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            provider.todayFlow().collect { seen.add(it) }
        }

        // Immediate first emission is the current day.
        assertEquals(listOf(LocalDate.of(2026, 7, 11)), seen)

        // Move the wall clock past midnight, then let the scheduled midnight wake fire.
        clock.current = LocalDate.of(2026, 7, 12).atTime(0, 0, 5).atZone(utc).toInstant()
        testScheduler.advanceTimeBy(2 * 60 * 60 * 1000L) // 2h > the ~1h until midnight
        testScheduler.runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 12)), seen)
        job.cancel()
    }

    @Test
    fun `default interface todayFlow emits today once`() = runTest {
        val fixed = LocalDate.of(2026, 1, 1)
        val provider = object : DateProvider {
            override fun today() = fixed
        }
        val seen = mutableListOf<LocalDate>()
        provider.todayFlow().collect { seen.add(it) }
        assertEquals(listOf(fixed), seen)
    }
}
