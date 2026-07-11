package com.zack.recomptracker.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P1-13: the rebalance day-progress dots were off by one — `i < dayX` marked TODAY as completed and
 * `i == dayX` put the ring on TOMORROW (and no ring at all on the final day). [dotStateFor] converts
 * the 1-based dayX to the 0-based dot index correctly.
 */
class RebalanceVizDotStateTest {

    @Test
    fun `on day 1 of 4 the first dot is today and nothing is completed`() {
        assertEquals(DayDotState.TODAY, dotStateFor(0, dayX = 1))
        assertEquals(DayDotState.UPCOMING, dotStateFor(1, dayX = 1))
        assertEquals(DayDotState.UPCOMING, dotStateFor(2, dayX = 1))
        assertEquals(DayDotState.UPCOMING, dotStateFor(3, dayX = 1))
    }

    @Test
    fun `on day 2 of 4 the first dot is completed and the second is today`() {
        assertEquals(DayDotState.COMPLETED, dotStateFor(0, dayX = 2))
        assertEquals(DayDotState.TODAY, dotStateFor(1, dayX = 2))
        assertEquals(DayDotState.UPCOMING, dotStateFor(2, dayX = 2))
        assertEquals(DayDotState.UPCOMING, dotStateFor(3, dayX = 2))
    }

    @Test
    fun `on the final day the last dot gets the ring, not nothing`() {
        assertEquals(DayDotState.COMPLETED, dotStateFor(0, dayX = 4))
        assertEquals(DayDotState.COMPLETED, dotStateFor(1, dayX = 4))
        assertEquals(DayDotState.COMPLETED, dotStateFor(2, dayX = 4))
        assertEquals(DayDotState.TODAY, dotStateFor(3, dayX = 4))
    }
}
