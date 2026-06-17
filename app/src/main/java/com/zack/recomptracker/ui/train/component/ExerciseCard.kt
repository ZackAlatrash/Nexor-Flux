package com.zack.recomptracker.ui.train.component

import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.ErrorRed
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
 * @param modifier           Applied to the outer [FrostedCard].
 * @param content            Inner content slot (a [SetGrid] in the expected mode).
 */
@Composable
fun ExerciseCard(
    exerciseName: String,
    imageUrl: String?,
    subtitle: String,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val appColors = LocalAppColors.current
    var menuOpen by remember { mutableStateOf(false) }

    FrostedCard(
        modifier = modifier,
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
                modifier = Modifier.size(18.dp),
            )

            Spacer(Modifier.width(10.dp))

            // Thumbnail
            val resolvedUrl = imageUrl?.let { exerciseImageUrl(it) }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(CornerSmall))
                    .background(appColors.cardSurface),
                contentAlignment = Alignment.Center,
            ) {
                if (resolvedUrl != null) {
                    AsyncImage(
                        model = resolvedUrl,
                        contentDescription = exerciseName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = appColors.textMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Name + subtitle
            Column(
                modifier = Modifier.weight(1f),
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
