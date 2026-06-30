package com.zack.recomptracker.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * The plan-preferences read/write surface that [com.zack.recomptracker.data.repository.PlanRepository]
 * depends on. Extracted from [AppPreferences] so the repository's save() choke-point can be
 * tested on pure JVM with an in-memory fake — no Android Context (and thus no Robolectric)
 * required. The production implementation is [AppPreferences] (DataStore-backed).
 */
interface PlanPreferencesSource {
    val preferences: Flow<PlanPreferences>
    suspend fun save(preferences: PlanPreferences)
    suspend fun resetDefaults()
}
