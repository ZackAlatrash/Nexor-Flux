package com.zack.recomptracker.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure (de)serialization for the single active [CoachExperiment]: round-trip + failure tolerance. */
class CoachExperimentSerializationTest {

    private val experiment = CoachExperiment(
        correlationId = "SLEEP_NEXT_DAY_ENERGY",
        hypothesis = "Protecting sleep may lift your energy.",
        trackedMetric = "energy",
        baselineValue = 6.2,
        startDateIso = "2026-07-01",
    )

    @Test
    fun `encode then decode round-trips`() {
        val decoded = CoachExperimentSerialization.decode(CoachExperimentSerialization.encode(experiment))
        assertEquals(experiment, decoded)
    }

    @Test
    fun `decode of blank or malformed returns null`() {
        assertNull(CoachExperimentSerialization.decode(null))
        assertNull(CoachExperimentSerialization.decode(""))
        assertNull(CoachExperimentSerialization.decode("{not json"))
        assertNull(CoachExperimentSerialization.decode("[]"))
    }
}
