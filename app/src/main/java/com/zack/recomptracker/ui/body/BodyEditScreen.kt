package com.zack.recomptracker.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.ScreenScaffold
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.SubScreenHeader
import java.time.format.DateTimeFormatter

private val HEADER_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val CARD_FMT = DateTimeFormatter.ofPattern("MMM d")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyEditScreen(
    viewModel: BodyEditViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onBack() }
    }

    val formState = BodyCheckInFormState(
        date = state.date,
        bodyWeightKg = state.bodyWeightKg,
        waistCm = state.waistCm,
        waistSkinfoldMm = state.waistSkinfoldMm,
        steps = state.steps,
        sleepHours = state.sleepHours,
        energyScore = state.energyScore,
        hungerScore = state.hungerScore,
        sorenessScore = state.sorenessScore,
        trained = state.trained,
        notes = state.notes,
        message = state.message,
    )
    val formActions = BodyCheckInFormActions(
        onBodyWeightChanged = viewModel::onBodyWeightChanged,
        onWaistChanged = viewModel::onWaistChanged,
        onWaistSkinfoldChanged = viewModel::onWaistSkinfoldChanged,
        onStepsChanged = viewModel::onStepsChanged,
        onSleepChanged = viewModel::onSleepChanged,
        onEnergyChanged = viewModel::onEnergyChanged,
        onHungerChanged = viewModel::onHungerChanged,
        onSorenessChanged = viewModel::onSorenessChanged,
        onTrainedChanged = viewModel::onTrainedChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onSave = viewModel::saveMetrics,
    )

    ScreenScaffold(withNavBarInset = false) {
        item {
            SubScreenHeader(
                title = state.date.format(HEADER_FMT),
                subtitle = "Past check-in",
                onBack = onBack,
            )
        }
        item {
            FrostedCard {
                SectionLabel("Check-in for ${state.date.format(CARD_FMT)}")
                BodyCheckInFormContent(
                    state = formState,
                    actions = formActions,
                    saveLabel = "Save check-in for ${state.date.format(CARD_FMT)}",
                )
            }
        }
    }
}
