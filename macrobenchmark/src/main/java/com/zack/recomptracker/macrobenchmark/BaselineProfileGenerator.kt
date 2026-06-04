package com.zack.recomptracker.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.zack.recomptracker"

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        // Cold start
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Navigate the main tabs to profile composable inflation
        navigateTabs()
    }

    private fun MacrobenchmarkScope.navigateTabs() {
        // Tab bar is at the bottom; tap each tab by UiSelector text label.
        val tabLabels = listOf("Body", "Log", "Progress", "More", "Home")
        for (label in tabLabels) {
            val tab = device.findObject(
                androidx.test.uiautomator.UiSelector().text(label)
            )
            if (tab.exists()) {
                tab.click()
                device.waitForIdle()
            }
        }
    }
}
