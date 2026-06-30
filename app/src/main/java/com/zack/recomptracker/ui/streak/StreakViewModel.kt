package com.zack.recomptracker.ui.streak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.StreakRepository
import com.zack.recomptracker.domain.activity.ActivityMetrics
import com.zack.recomptracker.domain.streak.Streaks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StreakUiState(
    val streaks: Streaks = Streaks.EMPTY,
    val stepGoal: Int? = null,
    val activity: ActivityMetrics = ActivityMetrics(),
)

class StreakViewModel(
    streakRepository: StreakRepository,
    userProfileStore: UserProfilePreferencesStore,
) : ViewModel() {
    val uiState: StateFlow<StreakUiState> =
        combine(
            streakRepository.streaks(),
            streakRepository.activity(),
            userProfileStore.preferences,
        ) { streaks, activity, profile ->
            StreakUiState(streaks = streaks, stepGoal = profile.dailyStepGoal, activity = activity)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StreakUiState(),
        )
}
