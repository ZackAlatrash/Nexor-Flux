package com.zack.recomptracker.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.CornerChip
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.train.component.exerciseImageUrl
import kotlinx.coroutines.launch

@Composable
fun ExercisePickerScreen(
    viewModel: ExercisePickerViewModel,
    onClose: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    /** When true the picker is single-select: tapping an exercise immediately calls [onReplacePick]
     *  (the "Add N" button is hidden). Used to replace one exercise in an active session. */
    replaceMode: Boolean = false,
    onReplacePick: (Long) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var showDetailFor by remember { mutableStateOf<Exercise?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Sticky "Add N" button height for bottom padding
    val stickyButtonHeight = 72.dp

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = stickyButtonHeight + 16.dp),
        ) {
            // ── Header: ✕ + title ───────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = appColors.textPrimary,
                        )
                    }
                    Text(
                        text = if (replaceMode) "Replace exercise" else "Add exercises",
                        style = AppType.screenTitleCompact,
                        color = appColors.textPrimary,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                }
            }

            // ── Search field ────────────────────────────────────────────────────
            item {
                FrostedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                    contentPadding = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = appColors.textMuted,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = state.query,
                            onValueChange = { viewModel.setQuery(it) },
                            modifier = Modifier.weight(1f),
                            textStyle = AppType.body.copy(color = appColors.textPrimary),
                            cursorBrush = SolidColor(accent.accent),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Search,
                            ),
                            singleLine = true,
                            decorationBox = { inner ->
                                Box {
                                    if (state.query.isEmpty()) {
                                        Text(
                                            "Search exercises…",
                                            style = AppType.body,
                                            color = appColors.textMuted,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        if (state.query.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.setQuery("") },
                                modifier = Modifier.size(18.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = appColors.textMuted,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Muscle filter chips ─────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MUSCLE_FILTER_CHIPS.forEach { chip ->
                        val isActive = state.muscleFilter == chip
                        Text(
                            text = chip,
                            style = AppType.label.copy(fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal),
                            color = if (isActive) accent.onAccent else appColors.textMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(CornerChip))
                                .background(if (isActive) accent.accent else appColors.cardSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) accent.accent else appColors.cardSurface,
                                    shape = RoundedCornerShape(CornerChip),
                                )
                                .clickable { viewModel.setFilter(chip) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            // ── Create custom exercise row ──────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(CornerCard))
                        .clickable { showCreateDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(CornerSmall))
                            .background(accent.tintedSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = accent.inkLight,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Create custom exercise",
                        style = AppType.cardTitle,
                        color = accent.inkLight,
                    )
                }
            }

            // ── Exercise list OR empty state ────────────────────────────────────
            if (!state.isLoading && state.results.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = appColors.textMuted,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "No exercises match",
                            style = AppType.cardTitle,
                            color = appColors.textMuted,
                            textAlign = TextAlign.Center,
                        )
                        if (state.query.isNotEmpty() || state.muscleFilter != "All") {
                            Text(
                                text = "Clear filters",
                                style = AppType.body,
                                color = accent.inkLight,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(CornerChip))
                                    .clickable {
                                        viewModel.setQuery("")
                                        viewModel.setFilter("All")
                                    }
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            } else {
                items(state.results, key = { it.id }) { exercise ->
                    ExercisePickerRow(
                        exercise = exercise,
                        isSelected = exercise.id in state.selected,
                        onToggle = {
                            if (replaceMode) onReplacePick(exercise.id)
                            else viewModel.toggle(exercise.id)
                        },
                        onThumbnailClick = { showDetailFor = exercise },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // ── Sticky "Add N" button (add mode only — replace mode confirms on row tap) ──
        if (!replaceMode) {
            val count = state.selected.size
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Ride just above the keyboard when it's open; rests at the bottom when closed.
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                LiquidGlassButton(
                    onClick = { onConfirm(viewModel.selectedExercises()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = count > 0,
                    tint = accent.accent,
                    surfaceColor = Color.White.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = if (count == 0) "Add exercises" else "Add $count ${if (count == 1) "exercise" else "exercises"}",
                        style = AppType.cardTitle,
                        color = if (count > 0) accent.onAccent else appColors.textMuted,
                    )
                }
            }
        }
    }

    // ── Exercise detail sheet ───────────────────────────────────────────────────
    showDetailFor?.let { exercise ->
        ExerciseDetailSheet(
            exercise = exercise,
            onDismiss = { showDetailFor = null },
        )
    }

    // ── Create custom exercise dialog ───────────────────────────────────────────
    if (showCreateDialog) {
        var customName by remember { mutableStateOf("") }
        var customMuscle by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; customName = ""; customMuscle = null },
            title = { Text("Custom exercise", color = appColors.textPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Exercise name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Muscle group (optional)", style = AppType.label, color = appColors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        MUSCLE_FILTER_CHIPS.filter { it != "All" }.forEach { muscle ->
                            val active = customMuscle == muscle
                            FilterChip(
                                selected = active,
                                onClick = { customMuscle = if (active) null else muscle },
                                label = { Text(muscle) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = customName.trim()
                        if (name.isNotEmpty()) {
                            val muscles = listOfNotNull(customMuscle)
                            scope.launch {
                                val id = viewModel.createCustom(name, muscles)
                                if (replaceMode) onReplacePick(id)
                            }
                            showCreateDialog = false
                            customName = ""
                            customMuscle = null
                        }
                    },
                    enabled = customName.isNotBlank(),
                ) {
                    Text("Create", color = accent.inkLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; customName = ""; customMuscle = null }) {
                    Text("Cancel", color = appColors.textMuted)
                }
            },
            containerColor = appColors.frostedSurface,
        )
    }
}

@Composable
private fun ExercisePickerRow(
    exercise: Exercise,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onThumbnailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    FrostedCard(
        modifier = modifier.clickable { onToggle() },
        surfaceTint = if (isSelected) accent.tintedSurface else Color.Unspecified,
        borderColor = if (isSelected) accent.tintedBorder else Color.Unspecified,
        contentPadding = 10.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail — tapping opens detail
            val imageUrl = exercise.images.firstOrNull()
            val resolvedUrl = imageUrl?.let { exerciseImageUrl(it) }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(CornerSmall))
                    .background(appColors.cardSurface)
                    .clickable { onThumbnailClick() },
                contentAlignment = Alignment.Center,
            ) {
                if (resolvedUrl != null) {
                    AsyncImage(
                        model = resolvedUrl,
                        contentDescription = exercise.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = appColors.textMuted,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Name + muscle/equipment
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = AppType.cardTitle,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    exercise.primaryMuscles.firstOrNull()?.replaceFirstChar { it.uppercase() },
                    exercise.equipment?.replaceFirstChar { it.uppercase() },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = sub,
                        style = AppType.cardSubtitle,
                        color = appColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Select indicator
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accent.accent else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) accent.accent else appColors.textMuted,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = accent.onAccent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
