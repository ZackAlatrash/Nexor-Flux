package com.zack.recomptracker.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.component.GlassBottomSheet
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.GlassTextArea
import com.zack.recomptracker.ui.component.ScoreStepper
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.VioletToggle
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppColors
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Sections for initialSection parameter
object CheckInSection {
    const val MEASUREMENTS = 0
    const val RECOVERY = 1
    const val ACTIVITY = 2
}

/**
 * Reusable daily check-in bottom sheet. Pass [onDismiss] to handle both
 * drag-to-close and post-save dismissal from the caller.
 *
 * [initialSection] scrolls to a specific section on open:
 *   CheckInSection.MEASUREMENTS (0), RECOVERY (1), or ACTIVITY (2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyCheckInSheet(
    state: BodyCheckInFormState,
    actions: BodyCheckInFormActions,
    onDismiss: () -> Unit,
    initialSection: Int = CheckInSection.MEASUREMENTS,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    GlassBottomSheet(onDismiss = onDismiss, sheetState = sheetState) {
        BodyCheckInSheetContent(
            state = state,
            actions = actions.copy(onSave = {
                actions.onSave()
                onDismiss()
            }),
            initialSection = initialSection,
        )
    }
}

/**
 * The inner content of the check-in sheet. Use this when you need to embed
 * the form inside a custom container instead of a ModalBottomSheet.
 */
@Composable
fun BodyCheckInSheetContent(
    state: BodyCheckInFormState,
    actions: BodyCheckInFormActions,
    initialSection: Int = CheckInSection.MEASUREMENTS,
) {
    val appColors = LocalAppColors.current
    val scrollState = rememberScrollState()
    val dateStr = remember(state.date) { state.date.format(DateTimeFormatter.ofPattern("MMM d")) }

    // Content offsets of the scroll-target sections, captured once at first layout (scroll == 0,
    // so positionInParent() is the stable content offset). Used to open the sheet at a section.
    var recoveryOffset by remember { mutableIntStateOf(0) }
    var activityOffset by remember { mutableIntStateOf(0) }
    var didInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(initialSection, recoveryOffset, activityOffset) {
        if (didInitialScroll) return@LaunchedEffect
        when (initialSection) {
            CheckInSection.RECOVERY -> if (recoveryOffset > 0) {
                scrollState.animateScrollTo(recoveryOffset); didInitialScroll = true
            }
            CheckInSection.ACTIVITY -> if (activityOffset > 0) {
                scrollState.animateScrollTo(activityOffset); didInitialScroll = true
            }
            else -> didInitialScroll = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Today's check-in",
                style = AppType.screenTitleCompact,
                color = appColors.textPrimary,
            )
            Text(text = dateStr, style = AppType.cardSubtitle, color = appColors.textMuted)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Measurements")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GlassInputField("Weight", state.bodyWeightKg, actions.onBodyWeightChanged, unit = "kg", modifier = Modifier.weight(1f))
                GlassInputField("Waist", state.waistCm, actions.onWaistChanged, unit = "cm", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GlassInputField("Sleep", state.sleepHours, actions.onSleepChanged, unit = "hrs", modifier = Modifier.weight(1f))
                GlassInputField("Steps", state.steps, actions.onStepsChanged, keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
            }
            GlassInputField("Skinfold", state.waistSkinfoldMm, actions.onWaistSkinfoldChanged, unit = "mm")
        }

        SheetDivider(appColors.cardBorder)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { if (recoveryOffset == 0) recoveryOffset = it.positionInParent().y.roundToInt() }
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Recovery")
            ScoreStepper("Energy", state.energyScore, actions.onEnergyChanged)
            ScoreStepper("Hunger", state.hungerScore, actions.onHungerChanged)
            ScoreStepper("Soreness", state.sorenessScore, actions.onSorenessChanged)
        }

        SheetDivider(appColors.cardBorder)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { if (activityOffset == 0) activityOffset = it.positionInParent().y.roundToInt() }
                .padding(top = 14.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Activity")
            VioletToggle("Training day", state.trained, actions.onTrainedChanged)
            GlassTextArea(state.notes, actions.onNotesChanged, placeholder = "Notes...", minLines = 2)
            LiquidPrimaryButton(text = "Save check-in", onClick = actions.onSave)
        }
    }
}

@Composable
private fun SheetDivider(color: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(color))
}
