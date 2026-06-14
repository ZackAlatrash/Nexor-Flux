package com.zack.recomptracker.ui.theme

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentThemeBackgroundTest {

    @Test
    fun `every theme has a non-zero drawable for both modes`() {
        for (theme in AccentTheme.entries) {
            assertTrue("dark res for $theme", theme.backgroundRes(darkMode = true) != 0)
            assertTrue("light res for $theme", theme.backgroundRes(darkMode = false) != 0)
        }
    }

    @Test
    fun `dark and light resources differ per theme`() {
        for (theme in AccentTheme.entries) {
            assertNotEquals(
                "dark/light should differ for $theme",
                theme.backgroundRes(darkMode = true),
                theme.backgroundRes(darkMode = false),
            )
        }
    }
}
