package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CoachCoordinatorTypesTest {

    @Test
    fun `Role enum has User and Assistant`() {
        assertEquals(2, Role.values().size)
        assertEquals(Role.User, Role.valueOf("User"))
        assertEquals(Role.Assistant, Role.valueOf("Assistant"))
    }

    @Test
    fun `ChatMessage equality uses role and text`() {
        val a = ChatMessage(Role.User, "hello")
        val b = ChatMessage(Role.User, "hello")
        val c = ChatMessage(Role.Assistant, "hello")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `ChatMessage id is unique per instance and excluded from equality`() {
        val a = ChatMessage(Role.User, "hello")
        val b = ChatMessage(Role.User, "hello")
        // Distinct instances get distinct ids (usable as stable list keys)…
        assertNotEquals(a.id, b.id)
        // …but the id does not participate in equality/hashCode (still value-equal).
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `ChatMessage id is stable across reads`() {
        val m = ChatMessage(Role.Assistant, "hi")
        assertEquals(m.id, m.id)
    }

    @Test
    fun `CoachState Idle holds history`() {
        val history = listOf(ChatMessage(Role.User, "hi"), ChatMessage(Role.Assistant, "hello"))
        val state = CoachState.Idle(history)
        assertEquals(2, state.history.size)
    }

    @Test
    fun `CoachState Thinking holds process steps`() {
        val state1 = CoachState.Thinking(emptyList())
        val state2 = CoachState.Thinking(emptyList(), steps = listOf("Thinking…", "Reading food log…"))
        assertEquals(emptyList<String>(), state1.steps)
        assertEquals(listOf("Thinking…", "Reading food log…"), state2.steps)
    }

    @Test
    fun `CoachState Responding holds partial text`() {
        val state = CoachState.Responding(emptyList(), partial = "Your weight")
        assertEquals("Your weight", state.partial)
    }
}
