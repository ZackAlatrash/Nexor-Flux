package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.preferences.AppPreferences
import com.zack.recomptracker.data.preferences.PlanPreferences
import kotlinx.coroutines.flow.Flow

class PlanRepository(
    private val appPreferences: AppPreferences,
) {
    val preferences: Flow<PlanPreferences> = appPreferences.preferences

    suspend fun save(preferences: PlanPreferences) {
        appPreferences.save(preferences)
    }

    suspend fun resetDefaults() {
        appPreferences.resetDefaults()
    }
}
