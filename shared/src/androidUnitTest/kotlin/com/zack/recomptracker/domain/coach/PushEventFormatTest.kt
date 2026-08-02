package com.zack.recomptracker.domain.coach

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the persisted shape of [PushEvent.timestamp].
 *
 * `PushEvent` is stored as JSON in the `coach_push_history` DataStore. The custom
 * `LocalDateTimeIsoSerializer` (which round-tripped `java.time.LocalDateTime.toString()` /
 * `parse()`) was deleted when `domain/coach` moved to `:shared`; kotlinx-datetime's built-in
 * `LocalDateTime` serializer took over. If that changed the emitted string, existing users'
 * push history would silently fail to decode and the tolerant decoder would return an empty
 * list — resetting rate limiting. These assertions prove the shape is byte-identical to what
 * `java.time.LocalDateTime.toString()` produced (seconds omitted when zero).
 */
class PushEventFormatTest {

    @Test
    fun timestampSerializesAsIsoLocalDateTime() {
        val json = Json.encodeToString(LocalDateTime.serializer(), LocalDateTime(2026, 8, 1, 22, 0))
        assertEquals("\"2026-08-01T22:00\"", json)
    }

    @Test
    fun timestampWithSecondsRoundTrips() {
        val original = LocalDateTime(2026, 8, 1, 22, 0, 30)
        val encoded = Json.encodeToString(LocalDateTime.serializer(), original)
        assertEquals("\"2026-08-01T22:00:30\"", encoded)
        assertEquals(original, Json.decodeFromString(LocalDateTime.serializer(), encoded))
    }

    /** The whole [PushEvent] envelope, as the push-history store actually writes it. */
    @Test
    fun pushEventEnvelopeMatchesTheLegacyShape() {
        val event = PushEvent(LocalDateTime(2026, 7, 1, 13, 0), isCelebration = true)
        val encoded = Json.encodeToString(PushEvent.serializer(), event)
        assertEquals("""{"timestamp":"2026-07-01T13:00","isCelebration":true}""", encoded)
        assertEquals(event, Json.decodeFromString(PushEvent.serializer(), encoded))
    }

    /** A payload written by the old `java.time`-backed serializer must still decode. */
    @Test
    fun legacyPersistedPayloadStillDecodes() {
        val legacy = """[{"timestamp":"2026-07-01T13:00","isCelebration":false}]"""
        val decoded = Json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(PushEvent.serializer()),
            legacy,
        )
        assertEquals(1, decoded.size)
        assertEquals(LocalDateTime(2026, 7, 1, 13, 0), decoded.first().timestamp)
    }
}
