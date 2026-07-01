package com.zack.recomptracker.domain.coach

import java.time.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * kotlinx.serialization adapter for [LocalDateTime] as an ISO-8601 string (e.g. `2026-07-01T13:00`).
 * Pure Kotlin; used by [PushEvent] so the push-history ledger persists as plain JSON. Uses the
 * canonical `LocalDateTime.toString()` / `LocalDateTime.parse()` round-trip.
 */
object LocalDateTimeIsoSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDateTime =
        LocalDateTime.parse(decoder.decodeString())
}
