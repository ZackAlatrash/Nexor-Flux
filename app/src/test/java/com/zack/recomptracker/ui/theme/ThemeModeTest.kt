package com.zack.recomptracker.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `round-trips every mode through storage value`() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemeMode.fromStored(mode.storageValue))
        }
    }

    @Test
    fun `unknown or null storage value defaults to SYSTEM`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("garbage"))
    }
}
