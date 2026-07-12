package com.zack.recomptracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.domain.plan.GeneratedPlan
import com.zack.recomptracker.domain.plan.PlanGenerationOutcome
import com.zack.recomptracker.domain.plan.PlanGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

private const val CM_PER_INCH = 2.54
private const val KG_PER_POUND = 0.45359237
private const val MIN_AGE = 13
private const val MAX_AGE = 120

// Plausibility bounds — an implausible measurement is rejected (parse → null) so it can never gate
// past validation or seed a garbage Mifflin-St Jeor plan (P1-14).
private const val MIN_HEIGHT_CM = 90
private const val MAX_HEIGHT_CM = 250
private const val MIN_WEIGHT_KG = 30.0
private const val MAX_WEIGHT_KG = 300.0
// Adjusted calorie target is clamped to the same range the coach tool uses; the zone is recomputed
// around the (clamped) target by PlanPreferences.withCalorieTarget (the canonical helper).
private const val MIN_CALORIE_TARGET = 500
private const val MAX_CALORIE_TARGET = 6000

internal fun isPlausibleHeightCm(cm: Int): Boolean = cm in MIN_HEIGHT_CM..MAX_HEIGHT_CM
internal fun isPlausibleWeightKg(kg: Double): Boolean = kg in MIN_WEIGHT_KG..MAX_WEIGHT_KG

/** Height in canonical centimetres from raw input, or null if unparseable or implausible. */
internal fun parseHeightCm(input: String, metric: Boolean): Int? {
    val value = input.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    val cm = if (metric) value else value * CM_PER_INCH
    return Math.round(cm).toInt().takeIf { isPlausibleHeightCm(it) }
}

/** Height in canonical centimetres from an imperial feet + inches split, or null if implausible. */
internal fun parseHeightCmImperial(feetInput: String, inchesInput: String): Int? {
    val feet = feetInput.trim().toIntOrNull() ?: return null
    // Inches is optional (a whole-foot height is valid) and must be a proper 0..<12 remainder.
    val inches = inchesInput.trim().ifBlank { "0" }.toDoubleOrNull() ?: return null
    if (feet < 0 || inches < 0.0 || inches >= 12.0) return null
    val totalInches = feet * 12 + inches
    if (totalInches <= 0.0) return null
    return Math.round(totalInches * CM_PER_INCH).toInt().takeIf { isPlausibleHeightCm(it) }
}

/** Canonical height (cm) from the current inputs — the metric cm field, or the imperial ft+in split. */
internal fun resolveHeightCm(s: OnboardingUiState): Int? =
    if (s.useMetricUnits) parseHeightCm(s.heightInput, metric = true)
    else parseHeightCmImperial(s.heightFeetInput, s.heightInchesInput)

/** Weight in canonical kilograms from raw input, or null if unparseable or implausible. */
internal fun parseWeightKg(input: String, metric: Boolean): Double? {
    val value = input.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    val kg = if (metric) value else value * KG_PER_POUND
    return kg.takeIf { isPlausibleWeightKg(it) }
}

/** Waist in canonical centimetres from raw input. Metric = cm; imperial = inches. Optional → null. */
internal fun parseWaistCm(input: String, metric: Boolean): Double? {
    val value = input.trim().toDoubleOrNull() ?: return null
    if (value <= 0.0) return null
    return if (metric) value else value * CM_PER_INCH
}

/** Whole years from an ISO `yyyy-MM-dd` birth date, or null if unset/unparseable/future. */
internal fun ageYearsFrom(birthDate: String?, today: LocalDate): Int? {
    val dob = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    if (dob.isAfter(today)) return null
    return Period.between(dob, today).years
}

/** A birth date that parses, is not in the future, and yields a plausible age. */
internal fun isValidBirthDate(birthDate: String?, today: LocalDate): Boolean {
    val age = ageYearsFrom(birthDate, today) ?: return false
    return age in MIN_AGE..MAX_AGE
}

/** Total onboarding steps (0..LAST_STEP). 0 About you · 1 Your body · 2 Goal & measurements · 3 Plan. */
const val ONBOARDING_STEPS = 4
private const val LAST_STEP = ONBOARDING_STEPS - 1

data class OnboardingUiState(
    val step: Int = 0,
    // Screen 1 — About you
    val name: String = "",
    val useMetricUnits: Boolean = true,
    // Screen 2 — Your body
    val sex: BiologicalSex? = null,
    val birthDate: String? = null,           // ISO yyyy-MM-dd
    val heightInput: String = "",            // metric: raw cm
    val heightFeetInput: String = "",        // imperial: whole feet
    val heightInchesInput: String = "",      // imperial: inches remainder (0..<12)
    // Screen 3 — Goal & measurements
    val goal: FitnessGoal? = null,
    val activityLevel: ActivityLevel? = null,
    val weightInput: String = "",            // raw, interpreted per useMetricUnits
    val waistInput: String = "",             // raw, optional
    // Screen 4 — Plan reveal
    val generatedPlan: GeneratedPlan? = null,
    val adjusting: Boolean = false,
    val adjCalories: String = "",
    val adjProtein: String = "",
    val adjCarbs: String = "",
    val adjFat: String = "",
    // cross-cutting
    val canContinue: Boolean = true,
    val message: String? = null,
    val finished: Boolean = false,
)

class OnboardingViewModel(
    private val userProfileStore: UserProfilePreferencesStore,
    private val planRepository: PlanRepository,
    private val logRepository: LogRepository,
    private val uiPreferences: UiPreferences,
    private val dateProvider: DateProvider,
    private val planGenerator: PlanGenerator = PlanGenerator(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    // --- field setters (synchronous draft updates; nothing persists until finish) ---

    fun setName(value: String) = set { copy(name = value) }

    fun setUnits(metric: Boolean) = set {
        if (metric == useMetricUnits) this
        // Units are chosen before any measurement is entered; if the user goes back and flips them,
        // clear the metric-ambiguous fields so they are re-entered in the new unit.
        else copy(
            useMetricUnits = metric,
            heightInput = "", heightFeetInput = "", heightInchesInput = "",
            weightInput = "", waistInput = "",
        )
    }

    fun setSex(value: BiologicalSex) = set { copy(sex = value) }
    fun setBirthDate(iso: String) = set { copy(birthDate = iso) }
    fun setHeight(value: String) = set { copy(heightInput = value.filter { it == '.' || it.isDigit() }.take(5)) }
    fun setHeightFeet(value: String) = set { copy(heightFeetInput = value.filter { it.isDigit() }.take(1)) }
    fun setHeightInches(value: String) = set { copy(heightInchesInput = value.filter { it == '.' || it.isDigit() }.take(4)) }

    fun setGoal(value: FitnessGoal) = set { copy(goal = value) }
    fun setActivityLevel(value: ActivityLevel) = set { copy(activityLevel = value) }
    fun setWeight(value: String) = set { copy(weightInput = value.filter { it == '.' || it.isDigit() }.take(6)) }
    fun setWaist(value: String) = set { copy(waistInput = value.filter { it == '.' || it.isDigit() }.take(6)) }

    // --- plan-reveal adjust mode ---

    fun startAdjusting() = set { copy(adjusting = true) }
    fun stopAdjusting() = set { copy(adjusting = false) }
    fun setAdjustedCalories(v: String) = set { copy(adjCalories = v.filter { it.isDigit() }.take(5)) }
    fun setAdjustedProtein(v: String) = set { copy(adjProtein = v.filter { it.isDigit() }.take(4)) }
    fun setAdjustedCarbs(v: String) = set { copy(adjCarbs = v.filter { it.isDigit() }.take(4)) }
    fun setAdjustedFat(v: String) = set { copy(adjFat = v.filter { it.isDigit() }.take(4)) }

    // --- navigation ---

    fun back() = set { if (step > 0) copy(step = step - 1, message = null) else this }

    fun next() {
        val s = _uiState.value
        if (!s.canContinue) return
        when (s.step) {
            0, 1 -> set { copy(step = step + 1) }
            2 -> generatePlan()
            else -> Unit
        }
    }

    private fun generatePlan() {
        val s = _uiState.value
        val weightKg = parseWeightKg(s.weightInput, s.useMetricUnits) ?: return
        viewModelScope.launch {
            when (val outcome = planGenerator.generate(buildDraftProfile(s), weightKg, dateProvider.today())) {
                is PlanGenerationOutcome.Ready -> set {
                    copy(
                        step = 3,
                        generatedPlan = outcome.plan,
                        message = null,
                        adjCalories = outcome.plan.targetCalories.toString(),
                        adjProtein = outcome.plan.proteinG.toString(),
                        adjCarbs = outcome.plan.carbsG.toString(),
                        adjFat = outcome.plan.fatG.toString(),
                    )
                }
                is PlanGenerationOutcome.NeedsWeight ->
                    set { copy(message = "Enter your current weight to continue.") }
                is PlanGenerationOutcome.MissingProfileFields ->
                    set { copy(message = "Missing: ${outcome.fields.joinToString(", ")}") }
            }
        }
    }

    // --- finish: single write point ---

    fun finish() {
        val s = _uiState.value
        val plan = s.generatedPlan ?: return
        val weightKg = parseWeightKg(s.weightInput, s.useMetricUnits) ?: return
        val waistCm = parseWaistCm(s.waistInput, s.useMetricUnits)
        viewModelScope.launch {
            userProfileStore.save(buildDraftProfile(s))
            val base = planRepository.preferences.first()
            // Clamp a hand-edited calorie target to a sane range; withCalorieTarget recomputes the
            // zone AROUND it (the canonical helper) so the zone never stays pinned to the original
            // plan's target after an adjustment (P1-14).
            val adjustedCalories = (s.adjCalories.toIntOrNull() ?: plan.targetCalories)
                .coerceIn(MIN_CALORIE_TARGET, MAX_CALORIE_TARGET)
            planRepository.save(
                base.copy(
                    targetProteinG = s.adjProtein.toIntOrNull() ?: plan.proteinG,
                    targetCarbsG = s.adjCarbs.toIntOrNull() ?: plan.carbsG,
                    targetFatG = s.adjFat.toIntOrNull() ?: plan.fatG,
                    useMetricUnits = s.useMetricUnits,
                ).withCalorieTarget(adjustedCalories),
            )
            // First run by definition: today's row does not exist yet, so an upsert is safe.
            logRepository.saveDailyMetrics(
                DailyMetricsInput(
                    date = dateProvider.today(),
                    bodyWeightKg = weightKg,
                    waistCm = waistCm,
                    steps = null,
                    sleepHours = null,
                    energyScore = null,
                    hungerScore = null,
                    sorenessScore = null,
                    trained = false,
                    notes = "",
                ),
            )
            uiPreferences.setOnboardingComplete(true)
            set { copy(finished = true) }
        }
    }

    // --- internals ---

    private fun buildDraftProfile(s: OnboardingUiState) = UserProfilePreferences(
        name = s.name.trim().ifBlank { null },
        heightCm = resolveHeightCm(s),
        birthDate = s.birthDate,
        biologicalSex = s.sex,
        activityLevel = s.activityLevel,
        goal = s.goal,
    )

    private fun computeCanContinue(s: OnboardingUiState): Boolean = when (s.step) {
        0 -> true
        1 -> s.sex != null &&
            isValidBirthDate(s.birthDate, dateProvider.today()) &&
            resolveHeightCm(s) != null
        2 -> s.goal != null && s.activityLevel != null &&
            (parseWeightKg(s.weightInput, s.useMetricUnits) ?: 0.0) > 0.0
        else -> true
    }

    private inline fun set(transform: OnboardingUiState.() -> OnboardingUiState) {
        _uiState.update { current ->
            val next = current.transform()
            next.copy(canContinue = computeCanContinue(next))
        }
    }
}
