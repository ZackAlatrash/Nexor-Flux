package com.zack.recomptracker.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zack.recomptracker.ai.WeeklyBriefing

@Composable
fun WeeklyBriefingOverlay(
    state: WeeklyReviewUiState,
    pendingApply: Int?,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit,
    onRequestApply: (Int) -> Unit,
    onConfirmApply: () -> Unit,
    onCancelApply: () -> Unit,
    onDiscussWithCoach: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (state is WeeklyReviewUiState.Hidden) return

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Weekly Review", style = MaterialTheme.typography.titleLarge)
                when (state) {
                    WeeklyReviewUiState.Hidden -> Unit
                    WeeklyReviewUiState.Upsell -> {
                        Text("Weekly Review is powered by cloud AI. Turn on cloud AI in Settings to unlock your weekly briefing.")
                        Button(onClick = onOpenSettings) { Text("Open Settings") }
                    }
                    is WeeklyReviewUiState.InsufficientData -> {
                        Text("Building your first review")
                        Text("Keep logging — about ${state.daysRemaining} more day(s) of data and your first briefing unlocks.")
                    }
                    WeeklyReviewUiState.Generating -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("  Reading your week…")
                        }
                    }
                    is WeeklyReviewUiState.Error -> {
                        Text(state.message)
                        Button(onClick = onRegenerate) { Text("Try again") }
                    }
                    is WeeklyReviewUiState.Ready -> BriefingBody(
                        briefing = state.briefing,
                        onRequestApply = onRequestApply,
                        onRegenerate = onRegenerate,
                        onDiscussWithCoach = onDiscussWithCoach,
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }

    if (pendingApply != null) {
        AlertDialog(
            onDismissRequest = onCancelApply,
            title = { Text("Update calorie target?") },
            text = { Text("Set your daily target to $pendingApply kcal?") },
            confirmButton = { TextButton(onClick = onConfirmApply) { Text("Apply") } },
            dismissButton = { TextButton(onClick = onCancelApply) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BriefingBody(
    briefing: WeeklyBriefing,
    onRequestApply: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onDiscussWithCoach: () -> Unit,
) {
    Text(briefing.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (briefing.narrative.isNotBlank()) Text(briefing.narrative)
    HorizontalDivider()
    briefing.signals.forEach { s ->
        Column {
            Text("${s.label}: ${s.value}", fontWeight = FontWeight.Medium)
            if (s.interpretation.isNotBlank()) {
                Text(s.interpretation, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    HorizontalDivider()
    Text(briefing.action.verdict, fontWeight = FontWeight.SemiBold)
    if (briefing.action.rationale.isNotBlank()) Text(briefing.action.rationale)
    val applyTarget = briefing.action.applyTargetCalories
    if (applyTarget != null) {
        Button(onClick = { onRequestApply(applyTarget) }) { Text("Apply: set target to $applyTarget kcal") }
    }
    if (briefing.watchNext.isNotBlank()) {
        HorizontalDivider()
        Text("Watch next week", fontWeight = FontWeight.Medium)
        Text(briefing.watchNext)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onRegenerate) { Text("Regenerate") }
        TextButton(onClick = onDiscussWithCoach) { Text("Discuss with coach") }
    }
}
