package com.zack.recomptracker.ui.train.component

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

private const val BASE_IMAGE_URL =
    "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

/**
 * Resolves an exercise image path to its full URL.
 * Absolute URLs are returned as-is; relative paths are prefixed with the free-exercise-db CDN.
 */
fun exerciseImageUrl(path: String): String =
    if (path.startsWith("http")) path else "$BASE_IMAGE_URL$path"

/**
 * Reusable exercise card — wraps a [FrostedCard] with a drag handle, thumbnail,
 * exercise name + muscle/equipment subtitle, an overflow menu, and a content slot.
 *
 * Used by:
 *  - Routine Builder (content = [SetGrid] in PLAN mode)
 *  - Active Session  (content = [SetGrid] in SESSION mode) — later
 *  - Session Detail  (content = [SetGrid] in READONLY mode) — later
 *
 * @param exerciseName       Display name.
 * @param imageUrl           Optional raw image path (before URL resolution). Null → icon fallback.
 * @param subtitle           Muscle · equipment text shown below the name.
 * @param onMoveUp           Move this exercise one position earlier; null to hide the option.
 * @param onMoveDown         Move this exercise one position later; null to hide the option.
 * @param onRemove           Remove this exercise from the list.
 * @param onReplace          Replace this exercise (session only); null to hide the option.
 * @param onShowDetails      Tap the thumbnail or name to open exercise details; null to disable.
 * @param modifier           Applied to the outer [FrostedCard].
 * @param content            Inner content slot (a [SetGrid] in the expected mode).
 */
@Composable
fun ExerciseCard(
    exerciseName: String,
    imageUrl: String?,
    fallbackMuscles: List<String>? = null,
    subtitle: String,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
    onReplace: (() -> Unit)? = null,
    onShowDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    isDragging: Boolean = false,
    content: @Composable () -> Unit,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    var menuOpen by remember { mutableStateOf(false) }

    FrostedCard(
        modifier = modifier.graphicsLayer {
            val s = if (isDragging) 1.02f else 1f
            scaleX = s
            scaleY = s
            shadowElevation = if (isDragging) 16f else 0f
            shape = RoundedCornerShape(20.dp)
            clip = false
        },
        contentPadding = 12.dp,
    ) {
        // ── Header row ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drag handle (visual affordance; up/down via ⋮ menu for simplicity)
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = appColors.textMuted.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp).then(dragHandleModifier),
            )

            Spacer(Modifier.width(10.dp))

            // Thumbnail
            val resolvedUrl = imageUrl?.takeIf { it.isNotBlank() }?.let { exerciseImageUrl(it) }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(CornerSmall))
                    .background(appColors.cardSurface)
                    .then(
                        if (onShowDetails != null) {
                            Modifier.clickable(onClickLabel = "View exercise details") { onShowDetails() }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (resolvedUrl != null) {
                    SubcomposeAsyncImage(
                        model = resolvedUrl,
                        contentDescription = exerciseName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { ExerciseThumbFallback(fallbackMuscles, appColors.textMuted, accent.accentLighter) },
                        error = { ExerciseThumbFallback(fallbackMuscles, appColors.textMuted, accent.accentLighter) },
                    )
                } else {
                    ExerciseThumbFallback(fallbackMuscles, appColors.textMuted, accent.accentLighter)
                }
            }

            Spacer(Modifier.width(10.dp))

            // Name + subtitle
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(CornerSmall))
                    .then(
                        if (onShowDetails != null) {
                            Modifier.clickable(onClickLabel = "View exercise details") { onShowDetails() }
                        } else Modifier
                    )
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = exerciseName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = appColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Overflow menu
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Exercise options",
                        tint = appColors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    if (onMoveUp != null) {
                        DropdownMenuItem(
                            text = { Text("Move up") },
                            onClick = {
                                menuOpen = false
                                onMoveUp()
                            },
                        )
                    }
                    if (onMoveDown != null) {
                        DropdownMenuItem(
                            text = { Text("Move down") },
                            onClick = {
                                menuOpen = false
                                onMoveDown()
                            },
                        )
                    }
                    if (onReplace != null) {
                        DropdownMenuItem(
                            text = { Text("Replace exercise") },
                            onClick = {
                                menuOpen = false
                                onReplace()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove", color = ErrorRed) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }

        // ── Divider ───────────────────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))

        // ── Content slot (SetGrid) ────────────────────────────────────────────
        content()
    }
}

@Composable
private fun ExerciseThumbFallback(
    fallbackMuscles: List<String>?,
    dumbbellTint: Color,
    accentTint: Color,
) {
    if (fallbackMuscles != null) {
        MuscleGroupIcon(
            primaryMuscles = fallbackMuscles,
            tint = accentTint,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = null,
            tint = dumbbellTint,
            modifier = Modifier.size(22.dp),
        )
    }
}
