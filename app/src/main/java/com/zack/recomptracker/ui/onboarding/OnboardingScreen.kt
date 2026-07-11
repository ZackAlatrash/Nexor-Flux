package com.zack.recomptracker.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val STEP_TITLES = listOf("About you", "Your body", "Goal", "Your plan")

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 32.dp, bottom = 24.dp)) {
            // --- shared frame: step counter + progress bar ---
            Text(
                text = "STEP ${state.step + 1} OF $ONBOARDING_STEPS · ${STEP_TITLES[state.step].uppercase()}",
                style = AppType.metaLabel,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            ProgressBar(fraction = (state.step + 1).toFloat() / ONBOARDING_STEPS)
            Spacer(Modifier.height(20.dp))

            // --- step body ---
            Box(modifier = Modifier.weight(1f)) {
                when (state.step) {
                    0 -> AboutYouStep(state, viewModel)
                    1 -> YourBodyStep(state, viewModel)
                    2 -> GoalStep(state, viewModel)
                    else -> PlanStep(state, viewModel)
                }
            }

            state.message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = AppType.body,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            // --- bottom CTA ---
            if (state.step < 3) {
                LiquidPrimaryButton(
                    text = if (state.step == 2) "See my plan" else "Continue",
                    onClick = viewModel::next,
                    enabled = state.canContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LiquidSecondaryButton(
                    text = if (state.adjusting) "Done adjusting" else "Adjust targets",
                    onClick = { if (state.adjusting) viewModel.stopAdjusting() else viewModel.startAdjusting() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                LiquidPrimaryButton(
                    text = "Start tracking",
                    onClick = viewModel::finish,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(appColors.textPrimary.copy(alpha = 0.10f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun AboutYouStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    Column {
        StepHeader("Let's get you set up", "First, the basics. This takes about a minute.")
        FrostedCard {
            GlassInputField(
                label = "Your name",
                value = state.name,
                onValueChange = vm::setName,
                keyboardType = KeyboardType.Text,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            SectionLabel("Units")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PillToggle("Metric (kg, cm)", state.useMetricUnits, Modifier.weight(1f)) { vm.setUnits(true) }
                PillToggle("Imperial", !state.useMetricUnits, Modifier.weight(1f)) { vm.setUnits(false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YourBodyStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    Column {
        StepHeader("A few body basics", "Used to estimate your daily energy needs.")
        FrostedCard {
            SectionLabel("Biological sex")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PillToggle("Male", state.sex == BiologicalSex.MALE, Modifier.weight(1f)) { vm.setSex(BiologicalSex.MALE) }
                PillToggle("Female", state.sex == BiologicalSex.FEMALE, Modifier.weight(1f)) { vm.setSex(BiologicalSex.FEMALE) }
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("Date of birth")
            PickerRow(value = state.birthDate ?: "Select date") { showDatePicker = true }
            Spacer(Modifier.height(16.dp))
            if (state.useMetricUnits) {
                GlassInputField(
                    label = "Height",
                    value = state.heightInput,
                    onValueChange = vm::setHeight,
                    unit = "cm",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Split feet + inches so a US user can't type "5.9" (5′9″) into one inches field and
                // silently get 15 cm (P1-14).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GlassInputField(
                        label = "Height",
                        value = state.heightFeetInput,
                        onValueChange = vm::setHeightFeet,
                        unit = "ft",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    GlassInputField(
                        label = "Inches",
                        value = state.heightInchesInput,
                        onValueChange = vm::setHeightInches,
                        unit = "in",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (showDatePicker) {
        val initialMillis = state.birthDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        vm.setBirthDate(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString(),
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun GoalStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    var sheet by remember { mutableStateOf<String?>(null) } // "goal" | "activity" | null
    Column {
        StepHeader("Your goal & starting point", "What you want, and where you're at today.")
        FrostedCard {
            SectionLabel("Goal")
            PickerRow(value = state.goal?.let { goalLabel(it) } ?: "Select goal") { sheet = "goal" }
            Spacer(Modifier.height(14.dp))
            SectionLabel("Activity level")
            PickerRow(value = state.activityLevel?.let { activityLabel(it) } ?: "Select activity") { sheet = "activity" }
            Spacer(Modifier.height(14.dp))
            GlassInputField(
                label = "Current weight",
                value = state.weightInput,
                onValueChange = vm::setWeight,
                unit = if (state.useMetricUnits) "kg" else "lb",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            GlassInputField(
                label = "Waist · optional",
                value = state.waistInput,
                onValueChange = vm::setWaist,
                unit = if (state.useMetricUnits) "cm" else "in",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    when (sheet) {
        "goal" -> OptionSheet(
            title = "Goal",
            options = FitnessGoal.entries.map { it to goalLabel(it) },
            selected = state.goal,
            onDismiss = { sheet = null },
        ) { vm.setGoal(it); sheet = null }
        "activity" -> OptionSheet(
            title = "Activity level",
            options = ActivityLevel.entries.map { it to activityLabel(it) },
            selected = state.activityLevel,
            onDismiss = { sheet = null },
        ) { vm.setActivityLevel(it); sheet = null }
    }
}

@Composable
private fun PlanStep(state: OnboardingUiState, vm: OnboardingViewModel) {
    val plan = state.generatedPlan
    val appColors = LocalAppColors.current
    Column {
        StepHeader("Here's your plan", "Tailored to your goal. You can fine-tune anytime.")
        if (plan != null) {
            FrostedCard {
                if (state.adjusting) {
                    GlassInputField("Calories", state.adjCalories, vm::setAdjustedCalories, unit = "kcal", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    GlassInputField("Protein", state.adjProtein, vm::setAdjustedProtein, unit = "g", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    GlassInputField("Carbs", state.adjCarbs, vm::setAdjustedCarbs, unit = "g", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    GlassInputField("Fat", state.adjFat, vm::setAdjustedFat, unit = "g", keyboardType = KeyboardType.Number, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        text = state.adjCalories.ifBlank { plan.targetCalories.toString() },
                        style = AppType.displayHero,
                        color = appColors.textPrimary,
                    )
                    Text("kcal / day", fontSize = 12.sp, color = appColors.textMuted, letterSpacing = 2.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MacroTile("Protein", state.adjProtein.ifBlank { plan.proteinG.toString() }, Modifier.weight(1f))
                        MacroTile("Carbs", state.adjCarbs.ifBlank { plan.carbsG.toString() }, Modifier.weight(1f))
                        MacroTile("Fat", state.adjFat.ifBlank { plan.fatG.toString() }, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Calculated with Mifflin–St Jeor from your profile", fontSize = 10.sp, color = appColors.textMuted)
        }
    }
}

// --- small local building blocks ---

@Composable
private fun StepHeader(title: String, subtitle: String) {
    val appColors = LocalAppColors.current
    Column {
        Text(title, style = AppType.screenTitleCompact, color = appColors.textPrimary)
        Text(subtitle, style = AppType.screenSubtitle, color = appColors.textMuted, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
    }
}

@Composable
private fun PillToggle(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else appColors.textPrimary.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) accent else appColors.textMuted)
    }
}

@Composable
private fun PickerRow(value: String, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, style = AppType.cardTitle, color = appColors.textPrimary)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = appColors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MacroTile(label: String, value: String, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.textPrimary.copy(alpha = 0.05f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${value}g", style = AppType.statValueSmall, color = appColors.textPrimary)
        Text(label.uppercase(), style = AppType.metaLabel, color = appColors.textMuted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = appColors.textPrimary, modifier = Modifier.padding(vertical = 8.dp))
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(value) }.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, fontSize = 15.sp, color = appColors.textPrimary, fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal)
                    if (value == selected) Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun goalLabel(g: FitnessGoal): String = when (g) {
    FitnessGoal.AGGRESSIVE_CUT -> "Aggressive cut"
    FitnessGoal.MODERATE_CUT -> "Moderate cut"
    FitnessGoal.MINI_CUT -> "Mini cut"
    FitnessGoal.RECOMP -> "Recomp"
    FitnessGoal.LEAN_BULK -> "Lean bulk"
    FitnessGoal.MODERATE_BULK -> "Moderate bulk"
    FitnessGoal.AGGRESSIVE_BULK -> "Aggressive bulk"
}

private fun activityLabel(a: ActivityLevel): String = when (a) {
    ActivityLevel.SEDENTARY -> "Sedentary"
    ActivityLevel.LIGHTLY_ACTIVE -> "Lightly active"
    ActivityLevel.MODERATELY_ACTIVE -> "Moderately active"
    ActivityLevel.VERY_ACTIVE -> "Very active"
}
