package com.zack.recomptracker.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    // Free-text fields are seeded ONCE from the persisted profile (see init). They must NOT be
    // re-collected from DataStore — echoing the async-persisted value back into the TextField resets
    // the cursor and scrambles fast input (e.g. typing "zack" → "ackz"). Edits update these flows
    // synchronously (see setName/setHeight) and persist async.
    private val _nameInput = MutableStateFlow("")
    val nameInput: StateFlow<String> = _nameInput.asStateFlow()

    private val _heightInput = MutableStateFlow("")
    val heightInput: StateFlow<String> = _heightInput.asStateFlow()

    init {
        // One-shot seed from the first persisted emission so already-stored values show on open.
        viewModelScope.launch {
            val seed = userProfileStore.preferences.first()
            _nameInput.value = seed.name.orEmpty()
            _heightInput.value = seed.heightCm?.toString() ?: ""
        }
    }

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

    /** Name edit: update the buffer synchronously so the field reflects the keystroke immediately,
     * then persist async. Does not re-seed [nameInput] from later profile emissions. */
    fun setName(value: String) {
        _nameInput.value = value
        update(profile.value.copy(name = value.ifBlank { null }))
    }

    /** Height edit: keep digit-only, max 3 chars; the buffer stores the filtered digits so the field
     * shows exactly what is persisted. Synchronous buffer update, async persist. */
    fun setHeight(value: String) {
        val digits = value.filter { it.isDigit() }.take(3)
        _heightInput.value = digits
        update(profile.value.copy(heightCm = digits.toIntOrNull()))
    }
}
