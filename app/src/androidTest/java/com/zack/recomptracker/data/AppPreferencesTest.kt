package com.zack.recomptracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.data.preferences.AppPreferences
import com.zack.recomptracker.data.preferences.PlanPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AppPreferencesTest {
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        appPreferences = AppPreferences(context)
        // Start from a clean slate so previous runs don't leak state.
        runBlocking { appPreferences.resetDefaults() }
    }

    /**
     * Regression: the calorie-zone bounds drive the dashboard's calorie chart. They were being
     * dropped on save (and never read back), so the chart stayed pinned to the defaults no matter
     * what plan the user set.
     */
    @Test
    fun saveRoundTripsCalorieZoneBounds() = runBlocking {
        appPreferences.save(
            PlanPreferences(
                targetCalories = 3000,
                calorieZoneLowerBound = 2850,
                calorieZoneUpperBound = 3150,
            ),
        )

        val loaded = appPreferences.preferences.first()

        assertEquals(3000, loaded.targetCalories)
        assertEquals(2850, loaded.calorieZoneLowerBound)
        assertEquals(3150, loaded.calorieZoneUpperBound)
    }
}
