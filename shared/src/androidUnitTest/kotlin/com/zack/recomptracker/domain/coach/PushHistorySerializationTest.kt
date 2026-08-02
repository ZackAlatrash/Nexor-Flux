package com.zack.recomptracker.domain.coach

import java.time.LocalDateTime as JavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive pure coverage of the append/cap/prune/(de)serialization logic backing `PushHistoryStore`.
 * No DataStore I/O — the thin store wiring is covered separately in `:app`'s `PushHistoryStoreTest`.
 *
 * The codec speaks kotlinx-datetime; `java.time` survives here only as fixture arithmetic
 * (`minusDays`/`minusHours` have no direct kotlinx `LocalDateTime` equivalent), which is fine in a
 * JVM-only source set.
 */
class PushHistorySerializationTest {

    private val nowJava = JavaLocalDateTime.of(2026, 7, 1, 13, 0)
    private val now = nowJava.toKotlinLocalDateTime()

    private fun event(daysAgo: Long, isCelebration: Boolean = false) =
        PushEvent(timestamp = nowJava.minusDays(daysAgo).toKotlinLocalDateTime(), isCelebration = isCelebration)

    @Test
    fun `decode of a blank or null string is an empty list`() {
        assertEquals(emptyList<PushEvent>(), PushHistorySerialization.decode(null))
        assertEquals(emptyList<PushEvent>(), PushHistorySerialization.decode(""))
        assertEquals(emptyList<PushEvent>(), PushHistorySerialization.decode("   "))
    }

    @Test
    fun `decode of a malformed payload is an empty list (never throws)`() {
        assertEquals(emptyList<PushEvent>(), PushHistorySerialization.decode("{not valid json"))
    }

    @Test
    fun `encode then decode round-trips events`() {
        val events = listOf(event(daysAgo = 3), event(daysAgo = 1, isCelebration = true))
        val decoded = PushHistorySerialization.decode(PushHistorySerialization.encode(events))
        assertEquals(events, decoded)
    }

    @Test
    fun `append pruned adds the newest event and keeps everything in the retention window`() {
        val existing = listOf(event(daysAgo = 3))
        val updated = PushHistorySerialization.appendPruned(existing, event(daysAgo = 0), now)
        assertEquals(2, updated.size)
        assertTrue(updated.contains(event(daysAgo = 0)))
    }

    @Test
    fun `append pruned drops events older than the retention window`() {
        // An event well past the retention horizon should be dropped on the next append.
        val stale = event(daysAgo = PushHistorySerialization.RETENTION_DAYS + 1)
        val updated = PushHistorySerialization.appendPruned(listOf(stale), event(daysAgo = 0), now)
        assertEquals(listOf(event(daysAgo = 0)), updated)
    }

    @Test
    fun `append pruned keeps an event exactly on the retention boundary`() {
        val onBoundary = event(daysAgo = PushHistorySerialization.RETENTION_DAYS)
        val updated = PushHistorySerialization.appendPruned(listOf(onBoundary), event(daysAgo = 0), now)
        assertTrue(updated.contains(onBoundary))
        assertEquals(2, updated.size)
    }

    @Test
    fun `append pruned caps the list length so the payload can't grow unbounded`() {
        // Many recent (in-window) events, then one more → list is trimmed to MAX_EVENTS.
        val many = (1..PushHistorySerialization.MAX_EVENTS + 5).map {
            PushEvent(timestamp = nowJava.minusHours(it.toLong()).toKotlinLocalDateTime(), isCelebration = false)
        }
        val updated = PushHistorySerialization.appendPruned(many, event(daysAgo = 0), now)
        assertEquals(PushHistorySerialization.MAX_EVENTS, updated.size)
        // The newest event is retained after trimming.
        assertTrue(updated.contains(event(daysAgo = 0)))
    }

    @Test
    fun `prune alone drops stale events relative to reference`() {
        val events = listOf(event(daysAgo = 2), event(daysAgo = PushHistorySerialization.RETENTION_DAYS + 3))
        val pruned = PushHistorySerialization.prune(events, now)
        assertEquals(listOf(event(daysAgo = 2)), pruned)
    }
}
