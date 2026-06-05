package com.zack.recomptracker.ui.coach

import androidx.lifecycle.ViewModel
import com.zack.recomptracker.ai.CoachCoordinator
import com.zack.recomptracker.ai.CoachState
import kotlinx.coroutines.flow.StateFlow

class CoachViewModel(
    private val coachCoordinator: CoachCoordinator,
) : ViewModel() {

    val state: StateFlow<CoachState> = coachCoordinator.state

    fun sendMessage(text: String) = coachCoordinator.sendMessage(text)

    fun clearHistory() = coachCoordinator.clearHistory()
}
