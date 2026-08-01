package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderSupportTest {

    private data class Item(val id: Long, val name: String)

    @Test
    fun `moves item from earlier to later position`() {
        val list = mutableListOf(Item(1, "a"), Item(2, "b"), Item(3, "c"))
        val moved = list.moveByKey(fromKey = 1L, toKey = 3L) { it.id }
        assertTrue(moved)
        assertEquals(listOf(2L, 3L, 1L), list.map { it.id })
    }

    @Test
    fun `moves item from later to earlier position`() {
        val list = mutableListOf(Item(1, "a"), Item(2, "b"), Item(3, "c"))
        val moved = list.moveByKey(fromKey = 3L, toKey = 1L) { it.id }
        assertTrue(moved)
        assertEquals(listOf(3L, 1L, 2L), list.map { it.id })
    }

    @Test
    fun `returns false and leaves list unchanged when a key is missing`() {
        val list = mutableListOf(Item(1, "a"), Item(2, "b"))
        val moved = list.moveByKey(fromKey = 1L, toKey = 99L) { it.id }
        assertFalse(moved)
        assertEquals(listOf(1L, 2L), list.map { it.id })
    }
}
