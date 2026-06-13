package com.zack.recomptracker.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class VerdictPillTest {
    @Test fun goodTrend_isGoodStatus() = assertEquals(PillStatus.GOOD, pillStatus(trendIsGood = true, isNeutral = false))
    @Test fun badTrend_isOffTrack() = assertEquals(PillStatus.OFF_TRACK, pillStatus(trendIsGood = false, isNeutral = false))
    @Test fun flatTrend_isNeutral() = assertEquals(PillStatus.NEUTRAL, pillStatus(trendIsGood = true, isNeutral = true))
}
