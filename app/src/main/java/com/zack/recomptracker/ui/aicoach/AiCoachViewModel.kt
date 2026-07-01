package com.zack.recomptracker.ui.aicoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.AiBackend
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.ModelVariant
import com.zack.recomptracker.data.preferences.SecureKeyStore
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.coach.CoachPushChannel
import com.zack.recomptracker.data.coach.CoachPushPayload
import com.zack.recomptracker.domain.coach.CoachAction
import com.zack.recomptracker.domain.coach.CoachActionType
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.CoachSurface
import com.zack.recomptracker.domain.coach.Confidence
import com.zack.recomptracker.domain.coach.SignalCategory
import com.zack.recomptracker.domain.coach.SignalFacts
import com.zack.recomptracker.domain.coach.SignalKind
import com.zack.recomptracker.domain.coach.SignalRationale
import com.zack.recomptracker.domain.coach.SignalTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AiCoachFlags(
    val ai: Boolean,
    val backend: AiBackend,
)

data class AiCoachUiState(
    val aiInsightsEnabled: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val aiBackend: AiBackend = AiBackend.LOCAL,
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
    // DEBUG ONLY (temporary — remove with the debug panel block below before merge).
    private val coachInbox: com.zack.recomptracker.data.coach.CoachInbox,
    private val coachNotifier: com.zack.recomptracker.data.coach.CoachNotifier,
    private val coachJourney: com.zack.recomptracker.data.coach.CoachJourney,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiCoachUiState())
    val uiState: StateFlow<AiCoachUiState> = _uiState.asStateFlow()

    val aiInsightState: StateFlow<AiInsightState> = aiInsightCoordinator.state
    val selectedModel: StateFlow<ModelVariant> = aiInsightCoordinator.selectedModel

    init {
        // Reactive toggles/selectors (not free-text, so no typing race).
        viewModelScope.launch {
            combine(
                uiPreferences.aiInsightsEnabled,
                uiPreferences.aiBackend,
            ) { ai, backend ->
                AiCoachFlags(ai, backend)
            }.collect { f ->
                _uiState.update {
                    it.copy(
                        aiInsightsEnabled = f.ai,
                        aiBackend = f.backend,
                    )
                }
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

    fun setAiBackend(backend: AiBackend) {
        viewModelScope.launch { uiPreferences.setAiBackend(backend) }
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

    fun requestModelDownload() = aiInsightCoordinator.requestDownload()
    fun cancelDownload() = aiInsightCoordinator.cancelDownload()
    fun deleteModel() = aiInsightCoordinator.deleteModel()
    fun setModel(variant: ModelVariant) = aiInsightCoordinator.setSelectedModel(variant)

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUG ONLY (temporary — remove this whole block, the coachInbox/coachNotifier/
    // coachJourney constructor params, and the debug panel in AiCoachScreen before
    // merge). Exercises the gated proactive surfaces on-device without real data.
    // ═══════════════════════════════════════════════════════════════════════════

    private fun dbgSignal(
        kind: SignalKind,
        tier: SignalTier,
        category: SignalCategory,
        verdict: String,
        action: CoachAction,
    ): CoachSignal = CoachSignal(
        kind = kind,
        tier = tier,
        category = category,
        severity = 70,
        facts = SignalFacts(),
        verdict = verdict,
        action = action,
        rationale = SignalRationale(confidence = Confidence.HIGH),
        dedupKey = "DEBUG_${kind.name}",
        surface = CoachSurface.TODAY,
        fallbackText = verdict,
    )

    /** Labeled fixtures for the debug panel (label → signal), covering every card variant. */
    private val debugFixtures: List<Pair<String, CoachSignal>> by lazy {
        listOf(
            "New PR (celebration)" to dbgSignal(
                SignalKind.NEW_PR, SignalTier.P0, SignalCategory.TRAINING,
                "New bench press PR — 82.5 kg for 5, your best yet.",
                CoachAction(CoachActionType.OPEN_TRAINING, "See your lifts"),
            ),
            "Recomp win (celebration)" to dbgSignal(
                SignalKind.RECOMP_WIN, SignalTier.P0, SignalCategory.BODY,
                "Weight held flat while your waist dropped 1.2 cm — textbook recomposition.",
                CoachAction(CoachActionType.OPEN_WEEKLY_REVIEW, "See weekly review"),
            ),
            "Deload due" to dbgSignal(
                SignalKind.DELOAD_DUE, SignalTier.P1, SignalCategory.RECOVERY,
                "Reps-in-reserve is down to ~1 and recovery is sliding — pull volume back this week.",
                CoachAction(CoachActionType.OPEN_TRAINING, "Plan a deload"),
            ),
            "Training plateau" to dbgSignal(
                SignalKind.TRAINING_PLATEAU, SignalTier.P1, SignalCategory.TRAINING,
                "Your squat e1RM has been flat for three weeks — time to change a variable.",
                CoachAction(CoachActionType.OPEN_TRAINING, "Review training"),
            ),
            "Sleep↔hunger (info, no button)" to dbgSignal(
                SignalKind.SLEEP_HUNGER_LINK, SignalTier.P2, SignalCategory.RECOVERY,
                "On nights under 6 hours' sleep your hunger runs ~2 points higher. Protect your sleep.",
                CoachAction.None,
            ),
            "Low adherence" to dbgSignal(
                SignalKind.LOW_ADHERENCE, SignalTier.P1, SignalCategory.NUTRITION,
                "You've logged 3 of the last 7 days — hard to coach through gaps this big.",
                CoachAction(CoachActionType.OPEN_FOOD_LOG, "Open food log"),
            ),
            "Protein miss (training day)" to dbgSignal(
                SignalKind.PROTEIN_MISS_TRAINING_DAY, SignalTier.P2, SignalCategory.NUTRITION,
                "Protein runs ~30 g lower on your training days than rest days — top it up.",
                CoachAction(CoachActionType.OPEN_FOOD_LOG, "Open food log"),
            ),
            "Unconfirmed planned meals" to dbgSignal(
                SignalKind.UNCONFIRMED_PLANNED_MEALS, SignalTier.P3, SignalCategory.BEHAVIOR,
                "You have 3 planned meals from yesterday still unconfirmed.",
                CoachAction(CoachActionType.CONFIRM_PLANNED_MEALS, "Confirm meals"),
            ),
            "Quiet weigh-ins" to dbgSignal(
                SignalKind.QUIET_WEIGH_INS, SignalTier.P3, SignalCategory.BEHAVIOR,
                "No weigh-in in 6 days — a quick step on the scale keeps your trend honest.",
                CoachAction(CoachActionType.LOG_WEIGHT, "Log weight"),
            ),
            "Fat gain warning" to dbgSignal(
                SignalKind.FAT_GAIN_WARNING, SignalTier.P0, SignalCategory.BODY,
                "Weight and waist are both up over three weeks — let's tighten the deficit.",
                CoachAction(CoachActionType.OPEN_WEEKLY_REVIEW, "See weekly review"),
            ),
        )
    }

    val debugSignalLabels: List<String> get() = debugFixtures.map { it.first }

    /** Stage the fixture at [index] into the Today slot; view it on Home. */
    fun debugStageSignal(index: Int) {
        val (label, signal) = debugFixtures[index]
        viewModelScope.launch {
            coachInbox.stage(signal)
            _uiState.update { it.copy(message = "Staged \"$label\" — open Home to view the card.") }
        }
    }

    /** Fire a real push (bypassing the RateLimiter) for the PR fixture, to verify the notification. */
    fun debugFirePush() {
        val signal = debugFixtures.first().second
        viewModelScope.launch {
            coachNotifier.ensureChannels()
            val shown = CoachPushPayload.from(signal, CoachPushChannel.COACHING)
                ?.let { coachNotifier.show(it) } ?: false
            _uiState.update {
                it.copy(
                    message = if (shown) "Test push sent." else
                        "Push blocked — grant notification permission and retry.",
                )
            }
        }
    }

    /** Seed a few weeks of journey history so the coach references past weeks in chat/briefing. */
    fun debugSeedJourney() {
        viewModelScope.launch {
            coachJourney.recordWeeklyVerdict("dbg-wk-3", "2026-06-08", "Slight deficit held; weight trending down ~0.3 kg/wk.")
            coachJourney.recordWeeklyVerdict("dbg-wk-2", "2026-06-15", "Bench stalled and recovery dipped.")
            coachJourney.recordWeeklyVerdict("dbg-wk-1", "2026-06-22", "Recomposition on track — waist down, weight flat.")
            coachJourney.recordFiredSignal(debugFixtures[2].second, "dbg-wk-2")
            _uiState.update {
                it.copy(message = "Seeded 3 weeks of journey. Open Coach and start a NEW chat to see it referenced.")
            }
        }
    }

    /** Clear the staged Today card. */
    fun debugClearCard() {
        viewModelScope.launch {
            coachInbox.stage(null)
            _uiState.update { it.copy(message = "Cleared the Today card.") }
        }
    }

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
