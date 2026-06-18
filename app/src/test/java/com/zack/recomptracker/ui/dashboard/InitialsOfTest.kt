package com.zack.recomptracker.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InitialsOfTest {

    @Test
    fun `two-word name uses first and last initials`() {
        assertEquals("ZA", initialsOf("Zack Atrash"))
    }

    @Test
    fun `three-word name uses first and last only`() {
        assertEquals("JD", initialsOf("John Michael Doe"))
    }

    @Test
    fun `single word uses one initial`() {
        assertEquals("Z", initialsOf("Zack"))
    }

    @Test
    fun `lowercases are uppercased and extra spaces ignored`() {
        assertEquals("ZA", initialsOf("  zack   atrash "))
    }

    @Test
    fun `null or blank returns null`() {
        assertNull(initialsOf(null))
        assertNull(initialsOf(""))
        assertNull(initialsOf("   "))
    }
}
