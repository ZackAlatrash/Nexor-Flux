package com.zack.recomptracker.ui.charts

import androidx.compose.ui.graphics.Color
import com.zack.recomptracker.ui.component.charts.ChartDefaults
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
}
