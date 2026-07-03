package com.zack.recomptracker.ui.aicoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.data.preferences.SecureKeyStore
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiCoachUiState(
    val aiInsightsEnabled: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val cloudBaseUrl: String = "",
    val cloudModelId: String = "",
    val cloudHasKey: Boolean = false,
    val cloudHasWebSearchKey: Boolean = false,
    val testConnectionResult: String? = null,
    val testingConnection: Boolean = false,
    // ── Phase-5 notification prefs (quiet-by-default opt-out surface) ──────────
    val weeklyCheckInPushEnabled: Boolean = true,
    val ambientNudgesEnabled: Boolean = false,
    /** The configured quiet window, pre-formatted for display (e.g. "10 PM – 7 AM"). */
    val quietHoursDisplay: String = "",
)

class AiCoachViewModel(
    private val uiPreferences: UiPreferences,
    private val aiInsightCoordinator: AiInsightCoordinator,
    private val secureKeyStore: SecureKeyStore,
    private val openAiCompatClient: OpenAiCompatClient,
    private val coachDigestCoordinator: com.zack.recomptracker.data.coach.CoachDigestCoordinator,
    private val coachNotificationPreferences: com.zack.recomptracker.data.coach.CoachNotifierPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiCoachUiState())
    val uiState: StateFlow<AiCoachUiState> = _uiState.asStateFlow()

    val aiInsightState: StateFlow<AiInsightState> = aiInsightCoordinator.state

    init {
        // Reactive AI-enabled toggle (not free-text, so no typing race).
        viewModelScope.launch {
            uiPreferences.aiInsightsEnabled.collect { ai ->
                _uiState.update { it.copy(aiInsightsEnabled = ai) }
            }
        }
        // Free-text fields are seeded ONCE from persisted prefs. They must NOT be re-collected
        // from DataStore — echoing the async value back into the TextField resets the cursor and
        // scrambles fast input. Edits update _uiState synchronously (see setters) and persist async.
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cloudBaseUrl = uiPreferences.cloudBaseUrl.first(),
                    cloudModelId = uiPreferences.cloudModelId.first(),
                )
            }
        }
        viewModelScope.launch {
            secureKeyStore.hasKey.collect { hasKey ->
                _uiState.update { it.copy(cloudHasKey = hasKey) }
            }
        }
        viewModelScope.launch {
            secureKeyStore.hasWebSearchKey.collect { hasWebKey ->
                _uiState.update { it.copy(cloudHasWebSearchKey = hasWebKey) }
            }
        }
        // Phase-5 notification toggles: reactive, like the AI/backend toggles above.
        viewModelScope.launch {
            combine(
                coachNotificationPreferences.weeklyCheckInPushEnabled,
                coachNotificationPreferences.ambientNudgesEnabled,
            ) { weekly, ambient -> weekly to ambient }
                .collect { (weekly, ambient) ->
                    _uiState.update {
                        it.copy(weeklyCheckInPushEnabled = weekly, ambientNudgesEnabled = ambient)
                    }
                }
        }
        // Quiet hours is a suspend read (not a flow); seed the display once at startup. It is only
        // editable via the data layer today, so a one-shot read is sufficient for the read-only row.
        viewModelScope.launch {
            val quiet = coachNotificationPreferences.quietHours()
            _uiState.update { it.copy(quietHoursDisplay = formatQuietHours(quiet)) }
        }
    }

    fun setAiInsights(enabled: Boolean) {
        viewModelScope.launch { uiPreferences.setAiInsights(enabled) }
        // Schedule/cancel the periodic proactive-coach digest with the toggle (mirrors the Health
        // Connect sync pattern). run() also self-gates on the preference, so this is belt-and-braces.
        if (enabled) coachDigestCoordinator.enableBackgroundDigest()
        else coachDigestCoordinator.disableBackgroundDigest()
    }

    /** Toggle the single weekly check-in push (on by default). Mirrors [setAiInsights]. */
    fun setWeeklyCheckInPush(enabled: Boolean) {
        viewModelScope.launch { coachNotificationPreferences.setWeeklyCheckInPushEnabled(enabled) }
    }

    /** Toggle rare P0/celebration ambient nudges (off by default). Mirrors [setAiInsights]. */
    fun setAmbientNudges(enabled: Boolean) {
        viewModelScope.launch { coachNotificationPreferences.setAmbientNudgesEnabled(enabled) }
    }

    fun setCloudBaseUrl(url: String) {
        _uiState.update { it.copy(cloudBaseUrl = url) }      // synchronous → field updates instantly
        viewModelScope.launch { uiPreferences.setCloudBaseUrl(url) }  // persist (async side effect)
    }

    fun setCloudModelId(model: String) {
        _uiState.update { it.copy(cloudModelId = model) }
        viewModelScope.launch { uiPreferences.setCloudModelId(model) }
    }

    fun setCloudApiKey(key: String) {
        viewModelScope.launch { withContext(Dispatchers.IO) { secureKeyStore.setApiKey(key) } }
    }

    fun clearCloudApiKey() {
        viewModelScope.launch { withContext(Dispatchers.IO) { secureKeyStore.clearApiKey() } }
    }

    fun setWebSearchKey(key: String) {
        viewModelScope.launch { withContext(Dispatchers.IO) { secureKeyStore.setWebSearchKey(key) } }
    }

    fun clearWebSearchKey() {
        viewModelScope.launch { withContext(Dispatchers.IO) { secureKeyStore.clearWebSearchKey() } }
    }

    fun testCloudConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(testingConnection = true, testConnectionResult = null) }
            val s = _uiState.value
            val key = secureKeyStore.getApiKey()
            if (s.cloudBaseUrl.isBlank() || s.cloudModelId.isBlank() || key.isBlank()) {
                _uiState.update { it.copy(testingConnection = false, testConnectionResult = "Fill in URL, model, and API key first.") }
                return@launch
            }
            val result = try {
                withContext(Dispatchers.IO) {
                    openAiCompatClient.completion(
                        config = CloudConfig(baseUrl = s.cloudBaseUrl, apiKey = key, model = s.cloudModelId),
                        messages = listOf(ChatRequestMessage(role = "user", content = "ping")),
                        toolSchemasJson = emptyList(),
                    )
                }
                "Connection OK"
            } catch (e: Exception) {
                "Failed: ${e.message?.take(120) ?: "unknown error"}"
            }
            _uiState.update { it.copy(testingConnection = false, testConnectionResult = result) }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    /**
     * Format a [QuietHours] window for the read-only settings row, e.g. `10 PM – 7 AM`. Uses a
     * fixed 12-hour clock with an en-dash separator; hours only (the window is whole-hour by design).
     */
    private fun formatQuietHours(quiet: com.zack.recomptracker.domain.coach.QuietHours): String {
        fun label(time: java.time.LocalTime): String {
            val hour = time.hour
            val period = if (hour < 12) "AM" else "PM"
            val display = when (hour % 12) { 0 -> 12; else -> hour % 12 }
            return "$display $period"
        }
        return "${label(quiet.start)} – ${label(quiet.end)}"
    }
}
