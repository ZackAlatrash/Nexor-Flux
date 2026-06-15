package com.zack.recomptracker.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSparklineTest {

    @Test
    fun `empty window yields no points`() {
        assertTrue(calendarSparkline(listOf(null, null, null)).isEmpty())
    }

    @Test
    fun `contiguous values pass through unchanged`() {
        assertEquals(listOf(80f, 81f, 82f), calendarSparkline(listOf(80f, 81f, 82f)))
    }

    @Test
    fun `internal one-day gap is interpolated, keeping calendar spacing`() {
        // day0=80, day1 missing, day2=82  ->  three slots: 80, 81, 82
        assertEquals(listOf(80f, 81f, 82f), calendarSparkline(listOf(80f, null, 82f)))
    }

    @Test
    fun `internal multi-day gap spans proportional width`() {
        // a 3-day gap becomes 3 slots, not 1
        assertEquals(listOf(80f, 81f, 82f, 83f), calendarSparkline(listOf(80f, null, null, 83f)))
    }

    @Test
    fun `leading and trailing empty days are trimmed`() {
        assertEquals(listOf(80f, 81f, 82f), calendarSparkline(listOf(null, 80f, null, 82f, null)))
    }

    @Test
    fun `single logged day yields a single point`() {
        assertEquals(listOf(80f), calendarSparkline(listOf(null, 80f, null)))
    }

    @Test
    fun `interpolated points never exceed the real min or max`() {
        val series = calendarSparkline(listOf(78f, null, null, null, 84f))
        assertEquals(78f, series.min())
        assertEquals(84f, series.max())
    }
}
