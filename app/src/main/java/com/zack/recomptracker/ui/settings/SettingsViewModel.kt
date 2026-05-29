package com.zack.recomptracker.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.BackupRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val busy: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun exportToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runBusy("Backup exported.") {
                val json = backupRepository.createBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray())
                    } ?: error("Could not open export destination.")
                }
            }
        }
    }

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runBusy("Backup imported.") {
                val rawJson = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    } ?: error("Could not open backup file.")
                }
                backupRepository.restoreFromJson(rawJson)
            }
        }
    }

    fun resetLogsOnly() {
        viewModelScope.launch {
            runBusy("Logs reset. Saved foods and meals kept.") {
                logRepository.resetAllLogs()
            }
        }
    }

    fun resetEverything() {
        viewModelScope.launch {
            runBusy("All local data reset.") {
                backupRepository.resetEverything(PlanPreferences())
            }
        }
    }

    private suspend fun runBusy(successMessage: String, block: suspend () -> Unit) {
        _uiState.update { it.copy(busy = true, message = null) }
        runCatching { block() }
            .onSuccess { _uiState.update { it.copy(busy = false, message = successMessage) } }
            .onFailure { error -> _uiState.update { it.copy(busy = false, message = error.message ?: "Operation failed.") } }
    }
}
