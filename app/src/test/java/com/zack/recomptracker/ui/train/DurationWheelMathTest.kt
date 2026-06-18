package com.zack.recomptracker.ui.train

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationWheelMathTest {

    @Test
    fun `durationToHm splits whole hours and minutes`() {
        assertEquals(1 to 15, durationToHm(75 * 60)) // 4500s
    }

    @Test
    fun `durationToHm drops sub-minute remainder`() {
        assertEquals(1 to 15, durationToHm(75 * 60 + 30)) // 4530s
    }

    @Test
    fun `durationToHm of zero is zero`() {
        assertEquals(0 to 0, durationToHm(0))
    }

    @Test
    fun `durationToHm clamps hours to twelve`() {
        assertEquals(12 to 0, durationToHm(13 * 3600))
    }

    @Test
    fun `durationToHm clamps negative to zero`() {
        assertEquals(0 to 0, durationToHm(-100))
    }

    @Test
    fun `hmToSeconds combines hours and minutes`() {
        assertEquals(4500, hmToSeconds(1, 15))
    }

    @Test
    fun `hmToSeconds clamps out-of-range inputs`() {
        assertEquals(MAX_DURATION_HOURS * 3600, hmToSeconds(20, 0))
        assertEquals(59 * 60, hmToSeconds(0, 90))
    }

    @Test
    fun `round trips through hm and back`() {
        for (seconds in listOf(0, 60, 4500, 12 * 3600)) {
            val (h, m) = durationToHm(seconds)
            assertEquals(seconds, hmToSeconds(h, m))
        }
    }
}
