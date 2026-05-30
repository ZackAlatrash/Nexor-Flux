package com.zack.recomptracker.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
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
    val healthConnectMessage: String? = null,
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
                    _uiState.update { it.copy(pendingHcPermissionRequest = true, healthConnectMessage = null) }
                HealthConnectAvailability.NotInstalled ->
                    _uiState.update { it.copy(healthConnectMessage = "Install Health Connect from the Play Store first.") }
                HealthConnectAvailability.NotSupported ->
                    _uiState.update { it.copy(healthConnectMessage = "Health Connect is not supported on this device.") }
            }
        } else {
            viewModelScope.launch {
                val prefs = planRepository.preferences.first()
                planRepository.save(prefs.copy(healthConnectEnabled = false))
                _uiState.update { it.copy(healthConnectEnabled = false, healthConnectMessage = "Disconnected.") }
            }
        }
    }

    fun onHcPermissionRequestConsumed() {
        _uiState.update { it.copy(pendingHcPermissionRequest = false) }
    }

    /**
     * Called when the Health Connect permission screen returns.
     *
     * The result contract's returned granted-set is unreliable on some Health
     * Connect versions (it can come back empty even when the user granted
     * everything), so we re-query the authoritative granted state instead of
     * trusting the callback payload.
     */
    fun onPermissionsResult() {
        viewModelScope.launch {
            val granted = hcRepository.hasPermissions()
            Log.d(TAG, "onPermissionsResult: hasPermissions=$granted")
            if (granted) {
                runCatching {
                    val prefs = planRepository.preferences.first()
                    planRepository.save(prefs.copy(healthConnectEnabled = true))
                }.onSuccess {
                    _uiState.update {
                        it.copy(
                            healthConnectEnabled = true,
                            healthConnectHasPermissions = true,
                            healthConnectMessage = "Connected. Syncing…",
                        )
                    }
                    syncNow()
                }.onFailure { e ->
                    Log.e(TAG, "Failed to persist healthConnectEnabled", e)
                    _uiState.update { it.copy(healthConnectMessage = "Couldn't save the setting: ${e.message}") }
                }
            } else {
                _uiState.update {
                    it.copy(healthConnectMessage = "Permission wasn't granted. Tap the toggle to try again.")
                }
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
                Log.d(TAG, "syncNow: read steps=${result.steps} weightKg=${result.weightKg} sleepHours=${result.sleepHours}")
                logRepository.applyHealthConnectSync(date, result)
                result
            }.onSuccess { result ->
                val any = result.steps != null || result.weightKg != null || result.sleepHours != null
                _uiState.update {
                    it.copy(
                        healthConnectSyncing = false,
                        healthConnectMessage = if (any) "Synced from Health Connect." else "Connected — no new data for today yet.",
                    )
                }
            }.onFailure { e ->
                Log.e(TAG, "syncNow failed", e)
                _uiState.update { it.copy(healthConnectSyncing = false, healthConnectMessage = "Sync failed: ${e.message}") }
            }
        }
    }

    private suspend fun runBusy(successMessage: String, block: suspend () -> Unit) {
        _uiState.update { it.copy(busy = true, message = null) }
        runCatching { block() }
            .onSuccess { _uiState.update { it.copy(busy = false, message = successMessage) } }
            .onFailure { error -> _uiState.update { it.copy(busy = false, message = error.message ?: "Operation failed.") } }
    }

    private companion object {
        const val TAG = "HealthConnect"
    }
}
