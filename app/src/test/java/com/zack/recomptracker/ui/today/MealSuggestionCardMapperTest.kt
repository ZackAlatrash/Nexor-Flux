package com.zack.recomptracker.ui.today

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.domain.food.MealSuggester
import com.zack.recomptracker.domain.food.SuggestionFocus
import com.zack.recomptracker.domain.food.SuggestionFood
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealSuggestionCardMapperTest {
    private val target = MealSuggester.MacroTargets(2000, 150, 200, 60)
    private fun food(name: String, cal: Int, p: Double, c: Double, f: Double) =
        SuggestionFood(name, "serving", 100.0, cal, p, c, f)

    @Test fun `eatingDayFraction is 0 at start, ~0_5 at mid, 1 at end, clamped`() {
        assertEquals(0.0, MealSuggestionCardMapper.eatingDayFraction(LocalTime.of(8, 0)), 0.001)
        assertEquals(0.5, MealSuggestionCardMapper.eatingDayFraction(LocalTime.of(15, 0)), 0.001)
        assertEquals(1.0, MealSuggestionCardMapper.eatingDayFraction(LocalTime.of(22, 0)), 0.001)
        assertEquals(0.0, MealSuggestionCardMapper.eatingDayFraction(LocalTime.of(6, 0)), 0.001)
        assertEquals(1.0, MealSuggestionCardMapper.eatingDayFraction(LocalTime.of(23, 30)), 0.001)
    }

    @Test fun `null before the day-fraction gate even with a gap`() {
        val s = MealSuggestionCardMapper.build(
            target, MacroTotals(500, 20.0, 40.0, 10.0),
            listOf(food("Chicken", 165, 31.0, 0.0, 4.0)), fractionOfDayElapsed = 0.3,
        )
        assertNull(s)
    }

    @Test fun `null when on target after the gate`() {
        val s = MealSuggestionCardMapper.build(
            target, MacroTotals(2000, 150.0, 200.0, 60.0), emptyList(), fractionOfDayElapsed = 0.9,
        )
        assertNull(s)
    }

    @Test fun `builds card with focus, headline, capped suggestions past the gate`() {
        val s = MealSuggestionCardMapper.build(
            target, MacroTotals(600, 30.0, 60.0, 15.0),
            listOf(
                food("Chicken", 165, 31.0, 0.0, 4.0), food("Whey", 120, 24.0, 3.0, 2.0),
                food("Cod", 90, 20.0, 0.0, 1.0), food("Tofu", 145, 15.0, 3.0, 8.0),
            ),
            fractionOfDayElapsed = 0.6,
        )
        assertNotNull(s)
        assertEquals(SuggestionFocus.PROTEIN, s!!.focus)
        assertTrue(s.headline.isNotBlank())
        assertTrue(s.suggestions.size <= 3)
        assertTrue(s.coachSeed.contains("protein", ignoreCase = true))
    }

    @Test fun `libraryThin true when nothing contributes the focus macro`() {
        val s = MealSuggestionCardMapper.build(
            target, MacroTotals(600, 30.0, 60.0, 15.0),
            listOf(food("Lettuce", 10, 0.0, 2.0, 0.0)), fractionOfDayElapsed = 0.7,
        )
        assertNotNull(s)
        assertTrue(s!!.libraryThin)
        assertTrue(s.suggestions.isEmpty())
    }
}
