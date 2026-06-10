package com.zack.recomptracker.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class IridescentPaletteTest {

    private fun assertRgb(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected[0], actual[0], 0.01f)
        assertEquals(expected[1], actual[1], 0.01f)
        assertEquals(expected[2], actual[2], 0.01f)
    }

    @Test
    fun `red rotated 120 degrees is green`() {
        assertRgb(floatArrayOf(0f, 1f, 0f), hueRotatedRgb(1f, 0f, 0f, 120f))
    }

    @Test
    fun `red rotated 240 degrees is blue`() {
        assertRgb(floatArrayOf(0f, 0f, 1f), hueRotatedRgb(1f, 0f, 0f, 240f))
    }

    @Test
    fun `red rotated 360 degrees is identity`() {
        assertRgb(floatArrayOf(1f, 0f, 0f), hueRotatedRgb(1f, 0f, 0f, 360f))
    }

    @Test
    fun `gray is unchanged by rotation`() {
        assertRgb(floatArrayOf(0.5f, 0.5f, 0.5f), hueRotatedRgb(0.5f, 0.5f, 0.5f, 90f))
    }

    @Test
    fun `rotation wraps past 360`() {
        assertRgb(hueRotatedRgb(1f, 0f, 0f, 120f), hueRotatedRgb(1f, 0f, 0f, 480f))
    }
}
