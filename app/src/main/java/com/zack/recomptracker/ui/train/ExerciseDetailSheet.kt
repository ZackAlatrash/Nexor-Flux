package com.zack.recomptracker.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.theme.CornerChip
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailSheet(
    exercise: Exercise,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Header row ──────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = exercise.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.textPrimary,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = appColors.textMuted,
                        )
                    }
                }
            }

            // ── Hero image ──────────────────────────────────────────────────────
            item {
                val imageUrl = exercise.images.firstOrNull()
                val baseUrl = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
                val resolvedUrl = imageUrl?.let { if (it.startsWith("http")) it else "$baseUrl$it" }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(CornerSmall))
                        .background(appColors.cardSurface),
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
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }

            // ── Meta chips (category / equipment / level) ───────────────────────
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOfNotNull(exercise.category, exercise.equipment, exercise.level).forEach { label ->
                        Text(
                            text = label.replaceFirstChar { it.uppercase() },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = appColors.textMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(CornerChip))
                                .background(appColors.cardSurface)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // ── Primary muscles ─────────────────────────────────────────────────
            if (exercise.primaryMuscles.isNotEmpty()) {
                item {
                    FrostedCard(contentPadding = 12.dp) {
                        Text(
                            text = "Primary Muscles",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent.inkLight,
                            letterSpacing = 0.6.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            exercise.primaryMuscles.forEach { muscle ->
                                Text(
                                    text = muscle.replaceFirstChar { it.uppercase() },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = accent.onAccent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(CornerChip))
                                        .background(accent.accent)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Secondary muscles ───────────────────────────────────────────────
            if (exercise.secondaryMuscles.isNotEmpty()) {
                item {
                    FrostedCard(contentPadding = 12.dp) {
                        Text(
                            text = "Secondary Muscles",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = appColors.textMuted,
                            letterSpacing = 0.6.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            exercise.secondaryMuscles.forEach { muscle ->
                                Text(
                                    text = muscle.replaceFirstChar { it.uppercase() },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = appColors.textPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(CornerChip))
                                        .background(appColors.cardSurface)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Instructions ────────────────────────────────────────────────────
            if (exercise.instructions.isNotEmpty()) {
                item {
                    Text(
                        text = "Instructions",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.textPrimary,
                    )
                }
                itemsIndexed(exercise.instructions) { index, step ->
                    FrostedCard(contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(CornerChip))
                                    .background(accent.tintedSurface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent.inkLight,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = step,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = appColors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
