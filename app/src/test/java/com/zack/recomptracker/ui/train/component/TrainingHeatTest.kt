package com.zack.recomptracker.ui.train.component

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [heatColor] band boundaries: dim→bright green through the productive range, a green plateau across
 * the healthy band, then green→amber→red into overtraining. Channels are compared with a small delta
 * because Compose's `lerp` interpolates in Oklab (endpoints round-trip through the colour space).
 */
class TrainingHeatTest {

    private val dim = Color(0xFF2F6B4E)   // barely trained
    private val good = Color(0xFF43D07A)  // optimal
    private val amber = Color(0xFFE0A73E) // creeping past optimal
    private val red = Color(0xFFE0574B)   // overtrained

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.02f)
        assertEquals(expected.green, actual.green, 0.02f)
        assertEquals(expected.blue, actual.blue, 0.02f)
    }

    @Test fun `zero sets is dim green`() = assertColor(dim, heatColor(0f))

    @Test fun `negative input clamps to dim green`() = assertColor(dim, heatColor(-5f))

    @Test fun `reaches full green by the low anchor`() = assertColor(good, heatColor(10f))

    @Test fun `healthy band stays bright green`() {
        assertColor(good, heatColor(14f))
        assertColor(good, heatColor(18f))
    }

    @Test fun `midpoint of the overtrain band is amber`() = assertColor(amber, heatColor(22f))

    @Test fun `full red at and past the overtrain anchor`() {
        assertColor(red, heatColor(26f))
        assertColor(red, heatColor(40f))
    }
}
