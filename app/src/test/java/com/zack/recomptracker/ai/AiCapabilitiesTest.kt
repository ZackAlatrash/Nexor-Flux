package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCapabilitiesTest {

    @Test
    fun `local backend has minimal capabilities`() {
        val caps = AiCapabilities.of(AiBackend.LOCAL)
        assertFalse(caps.richInsights)
        assertFalse(caps.longContext)
        assertFalse(caps.unboundedToolLoop)
        assertFalse(caps.proactiveReview)
    }

    @Test
    fun `cloud backend unlocks tier-1 capabilities but not tier-2`() {
        val caps = AiCapabilities.of(AiBackend.CLOUD)
        assertTrue(caps.richInsights)
        assertTrue(caps.longContext)
        assertTrue(caps.unboundedToolLoop)
        assertFalse(caps.proactiveReview) // Tier 2 — deferred
    }

    @Test
    fun `backend parses from stored name with local fallback`() {
        assertEquals(AiBackend.CLOUD, AiBackend.fromStored("CLOUD"))
        assertEquals(AiBackend.LOCAL, AiBackend.fromStored("LOCAL"))
        assertEquals(AiBackend.LOCAL, AiBackend.fromStored(null))
        assertEquals(AiBackend.LOCAL, AiBackend.fromStored("garbage"))
    }
}
