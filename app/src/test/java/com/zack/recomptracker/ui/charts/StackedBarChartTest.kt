package com.zack.recomptracker.ui.charts

import com.zack.recomptracker.ui.component.charts.DayMacros
import com.zack.recomptracker.ui.component.charts.stackedYScale
import org.junit.Assert.assertEquals
import org.junit.Test

class StackedBarChartTest {

    private fun day(p: Float, c: Float, f: Float) = DayMacros("Mon", p, c, f)

    @Test
    fun yScaleIs115PercentOfMaxTotal() {
        val days = listOf(day(100f, 200f, 50f), day(50f, 100f, 30f))
        // max total = 100+200+50 = 350; scale = 350 * 1.15 = 402.5
        assertEquals(402.5f, stackedYScale(days), 0.5f)
    }

    @Test
    fun yScaleIsAtLeastOne() {
        assertEquals(1f, stackedYScale(emptyList()), 0.001f)
    }

    @Test
    fun yScaleHandlesSingleDay() {
        val days = listOf(day(200f, 300f, 100f))
        assertEquals(690f, stackedYScale(days), 0.5f)
    }
}
