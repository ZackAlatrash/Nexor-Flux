package com.zack.recomptracker.ui.streak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.StreakRepository
import com.zack.recomptracker.domain.activity.ActivityMetrics
import com.zack.recomptracker.domain.streak.Streaks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class StreakUiState(
    val streaks: Streaks = Streaks.EMPTY,
    val stepGoal: Int? = null,
    val activity: ActivityMetrics = ActivityMetrics(),
)

class StreakViewModel(
    streakRepository: StreakRepository,
    userProfileStore: UserProfilePreferencesStore,
    // Off-main dispatcher. The VM's own transform is trivial, but the upstream repository flows
    // (streaks()/activity()) are combine transforms doing groupBy + sumOf + LocalDate.parse per
    // emission; flowOn here pushes that upstream work off the main thread. Default keeps AppContainer
    // unchanged.
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val uiState: StateFlow<StreakUiState> =
        combine(
            streakRepository.streaks(),
            streakRepository.activity(),
            userProfileStore.preferences,
        ) { streaks, activity, profile ->
            StreakUiState(streaks = streaks, stepGoal = profile.dailyStepGoal, activity = activity)
        }
            .flowOn(computeDispatcher)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StreakUiState(),
            )
}
