package com.zack.recomptracker.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.data.health.HealthConnectAvailability
import com.zack.recomptracker.data.health.HealthConnectRepository
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.BackupRepository
import com.zack.recomptracker.data.repository.FoodCatalogRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PersonalFoodRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.domain.foodimport.FoodIdentity
import com.zack.recomptracker.domain.foodimport.FoodImportCandidate
import com.zack.recomptracker.ui.component.MessageKind
import com.zack.recomptracker.domain.foodimport.HistoricalFoodImporter
import com.zack.recomptracker.domain.foodimport.SamsungHealthFoodCsvParser
import com.zack.recomptracker.domain.foodimport.identity
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val messageKind: MessageKind = MessageKind.INFO,
    val healthConnectAvailability: HealthConnectAvailability = HealthConnectAvailability.NotSupported,
    val healthConnectEnabled: Boolean = false,
    val healthConnectHasPermissions: Boolean = false,
    val healthConnectSyncing: Boolean = false,
    val pendingHcPermissionRequest: Boolean = false,
    val healthConnectMessage: String? = null,
    val healthConnectMessageKind: MessageKind = MessageKind.INFO,
    val nevoSourceVersion: String? = null,
    val historicalFoodCandidates: List<FoodImportCandidate> = emptyList(),
    val selectedHistoricalFoodIdentities: Set<FoodIdentity> = emptySet(),
    val pendingNutritionPermissionRequest: Boolean = false,
    val historicalFoodBusy: Boolean = false,
)

class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val hcRepository: HealthConnectRepository,
    private val foodCatalogRepository: FoodCatalogRepository,
    private val personalFoodRepository: PersonalFoodRepository,
    private val userProfileStore: UserProfilePreferencesStore,
) : ViewModel() {
    private val samsungFoodParser = SamsungHealthFoodCsvParser()
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val hcPermissionsContract = hcRepository.permissionsContract()
    val hcRequiredPermissions: Set<String> = hcRepository.requiredPermissions
    val nutritionPermission: Set<String> = hcRepository.historicalNutritionPermissions

    val profileState: StateFlow<UserProfilePreferences> =
        userProfileStore.preferences
            .stateIn(viewModelScope, SharingStarted.Eagerly, UserProfilePreferences())

    fun saveProfile(profile: UserProfilePreferences) {
        viewModelScope.launch { userProfileStore.save(profile) }
    }

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
        viewModelScope.launch {
            foodCatalogRepository.observeNevoSourceVersion().collect { version ->
                _uiState.update { it.copy(nevoSourceVersion = version) }
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

    fun exportPersonalFoodsToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runBusy("Personal foods exported.") {
                writeText(context, uri, personalFoodRepository.createPersonalFoodsJson())
            }
        }
    }

    fun importPersonalFoodsFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runBusyWithSummary("Personal foods imported", personalFoodRepository::mergePersonalFoodsJson) {
                readText(context, uri)
            }
        }
    }

    fun importNevoFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        foodCatalogRepository.replaceNevoCatalog(stream)
                    } ?: error("Could not open selected file.")
                }
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        busy = false,
                        message = "NEVO catalog imported: ${summary.importedCount} added; ${summary.duplicateCount} duplicates skipped.",
                        messageKind = MessageKind.SUCCESS,
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(busy = false, message = error.message ?: "Import failed.", messageKind = MessageKind.ERROR) }
            }
        }
    }

    fun removeNevoCatalog() {
        viewModelScope.launch {
            runBusy("NEVO catalog removed.") {
                foodCatalogRepository.removeNevoCatalog()
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
                    _uiState.update { it.copy(healthConnectMessage = "Install Health Connect from the Play Store first.", healthConnectMessageKind = MessageKind.ERROR) }
                HealthConnectAvailability.NotSupported ->
                    _uiState.update { it.copy(healthConnectMessage = "Health Connect is not supported on this device.", healthConnectMessageKind = MessageKind.ERROR) }
            }
        } else {
            viewModelScope.launch {
                val prefs = planRepository.preferences.first()
                planRepository.save(prefs.copy(healthConnectEnabled = false))
                _uiState.update { it.copy(healthConnectEnabled = false, healthConnectMessage = "Disconnected.", healthConnectMessageKind = MessageKind.SUCCESS) }
            }
        }
    }

    fun onHcPermissionRequestConsumed() {
        _uiState.update { it.copy(pendingHcPermissionRequest = false) }
    }

    fun startHistoricalFoodImport() {
        if (_uiState.value.healthConnectAvailability != HealthConnectAvailability.Available) {
            _uiState.update { it.copy(healthConnectMessage = "Health Connect is not available on this device.", healthConnectMessageKind = MessageKind.ERROR) }
            return
        }
        if (!hcRepository.supportsHistoricalNutritionImport()) {
            _uiState.update {
                it.copy(healthConnectMessage = "Health Connect on this device cannot provide 365-day history access.", healthConnectMessageKind = MessageKind.ERROR)
            }
            return
        }
        viewModelScope.launch {
            if (hcRepository.hasHistoricalNutritionPermissions()) {
                scanHistoricalFoods()
            } else {
                _uiState.update { it.copy(pendingNutritionPermissionRequest = true) }
            }
        }
    }

    fun onNutritionPermissionRequestConsumed() {
        _uiState.update { it.copy(pendingNutritionPermissionRequest = false) }
    }

    fun onNutritionPermissionsResult() {
        viewModelScope.launch {
            if (hcRepository.hasHistoricalNutritionPermissions()) {
                scanHistoricalFoods()
            } else {
                _uiState.update {
                    it.copy(healthConnectMessage = "Nutrition permission is required to import historical foods.", healthConnectMessageKind = MessageKind.ERROR)
                }
            }
        }
    }

    fun scanSamsungHealthFoodExport(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(historicalFoodBusy = true, healthConnectMessage = "Reading Samsung Health export…", healthConnectMessageKind = MessageKind.INFO) }
            runCatching {
                val parsed = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { raw ->
                        openFoodInfoStream(context, uri, raw)?.use { foodStream ->
                            samsungFoodParser.parse(foodStream)
                        } ?: error(
                            "No food_info CSV found. If you picked the Samsung Health ZIP, " +
                                "make sure it is the unmodified export file. " +
                                "Otherwise pick the \"com.samsung.health.food_info.T.csv\" file directly."
                        )
                    } ?: error("Could not open selected file.")
                }
                HistoricalFoodImporter.reviewCandidates(
                    existing = personalFoodRepository.getPersonalFoods(),
                    history = parsed,
                )
            }.onSuccess { candidates ->
                _uiState.update {
                    it.copy(
                        historicalFoodBusy = false,
                        historicalFoodCandidates = candidates,
                        selectedHistoricalFoodIdentities = candidates.mapTo(mutableSetOf(), FoodImportCandidate::identity),
                        healthConnectMessage = if (candidates.isEmpty()) {
                            "No new foods found — all items from that file are already in your library."
                        } else {
                            "${candidates.size} foods ready for review."
                        },
                        healthConnectMessageKind = MessageKind.SUCCESS,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        historicalFoodBusy = false,
                        healthConnectMessage = error.message ?: "Samsung Health food import failed.",
                        healthConnectMessageKind = MessageKind.ERROR,
                    )
                }
            }
        }
    }

    fun toggleHistoricalFoodCandidate(candidate: FoodImportCandidate) {
        _uiState.update { state ->
            val identity = candidate.identity()
            val selected = state.selectedHistoricalFoodIdentities
            state.copy(
                selectedHistoricalFoodIdentities = if (identity in selected) selected - identity else selected + identity,
            )
        }
    }

    fun dismissHistoricalFoodReview() {
        _uiState.update {
            it.copy(historicalFoodCandidates = emptyList(), selectedHistoricalFoodIdentities = emptySet())
        }
    }

    fun importSelectedHistoricalFoods() {
        val state = _uiState.value
        val selected = state.historicalFoodCandidates.filter { it.identity() in state.selectedHistoricalFoodIdentities }
        viewModelScope.launch {
            runCatching { personalFoodRepository.mergePersonalFoods(selected) }
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            historicalFoodCandidates = emptyList(),
                            selectedHistoricalFoodIdentities = emptySet(),
                            healthConnectMessage = "${summary.importedCount} historical foods imported; ${summary.duplicateCount} duplicates skipped.",
                            healthConnectMessageKind = MessageKind.SUCCESS,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(healthConnectMessage = error.message ?: "Historical food import failed.", healthConnectMessageKind = MessageKind.ERROR) }
                }
        }
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
                            healthConnectMessageKind = MessageKind.SUCCESS,
                        )
                    }
                    syncNow()
                }.onFailure { e ->
                    Log.e(TAG, "Failed to persist healthConnectEnabled", e)
                    _uiState.update { it.copy(healthConnectMessage = "Couldn't save the setting: ${e.message}", healthConnectMessageKind = MessageKind.ERROR) }
                }
            } else {
                _uiState.update {
                    it.copy(healthConnectMessage = "Permission wasn't granted. Tap the toggle to try again.", healthConnectMessageKind = MessageKind.ERROR)
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
                        healthConnectMessageKind = MessageKind.SUCCESS,
                    )
                }
            }.onFailure { e ->
                Log.e(TAG, "syncNow failed", e)
                _uiState.update { it.copy(healthConnectSyncing = false, healthConnectMessage = "Sync failed: ${e.message}", healthConnectMessageKind = MessageKind.ERROR) }
            }
        }
    }

    private suspend fun runBusy(successMessage: String, block: suspend () -> Unit) {
        _uiState.update { it.copy(busy = true, message = null, messageKind = MessageKind.INFO) }
        runCatching { block() }
            .onSuccess { _uiState.update { it.copy(busy = false, message = successMessage, messageKind = MessageKind.SUCCESS) } }
            .onFailure { error -> _uiState.update { it.copy(busy = false, message = error.message ?: "Operation failed.", messageKind = MessageKind.ERROR) } }
    }

    private suspend fun scanHistoricalFoods() {
        _uiState.update { it.copy(historicalFoodBusy = true, healthConnectMessage = "Scanning the last 365 days…", healthConnectMessageKind = MessageKind.INFO) }
        runCatching {
            val history = hcRepository.readHistoricalNutrition().getOrThrow()
            HistoricalFoodImporter.reviewCandidates(
                existing = personalFoodRepository.getPersonalFoods(),
                history = history,
            )
        }.onSuccess { candidates ->
            _uiState.update {
                it.copy(
                    historicalFoodBusy = false,
                    historicalFoodCandidates = candidates,
                    selectedHistoricalFoodIdentities = candidates.mapTo(mutableSetOf(), FoodImportCandidate::identity),
                    healthConnectMessage = if (candidates.isEmpty()) {
                        "No importable foods found. Samsung Health writes meal-level records (Breakfast, Lunch, etc.) to Health Connect rather than individual food items — these are filtered out. Use the NEVO CSV import for a full food database."
                    } else {
                        "${candidates.size} foods ready for review."
                    },
                    healthConnectMessageKind = MessageKind.SUCCESS,
                )
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    historicalFoodBusy = false,
                    healthConnectMessage = error.message ?: "Historical food scan failed.",
                    healthConnectMessageKind = MessageKind.ERROR,
                )
            }
        }
    }

    private suspend fun runBusyWithSummary(
        label: String,
        import: suspend (String) -> com.zack.recomptracker.domain.foodimport.FoodImportSummary,
        read: suspend () -> String,
    ) {
        _uiState.update { it.copy(busy = true, message = null, messageKind = MessageKind.INFO) }
        runCatching { import(read()) }
            .onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        busy = false,
                        message = "$label: ${summary.importedCount} added; ${summary.duplicateCount} duplicates skipped.",
                        messageKind = MessageKind.SUCCESS,
                    )
                }
            }
            .onFailure { error ->
                _uiState.update { it.copy(busy = false, message = error.message ?: "Import failed.", messageKind = MessageKind.ERROR) }
            }
    }

    private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().readText()
        } ?: error("Could not open selected file.")
    }

    private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(text.toByteArray())
        } ?: error("Could not open export destination.")
    }

    // Returns the food_info stream from either a ZIP export or a bare CSV.
    // The caller is responsible for closing the returned InputStream.
    private fun openFoodInfoStream(context: Context, uri: Uri, raw: InputStream): InputStream? {
        val fileName = uri.lastPathSegment?.lowercase() ?: ""
        val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
        val isZip = mimeType.contains("zip") || fileName.endsWith(".zip")
        if (!isZip) return raw  // bare CSV — hand back the same stream

        // Walk the ZIP entries; return a non-closing wrapper around the matching entry.
        val zip = ZipInputStream(raw)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && entry.name.contains("food_info", ignoreCase = true)) {
                return zip  // ZipInputStream positioned at the start of this entry
            }
            zip.closeEntry()
        }
        zip.close()
        return null
    }

    private companion object {
        const val TAG = "HealthConnect"
    }
}
