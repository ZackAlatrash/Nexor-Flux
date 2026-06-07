package com.zack.recomptracker.ui.more

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.ModelVariant
import com.zack.recomptracker.data.health.HealthConnectAvailability
import com.zack.recomptracker.data.health.HealthConnectRepository
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.data.repository.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MoreUiState(
    val selectedFont: String = "default",
    val aiInsightsEnabled: Boolean = false,
    val healthConnectConnected: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class MoreViewModel(
    private val uiPreferences: UiPreferences,
    private val hcRepository: HealthConnectRepository,
    private val backupRepository: BackupRepository,
    private val aiInsightCoordinator: AiInsightCoordinator,
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
            ) { font, ai -> font to ai }
                .collect { (font, ai) ->
                    val connected = hcAvailable && hcRepository.hasPermissions()
                    _uiState.update {
                        it.copy(
                            selectedFont = font,
                            aiInsightsEnabled = ai,
                            healthConnectConnected = connected,
                        )
                    }
                }
        }
    }

    fun setFont(font: String) {
        viewModelScope.launch { uiPreferences.setFont(font) }
    }

    fun setAiInsights(enabled: Boolean) {
        viewModelScope.launch { uiPreferences.setAiInsights(enabled) }
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
