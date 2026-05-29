package com.zack.recomptracker.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.health.HealthConnectAvailability
import com.zack.recomptracker.data.health.HealthConnectRepository
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.BackupRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val healthConnectAvailability: HealthConnectAvailability = HealthConnectAvailability.NotSupported,
    val healthConnectEnabled: Boolean = false,
    val healthConnectHasPermissions: Boolean = false,
    val healthConnectSyncing: Boolean = false,
    val pendingHcPermissionRequest: Boolean = false,
)

class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val hcRepository: HealthConnectRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val hcPermissionsContract = hcRepository.permissionsContract()
    val hcRequiredPermissions: Set<String> = hcRepository.requiredPermissions

    init {
        val availability = hcRepository.availability()
        viewModelScope.launch {
            val hasPerms = if (availability == HealthConnectAvailability.Available) {
                hcRepository.hasPermissions()
            } else false
            val prefs = planRepository.preferences.first()
            _uiState.update {
                it.copy(
                    healthConnectAvailability = availability,
                    healthConnectEnabled = prefs.healthConnectEnabled,
                    healthConnectHasPermissions = hasPerms,
                )
            }
        }
    }

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

    fun onHealthConnectToggled(enabled: Boolean) {
        if (enabled) {
            when (_uiState.value.healthConnectAvailability) {
                HealthConnectAvailability.Available ->
                    _uiState.update { it.copy(pendingHcPermissionRequest = true) }
                HealthConnectAvailability.NotInstalled ->
                    _uiState.update { it.copy(message = "Install Health Connect from the Play Store first.") }
                HealthConnectAvailability.NotSupported ->
                    _uiState.update { it.copy(message = "Health Connect is not supported on this device.") }
            }
        } else {
            viewModelScope.launch {
                val prefs = planRepository.preferences.first()
                planRepository.save(prefs.copy(healthConnectEnabled = false))
                _uiState.update { it.copy(healthConnectEnabled = false) }
            }
        }
    }

    fun onHcPermissionRequestConsumed() {
        _uiState.update { it.copy(pendingHcPermissionRequest = false) }
    }

    fun onPermissionsResult(granted: Set<String>) {
        viewModelScope.launch {
            if (granted.containsAll(hcRepository.requiredPermissions)) {
                val prefs = planRepository.preferences.first()
                planRepository.save(prefs.copy(healthConnectEnabled = true))
                _uiState.update {
                    it.copy(
                        healthConnectEnabled = true,
                        healthConnectHasPermissions = true,
                    )
                }
            } else {
                _uiState.update { it.copy(message = "All permissions are required to sync health data.") }
            }
        }
    }

    fun syncNow() {
        if (_uiState.value.healthConnectSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(healthConnectSyncing = true) }
            runCatching {
                val date = LocalDate.now()
                val result = hcRepository.readToday(date)
                logRepository.applyHealthConnectSync(date, result)
            }.onSuccess {
                _uiState.update { it.copy(healthConnectSyncing = false, message = "Synced.") }
            }.onFailure {
                _uiState.update { it.copy(healthConnectSyncing = false, message = "Sync failed.") }
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
