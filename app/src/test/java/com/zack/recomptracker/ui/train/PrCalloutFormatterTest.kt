package com.zack.recomptracker.ui.train

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrCalloutFormatterTest {

    @Test fun `headline includes exercise name and record when top set present`() {
        val c = PrCalloutFormatter.format("Bench Press", "80×5")
        assertEquals("New Bench Press record — 80×5", c.headline)
    }

    @Test fun `headline omits record when top set is blank`() {
        val c = PrCalloutFormatter.format("Bench Press", "")
        assertEquals("New Bench Press record", c.headline)
        assertFalse(c.headline.contains("—"))
    }

    @Test fun `forward line is forward-looking, not just a trophy`() {
        val c = PrCalloutFormatter.format("Squat", "120×3")
        assertTrue(c.forwardLine.contains("next week's target"))
    }
}
