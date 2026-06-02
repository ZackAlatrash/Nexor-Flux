package com.zack.recomptracker.ui.charts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.zack.recomptracker.ui.component.charts.ChartDefaults
import com.zack.recomptracker.ui.component.charts.dotScale
import com.zack.recomptracker.ui.component.charts.nearestPointIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartHelpersTest {

    @Test
    fun chartDefaultsGridAlphaIsCorrect() {
        assertEquals(0.04f, ChartDefaults.gridAlpha, 0.001f)
    }

    @Test
    fun chartDefaultsMacroColorsAreDefined() {
        val p = ChartDefaults.MacroColors.Protein
        val c = ChartDefaults.MacroColors.Carbs
        val f = ChartDefaults.MacroColors.Fat
        // violet, emerald, orange — all fully opaque
        // Colors are in ARGB format: 0xFFa78bfa means alpha=0xFF, rgb=0xa78bfa
        assertEquals(Color(0xFFa78bfa), p)
        assertEquals(Color(0xFF34d399), c)
        assertEquals(Color(0xFFfb923c), f)
    }

    @Test
    fun dotScaleIsZeroBeforeThreshold() {
        assertEquals(0f, dotScale(dotX = 100f, totalWidth = 200f, progress = 0.3f), 0.001f)
    }

    @Test
    fun dotScaleIsOneWhenProgressWellPastThreshold() {
        // threshold = 100/200 = 0.5, progress = 0.7 → local = (0.7-0.5)/0.08 = 2.5 → clamped to 1.0
        assertEquals(1f, dotScale(dotX = 100f, totalWidth = 200f, progress = 0.7f), 0.001f)
    }

    @Test
    fun dotScaleIsPartialMidTransition() {
        // threshold = 0.5, progress = 0.54 → local = 0.04/0.08 = 0.5
        assertEquals(0.5f, dotScale(dotX = 100f, totalWidth = 200f, progress = 0.54f), 0.001f)
    }

    @Test
    fun nearestPointIndexReturnsClosest() {
        val pts = listOf(Offset(10f, 0f), Offset(50f, 0f), Offset(90f, 0f))
        assertEquals(1, nearestPointIndex(52f, pts))
    }

    @Test
    fun nearestPointIndexReturnsZeroForEmptyList() {
        assertEquals(0, nearestPointIndex(50f, emptyList()))
    }
}
