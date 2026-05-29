package com.zack.recomptracker.ui

import com.zack.recomptracker.ui.component.calorieFraction
import com.zack.recomptracker.ui.component.calorieScaleMax
import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieZoneBarKtTest {

    @Test
    fun scaleMaxIsUpperBoundTimes1Point2() {
        assertEquals(3120, calorieScaleMax(2600))
    }

    @Test
    fun fractionIsZeroWhenEatenIsZero() {
        assertEquals(0f, calorieFraction(0, 3120))
    }

    @Test
    fun fractionIsClampedToOneWhenEatenExceedsScaleMax() {
        assertEquals(1f, calorieFraction(5000, 3120))
    }

    @Test
    fun fractionIsCorrectMidRange() {
        assertEquals(0.5f, calorieFraction(1560, 3120))
    }

    @Test
    fun zoneFractionsAreWithinTrack() {
        val scaleMax = calorieScaleMax(2600)
        val lower = calorieFraction(2400, scaleMax)
        val upper = calorieFraction(2600, scaleMax)
        assert(lower in 0f..1f) { "lower=$lower" }
        assert(upper in 0f..1f) { "upper=$upper" }
        assert(lower < upper) { "lower must be < upper" }
    }
}
