package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachHandoffStoreTest {

    @Test
    fun `consume returns then clears the context`() {
        val store = CoachHandoffStore()
        store.set("BRIEFING CONTEXT")
        assertEquals("BRIEFING CONTEXT", store.consume())
        assertNull(store.consume())
    }

    @Test
    fun `consume is null when nothing set`() {
        assertNull(CoachHandoffStore().consume())
    }
}
