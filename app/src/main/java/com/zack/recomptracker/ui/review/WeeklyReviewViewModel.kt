package com.zack.recomptracker.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.WeeklyBriefing
import com.zack.recomptracker.domain.review.WeeklyReviewData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WeeklyReviewUiState {
    object Hidden : WeeklyReviewUiState
    object Upsell : WeeklyReviewUiState
    data class InsufficientData(val daysRemaining: Int) : WeeklyReviewUiState
    object Generating : WeeklyReviewUiState
    data class Ready(val briefing: WeeklyBriefing) : WeeklyReviewUiState
    data class Error(val message: String) : WeeklyReviewUiState
}

/**
 * All collaborators the ViewModel needs, as plain flows/lambdas so it is unit-testable without Room
 * or Android. Production values are assembled in AppContainer.
 */
class WeeklyReviewConfig(
    val cloudActiveFlow: Flow<Boolean>,
    val reviewDataFlow: Flow<WeeklyReviewData?>,
    val signatureOf: (WeeklyReviewData) -> String,
    val briefingFor: suspend (weekStart: String, signature: String, generate: suspend () -> WeeklyBriefing) -> WeeklyBriefing,
    val generate: suspend (WeeklyReviewData) -> WeeklyBriefing,
    val saveCalorieTarget: suspend (Int) -> Unit,
    val markSeen: suspend (String) -> Unit,
    val lastSeenSignatureFlow: Flow<String>,
    val startCoachHandoff: (WeeklyReviewData, WeeklyBriefing) -> Unit,
)

class WeeklyReviewViewModel(
    private val config: WeeklyReviewConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WeeklyReviewUiState>(WeeklyReviewUiState.Hidden)
    val uiState: StateFlow<WeeklyReviewUiState> = _uiState.asStateFlow()

    private val _pendingApply = MutableStateFlow<Int?>(null)
    val pendingApply: StateFlow<Int?> = _pendingApply.asStateFlow()

    @Volatile private var latestData: WeeklyReviewData? = null
    @Volatile private var latestBriefing: WeeklyBriefing? = null

    /** True when the current week's signature differs from the last one the user opened. */
    val badge: StateFlow<Boolean> =
        combine(config.reviewDataFlow, config.lastSeenSignatureFlow, config.cloudActiveFlow) { data, lastSeen, cloud ->
            data != null && cloud && config.signatureOf(data) != lastSeen
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            config.reviewDataFlow.collect { latestData = it }
        }
    }

    fun open() {
        viewModelScope.launch {
            val cloud = firstCloudActive()
            if (!cloud) { _uiState.value = WeeklyReviewUiState.Upsell; return@launch }
            val data = latestData
            if (data == null || data.daysLogged < 7) {
                val remaining = ((7 - (data?.daysLogged ?: 0)).coerceAtLeast(1))
                _uiState.value = WeeklyReviewUiState.InsufficientData(remaining)
                return@launch
            }
            _uiState.value = WeeklyReviewUiState.Generating
            try {
                val signature = config.signatureOf(data)
                val briefing = config.briefingFor(data.weekStart, signature) { config.generate(data) }
                latestBriefing = briefing
                config.markSeen(signature)
                _uiState.value = WeeklyReviewUiState.Ready(briefing)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = WeeklyReviewUiState.Error("Couldn't build your review — try again.")
            }
        }
    }

    fun regenerate() = open()

    fun dismiss() { _uiState.value = WeeklyReviewUiState.Hidden }

    fun requestApply(target: Int) { _pendingApply.value = target }
    fun cancelApply() { _pendingApply.value = null }

    fun confirmApply() {
        val target = _pendingApply.value ?: return
        viewModelScope.launch {
            config.saveCalorieTarget(target)
            _pendingApply.value = null
        }
    }

    fun discussWithCoach() {
        val data = latestData ?: return
        val briefing = latestBriefing ?: return
        config.startCoachHandoff(data, briefing)
    }

    private suspend fun firstCloudActive(): Boolean = config.cloudActiveFlow.first()
}
