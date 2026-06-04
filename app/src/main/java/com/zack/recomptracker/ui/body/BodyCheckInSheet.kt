package com.zack.recomptracker.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.component.GlassInputField
import com.zack.recomptracker.ui.component.GlassTextArea
import com.zack.recomptracker.ui.component.ScoreStepper
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.VioletToggle
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.theme.TextMuted
import java.time.format.DateTimeFormatter

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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F0B1C),
    ) {
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
    val listState = rememberLazyListState()
    val dateStr = remember(state.date) { state.date.format(DateTimeFormatter.ofPattern("MMM d")) }

    // item layout: 0=header, 1=measurements, 2=divider, 3=recovery, 4=divider, 5=activity
    LaunchedEffect(initialSection) {
        val target = when (initialSection) {
            CheckInSection.RECOVERY -> 3
            CheckInSection.ACTIVITY -> 5
            else -> 0
        }
        if (target > 0) listState.animateScrollToItem(target)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today's check-in",
                    fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, letterSpacing = (-0.5).sp,
                )
                Text(text = dateStr, fontSize = 11.sp, color = TextMuted)
            }
        }

        item {
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
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x0DFFFFFF)).padding(bottom = 8.dp))
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel("Recovery")
                ScoreStepper("Energy", state.energyScore, actions.onEnergyChanged)
                ScoreStepper("Hunger", state.hungerScore, actions.onHungerChanged)
                ScoreStepper("Soreness", state.sorenessScore, actions.onSorenessChanged)
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x0DFFFFFF)).padding(bottom = 8.dp))
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel("Activity")
                VioletToggle("Training day", state.trained, actions.onTrainedChanged)
                GlassTextArea(state.notes, actions.onNotesChanged, placeholder = "Notes...", minLines = 2)
                LiquidPrimaryButton(text = "Save check-in", onClick = actions.onSave)
            }
        }
    }
}
