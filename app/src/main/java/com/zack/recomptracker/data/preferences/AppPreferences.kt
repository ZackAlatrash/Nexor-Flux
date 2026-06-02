package com.zack.recomptracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    suspend fun setFont(font: String) {
        context.uiDataStore.edit { it[Keys.SelectedFont] = font }
    }

    suspend fun setAiInsights(enabled: Boolean) {
        context.uiDataStore.edit { it[Keys.AiInsightsEnabled] = enabled }
    }

    private object Keys {
        val SelectedFont = stringPreferencesKey("selected_font")
        val AiInsightsEnabled = booleanPreferencesKey("ai_insights_enabled")
    }
}
