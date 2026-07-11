package com.zack.recomptracker.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

@Composable
fun SharedRoutineImportScreen(
    viewModel: SharedRoutineImportViewModel,
    onClose: () -> Unit,
    onImported: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appColors = LocalAppColors.current

    // Navigate away once the routine is saved.
    LaunchedEffect(state) {
        (state as? ImportUiState.Saved)?.let { onImported(it.workoutId) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SubScreenHeader(title = "Shared routine", onBack = onClose) }

        when (val s = state) {
            is ImportUiState.Loading -> item {
                Text("Opening routine…", style = AppType.body, color = appColors.textMuted)
            }

            is ImportUiState.Error -> item {
                FrostedCard(contentPadding = 16.dp) {
                    Text(s.message, style = AppType.body, color = appColors.textPrimary)
                    Spacer(Modifier.height(12.dp))
                    LiquidSecondaryButton(text = "Close", onClick = onClose)
                }
            }

            is ImportUiState.Preview -> {
                item {
                    FrostedCard(contentPadding = 16.dp) {
                        Text(s.name, style = AppType.cardTitle, color = appColors.textPrimary)
                        s.note?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = AppType.cardSubtitle, color = appColors.textMuted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${s.exercises.size} exercises",
                            style = AppType.label,
                            color = appColors.textMuted,
                        )
                    }
                }
                item { SectionLabel("Exercises") }
                items(s.exercises) { row -> ImportExerciseRowView(row) }
                item {
                    Spacer(Modifier.height(4.dp))
                    LiquidPrimaryButton(
                        text = if (s.saving) "Saving…" else "Save to my routines",
                        onClick = { viewModel.save() },
                    )
                }
            }

            is ImportUiState.Saved -> item {
                Text("Saved.", style = AppType.body, color = appColors.textMuted)
            }
        }
    }
}

@Composable
private fun ImportExerciseRowView(row: ImportExerciseRow) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    FrostedCard(contentPadding = 13.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = AppType.body,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.willCreate) {
                    Text(
                        "New — will be added to your library",
                        style = AppType.metaLabel,
                        color = accent.inkLight,
                    )
                }
            }
            Text("${row.setCount} sets", style = AppType.label, color = appColors.textMuted)
        }
    }
}
