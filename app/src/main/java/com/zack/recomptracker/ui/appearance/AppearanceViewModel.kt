package com.zack.recomptracker.ui.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.ui.theme.AccentTheme
import com.zack.recomptracker.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Appearance screen: exposes the selected font and accent theme and persists
 * edits back through [UiPreferences]. Both values are written to the same global prefs the
 * app theme observes, so changes take effect app-wide immediately.
 */
class AppearanceViewModel(
    private val uiPreferences: UiPreferences,
) : ViewModel() {

    val font: StateFlow<String> =
        uiPreferences.selectedFont.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "default",
        )

    val accent: StateFlow<AccentTheme> =
        uiPreferences.accentTheme.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AccentTheme.VIOLET,
        )

    val themeMode: StateFlow<ThemeMode> =
        uiPreferences.themeMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )

    fun setFont(value: String) {
        viewModelScope.launch { uiPreferences.setFont(value) }
    }

    fun setAccent(theme: AccentTheme) {
        viewModelScope.launch { uiPreferences.setAccentTheme(theme) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { uiPreferences.setThemeMode(mode) }
    }
}
