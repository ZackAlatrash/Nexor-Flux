package com.zack.recomptracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.LogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Profile screen: exposes the persisted user profile and the latest logged body
 * weight (read-only), and persists edits back through [UserProfilePreferencesStore].
 */
class ProfileViewModel(
    private val userProfileStore: UserProfilePreferencesStore,
    private val logRepository: LogRepository,
) : ViewModel() {

    val profile: StateFlow<UserProfilePreferences> =
        userProfileStore.preferences.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserProfilePreferences(),
        )

    /** Most recent body weight from the daily logs (kg), or null if none has been logged. */
    val currentWeightKg: StateFlow<Double?> =
        logRepository.observeDailyLogs()
            .map { logs ->
                logs.filter { it.bodyWeightKg != null }
                    .maxByOrNull { it.date }
                    ?.bodyWeightKg
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    fun update(p: UserProfilePreferences) {
        viewModelScope.launch { userProfileStore.save(p) }
    }
}
