package com.zack.recomptracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zack.recomptracker.ai.ModelVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.planDataStore by preferencesDataStore(name = "plan_preferences")

class AppPreferences(
    private val context: Context,
) {
    val preferences: Flow<PlanPreferences> = context.planDataStore.data.map { prefs ->
        PlanPreferences(
            targetCalories = prefs[Keys.TargetCalories] ?: 2550,
            targetProteinG = prefs[Keys.TargetProteinG] ?: 165,
            targetCarbsG = prefs[Keys.TargetCarbsG] ?: 320,
            targetFatG = prefs[Keys.TargetFatG] ?: 68,
            maintenancePhaseStartDate = prefs[Keys.MaintenancePhaseStartDate],
            weightTrendThresholdKgPerWeek = prefs[Keys.WeightTrendThresholdKgPerWeek] ?: 0.20,
            waistIncreaseThresholdCm = prefs[Keys.WaistIncreaseThresholdCm] ?: 0.5,
            adherenceMinimumPercent = prefs[Keys.AdherenceMinimumPercent] ?: 85.0,
            reviewCadenceDays = prefs[Keys.ReviewCadenceDays] ?: 7,
            useMetricUnits = prefs[Keys.UseMetricUnits] ?: true,
            healthConnectEnabled = prefs[Keys.HealthConnectEnabled] ?: false,
        )
    }

    suspend fun save(preferences: PlanPreferences) {
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
            prefs[Keys.HealthConnectEnabled] = preferences.healthConnectEnabled
        }
    }

    suspend fun resetDefaults() {
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

    /**
     * The DownloadManager ID of an in-progress model download. Persisted so that if
     * the app process is killed mid-download, the coordinator can resume progress
     * polling on next launch. -1L means no active download.
     */
    val pendingDownloadId: kotlinx.coroutines.flow.Flow<Long> =
        context.uiDataStore.data.map { it[Keys.PendingDownloadId] ?: -1L }

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

    suspend fun setFont(font: String) {
        context.uiDataStore.edit { it[Keys.SelectedFont] = font }
    }

    suspend fun setAiInsights(enabled: Boolean) {
        context.uiDataStore.edit { it[Keys.AiInsightsEnabled] = enabled }
    }

    suspend fun setAccentTheme(theme: com.zack.recomptracker.ui.theme.AccentTheme) {
        context.uiDataStore.edit { it[Keys.AccentTheme] = theme.name }
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

    private object Keys {
        val SelectedFont = stringPreferencesKey("selected_font")
        val AiInsightsEnabled = booleanPreferencesKey("ai_insights_enabled")
        val PendingDownloadId = longPreferencesKey("pending_download_id")
        val SelectedModelVariant = stringPreferencesKey("selected_model_variant")
        val AccentTheme = stringPreferencesKey("accent_theme")
    }
}
