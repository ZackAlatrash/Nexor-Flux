package com.zack.recomptracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zack.recomptracker.ai.AiBackend
import com.zack.recomptracker.ai.ModelVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.planDataStore by preferencesDataStore(name = "plan_preferences")

class AppPreferences(
    private val context: Context,
) : PlanPreferencesSource {
    override val preferences: Flow<PlanPreferences> = context.planDataStore.data.map { prefs ->
        PlanPreferences(
            targetCalories = prefs[Keys.TargetCalories] ?: 2550,
            targetProteinG = prefs[Keys.TargetProteinG] ?: 165,
            targetCarbsG = prefs[Keys.TargetCarbsG] ?: 320,
            targetFatG = prefs[Keys.TargetFatG] ?: 68,
            maintenancePhaseStartDate = prefs[Keys.MaintenancePhaseStartDate],
            weightTrendThresholdKgPerWeek = prefs[Keys.WeightTrendThresholdKgPerWeek] ?: 0.20,
            waistIncreaseThresholdCm = prefs[Keys.WaistIncreaseThresholdCm] ?: 0.5,
            adherenceMinimumPercent = prefs[Keys.AdherenceMinimumPercent] ?: 80.0,
            reviewCadenceDays = prefs[Keys.ReviewCadenceDays] ?: 7,
            useMetricUnits = prefs[Keys.UseMetricUnits] ?: true,
            calorieZoneLowerBound = prefs[Keys.CalorieZoneLowerBound] ?: 2400,
            calorieZoneUpperBound = prefs[Keys.CalorieZoneUpperBound] ?: 2600,
            healthConnectEnabled = prefs[Keys.HealthConnectEnabled] ?: false,
        )
    }

    override suspend fun save(preferences: PlanPreferences) {
        context.planDataStore.edit { prefs ->
            prefs[Keys.TargetCalories] = preferences.targetCalories
            prefs[Keys.TargetProteinG] = preferences.targetProteinG
            prefs[Keys.TargetCarbsG] = preferences.targetCarbsG
            prefs[Keys.TargetFatG] = preferences.targetFatG
            if (preferences.maintenancePhaseStartDate == null) {
                prefs.remove(Keys.MaintenancePhaseStartDate)
            } else {
                prefs[Keys.MaintenancePhaseStartDate] = preferences.maintenancePhaseStartDate
            }
            prefs[Keys.WeightTrendThresholdKgPerWeek] = preferences.weightTrendThresholdKgPerWeek
            prefs[Keys.WaistIncreaseThresholdCm] = preferences.waistIncreaseThresholdCm
            prefs[Keys.AdherenceMinimumPercent] = preferences.adherenceMinimumPercent
            prefs[Keys.ReviewCadenceDays] = preferences.reviewCadenceDays
            prefs[Keys.UseMetricUnits] = preferences.useMetricUnits
            prefs[Keys.CalorieZoneLowerBound] = preferences.calorieZoneLowerBound
            prefs[Keys.CalorieZoneUpperBound] = preferences.calorieZoneUpperBound
            prefs[Keys.HealthConnectEnabled] = preferences.healthConnectEnabled
        }
    }

    override suspend fun resetDefaults() {
        context.planDataStore.edit { it.clear() }
    }

    private object Keys {
        val TargetCalories = intPreferencesKey("target_calories")
        val TargetProteinG = intPreferencesKey("target_protein_g")
        val TargetCarbsG = intPreferencesKey("target_carbs_g")
        val TargetFatG = intPreferencesKey("target_fat_g")
        val MaintenancePhaseStartDate = stringPreferencesKey("maintenance_phase_start_date")
        val WeightTrendThresholdKgPerWeek = doublePreferencesKey("weight_trend_threshold_kg_per_week")
        val WaistIncreaseThresholdCm = doublePreferencesKey("waist_increase_threshold_cm")
        val AdherenceMinimumPercent = doublePreferencesKey("adherence_minimum_percent")
        val ReviewCadenceDays = intPreferencesKey("review_cadence_days")
        val UseMetricUnits = booleanPreferencesKey("use_metric_units")
        val CalorieZoneLowerBound = intPreferencesKey("calorie_zone_lower_bound")
        val CalorieZoneUpperBound = intPreferencesKey("calorie_zone_upper_bound")
        val HealthConnectEnabled = booleanPreferencesKey("health_connect_enabled")
        val SelectedFont = stringPreferencesKey("selected_font")
        val AiInsightsEnabled = booleanPreferencesKey("ai_insights_enabled")
    }
}

// Separate lightweight preference class for UI-only settings (font, AI)
class UiPreferences(private val context: Context) {
    private val Context.uiDataStore by preferencesDataStore(name = "ui_preferences")

    val selectedFont: kotlinx.coroutines.flow.Flow<String> = context.uiDataStore.data.map { it[Keys.SelectedFont] ?: "default" }
    val aiInsightsEnabled: kotlinx.coroutines.flow.Flow<Boolean> = context.uiDataStore.data.map { it[Keys.AiInsightsEnabled] ?: false }

    /** True once the user has completed first-run onboarding. Gates the nav start destination. */
    val onboardingComplete: kotlinx.coroutines.flow.Flow<Boolean> =
        context.uiDataStore.data.map { it[Keys.OnboardingComplete] ?: false }

    /**
     * The DownloadManager ID of an in-progress model download. Persisted so that if
     * the app process is killed mid-download, the coordinator can resume progress
     * polling on next launch. -1L means no active download.
     */
    val pendingDownloadId: kotlinx.coroutines.flow.Flow<Long> =
        context.uiDataStore.data.map { it[Keys.PendingDownloadId] ?: -1L }

    val aiBackend: kotlinx.coroutines.flow.Flow<AiBackend> =
        context.uiDataStore.data.map { AiBackend.fromStored(it[Keys.AiBackend]) }

    val lastSeenBriefingSignature: kotlinx.coroutines.flow.Flow<String> =
        context.uiDataStore.data.map { it[Keys.LastSeenBriefingSignature] ?: "" }

    val cloudBaseUrl: kotlinx.coroutines.flow.Flow<String> =
        context.uiDataStore.data.map { it[Keys.CloudBaseUrl] ?: "" }

    val cloudModelId: kotlinx.coroutines.flow.Flow<String> =
        context.uiDataStore.data.map {
            when (val stored = it[Keys.CloudModelId] ?: "") {
                // The free OpenRouter gpt-oss-20b route regressed: it stopped emitting OpenAI
                // tool_calls (it only "reasons" about the call), silently breaking food logging for
                // every user with no app change. Remap that exact id to a single-provider (Nvidia)
                // free model that tool-calls reliably. Users on any other model are left untouched.
                // See memory: project_cloud_coach_tool_calling.
                DEPRECATED_TOOL_BROKEN_MODEL -> DEFAULT_CLOUD_MODEL
                else -> stored
            }
        }

    /** True when base URL and model id are both set (API-key presence is tracked by SecureKeyStore). */
    val cloudConfigPresent: kotlinx.coroutines.flow.Flow<Boolean> =
        context.uiDataStore.data.map {
            !(it[Keys.CloudBaseUrl].isNullOrBlank()) && !(it[Keys.CloudModelId].isNullOrBlank())
        }

    val selectedModelVariant: kotlinx.coroutines.flow.Flow<ModelVariant> =
        context.uiDataStore.data.map {
            when (it[Keys.SelectedModelVariant]) {
                ModelVariant.GEMMA_4B.name -> ModelVariant.GEMMA_4B
                else -> ModelVariant.GEMMA_2B
            }
        }

    val accentTheme: kotlinx.coroutines.flow.Flow<com.zack.recomptracker.ui.theme.AccentTheme> =
        context.uiDataStore.data.map {
            val stored = it[Keys.AccentTheme]
            com.zack.recomptracker.ui.theme.AccentTheme.entries
                .firstOrNull { theme -> theme.name == stored }
                ?: com.zack.recomptracker.ui.theme.AccentTheme.VIOLET
        }

    val themeMode: kotlinx.coroutines.flow.Flow<com.zack.recomptracker.ui.theme.ThemeMode> =
        context.uiDataStore.data.map {
            com.zack.recomptracker.ui.theme.ThemeMode.fromStored(it[Keys.ThemeMode])
        }

    suspend fun setFont(font: String) {
        context.uiDataStore.edit { it[Keys.SelectedFont] = font }
    }

    suspend fun setAiInsights(enabled: Boolean) {
        context.uiDataStore.edit { it[Keys.AiInsightsEnabled] = enabled }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.uiDataStore.edit { it[Keys.OnboardingComplete] = complete }
    }

    suspend fun setAccentTheme(theme: com.zack.recomptracker.ui.theme.AccentTheme) {
        context.uiDataStore.edit { it[Keys.AccentTheme] = theme.name }
    }

    suspend fun setThemeMode(mode: com.zack.recomptracker.ui.theme.ThemeMode) {
        context.uiDataStore.edit { it[Keys.ThemeMode] = mode.storageValue }
    }

    /**
     * Persists [id] so it survives process death. Pass -1L to clear.
     */
    suspend fun setPendingDownloadId(id: Long) {
        context.uiDataStore.edit { prefs ->
            if (id == -1L) prefs.remove(Keys.PendingDownloadId)
            else prefs[Keys.PendingDownloadId] = id
        }
    }

    suspend fun setSelectedModel(variant: ModelVariant) {
        context.uiDataStore.edit { it[Keys.SelectedModelVariant] = variant.name }
    }

    suspend fun setAiBackend(backend: AiBackend) {
        context.uiDataStore.edit { it[Keys.AiBackend] = backend.name }
    }

    suspend fun setCloudBaseUrl(url: String) {
        context.uiDataStore.edit { it[Keys.CloudBaseUrl] = url.trim() }
    }

    suspend fun setCloudModelId(model: String) {
        context.uiDataStore.edit { it[Keys.CloudModelId] = model.trim() }
    }

    suspend fun setLastSeenBriefingSignature(signature: String) {
        context.uiDataStore.edit { it[Keys.LastSeenBriefingSignature] = signature }
    }

    private object Keys {
        val SelectedFont = stringPreferencesKey("selected_font")
        val AiInsightsEnabled = booleanPreferencesKey("ai_insights_enabled")
        val OnboardingComplete = booleanPreferencesKey("onboarding_complete")
        val PendingDownloadId = longPreferencesKey("pending_download_id")
        val SelectedModelVariant = stringPreferencesKey("selected_model_variant")
        val AccentTheme = stringPreferencesKey("accent_theme")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val AiBackend = stringPreferencesKey("ai_backend")
        val CloudBaseUrl = stringPreferencesKey("cloud_base_url")
        val CloudModelId = stringPreferencesKey("cloud_model_id")
        val LastSeenBriefingSignature = stringPreferencesKey("last_seen_briefing_signature")
    }

    companion object {
        /** Free OpenRouter model that regressed to reasoning-only and no longer emits tool_calls. */
        const val DEPRECATED_TOOL_BROKEN_MODEL = "openai/gpt-oss-20b:free"

        /** Replacement default: single-provider (Nvidia) free model with reliable tool calling. */
        const val DEFAULT_CLOUD_MODEL = "nvidia/nemotron-nano-9b-v2:free"
    }
}
