package com.zack.recomptracker.ui.more

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.AiBackend
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.ModelVariant
import com.zack.recomptracker.data.health.HealthConnectAvailability
import com.zack.recomptracker.data.health.HealthConnectRepository
import com.zack.recomptracker.data.preferences.SecureKeyStore
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.data.remote.ChatRequestMessage
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.repository.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class MoreFlags(
    val font: String,
    val ai: Boolean,
    val backend: AiBackend,
    val baseUrl: String,
    val modelId: String,
)

data class MoreUiState(
    val selectedFont: String = "default",
    val aiInsightsEnabled: Boolean = false,
    val healthConnectConnected: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val aiBackend: AiBackend = AiBackend.LOCAL,
    val cloudBaseUrl: String = "",
    val cloudModelId: String = "",
    val cloudHasKey: Boolean = false,
    val testConnectionResult: String? = null,
    val testingConnection: Boolean = false,
)

class MoreViewModel(
    private val uiPreferences: UiPreferences,
    private val hcRepository: HealthConnectRepository,
    private val backupRepository: BackupRepository,
    private val aiInsightCoordinator: AiInsightCoordinator,
    private val secureKeyStore: SecureKeyStore,
    private val openAiCompatClient: OpenAiCompatClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MoreUiState())
    val uiState: StateFlow<MoreUiState> = _uiState.asStateFlow()

    val aiInsightState: StateFlow<AiInsightState> = aiInsightCoordinator.state
    val selectedModel: StateFlow<ModelVariant> = aiInsightCoordinator.selectedModel

    init {
        val hcAvailable = hcRepository.availability() == HealthConnectAvailability.Available
        viewModelScope.launch {
            combine(
                uiPreferences.selectedFont,
                uiPreferences.aiInsightsEnabled,
                uiPreferences.aiBackend,
                uiPreferences.cloudBaseUrl,
                uiPreferences.cloudModelId,
            ) { font, ai, backend, baseUrl, modelId ->
                MoreFlags(font, ai, backend, baseUrl, modelId)
            }.collect { f ->
                val connected = hcAvailable && hcRepository.hasPermissions()
                _uiState.update {
                    it.copy(
                        selectedFont = f.font,
                        aiInsightsEnabled = f.ai,
                        healthConnectConnected = connected,
                        aiBackend = f.backend,
                        cloudBaseUrl = f.baseUrl,
                        cloudModelId = f.modelId,
                    )
                }
            }
        }
        viewModelScope.launch {
            secureKeyStore.hasKey.collect { hasKey ->
                _uiState.update { it.copy(cloudHasKey = hasKey) }
            }
        }
    }

    fun setFont(font: String) {
        viewModelScope.launch { uiPreferences.setFont(font) }
    }

    fun setAiInsights(enabled: Boolean) {
        viewModelScope.launch { uiPreferences.setAiInsights(enabled) }
    }

    fun setAiBackend(backend: AiBackend) {
        viewModelScope.launch { uiPreferences.setAiBackend(backend) }
    }

    fun setCloudBaseUrl(url: String) {
        viewModelScope.launch { uiPreferences.setCloudBaseUrl(url) }
    }

    fun setCloudModelId(model: String) {
        viewModelScope.launch { uiPreferences.setCloudModelId(model) }
    }

    fun setCloudApiKey(key: String) {
        secureKeyStore.setApiKey(key)
    }

    fun clearCloudApiKey() {
        secureKeyStore.clearApiKey()
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

    fun exportToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            try {
                val json = backupRepository.createBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("Could not open export destination.")
                }
                _uiState.update { it.copy(busy = false, message = "Backup exported") }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, message = "Export failed: ${e.message}") }
            }
        }
    }

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            try {
                val rawJson = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                        ?: error("Could not open backup file.")
                }
                backupRepository.restoreFromJson(rawJson)
                _uiState.update { it.copy(busy = false, message = "Backup imported") }
            } catch (e: Exception) {
                _uiState.update { it.copy(busy = false, message = "Import failed: ${e.message}") }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
