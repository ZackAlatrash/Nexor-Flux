package com.zack.recomptracker.ui.charts

import com.zack.recomptracker.ui.component.charts.macroSweepAngles
import org.junit.Assert.assertEquals
import org.junit.Test

class MacroRingChartTest {

    @Test
    fun equalMacrosProduceEqualSweeps() {
        val (p, c, f) = macroSweepAngles(100f, 100f, 100f)
        // 360/3 = 120, minus 2dp gap each = 118
        assertEquals(118f, p, 0.5f)
        assertEquals(118f, c, 0.5f)
        assertEquals(118f, f, 0.5f)
    }

    @Test
    fun sweepsDoNotExceed360Total() {
        val (p, c, f) = macroSweepAngles(200f, 100f, 50f)
        assert(p + c + f <= 360f) { "total sweep=${p+c+f} exceeds 360" }
    }

    @Test
    fun zeroMacrosReturnZeroSweeps() {
        val (p, c, f) = macroSweepAngles(0f, 0f, 0f)
        assertEquals(0f, p, 0.001f)
        assertEquals(0f, c, 0.001f)
        assertEquals(0f, f, 0.001f)
    }

    @Test
    fun onlyProteinGetsMostSweep() {
        val (p, c, f) = macroSweepAngles(300f, 50f, 50f)
        assert(p > c) { "protein should have more sweep than carbs" }
        assert(p > f) { "protein should have more sweep than fat" }
    }
}
