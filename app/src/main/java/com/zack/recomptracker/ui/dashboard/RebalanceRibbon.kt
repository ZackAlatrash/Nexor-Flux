package com.zack.recomptracker.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.component.rememberAnimationsEnabled
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * A tappable, accent-tinted strip that rides the Today card's own space, summarizing an in-progress
 * Weekly Rebalance plan and opening [RebalanceProgressDetailOverlay] on tap. It always leads with a
 * clear "WEEKLY REBALANCE" label + sparkle so the user can tell what it is at a glance (an earlier
 * version showed only bare day-dots and read as an unexplained graphic), then a compact day-X-of-Y
 * row (or "Starts tomorrow" on the accept-late day-0), and today's lever on the right.
 *
 * Renders its content unconditionally — this composable does NOT early-return on [state]'s face and
 * does NOT wrap itself in `AnimatedVisibility`. The `TodayCard` call site wraps it in
 * `AnimatedVisibility(visible = state.face == RebalanceCardUiState.Face.PROGRESS)` so the fade/expand
 * enter-exit can play across composition.
 *
 * Has its OWN `Modifier.clickable` (rather than relying on the Today card's Food-Log tap) so the
 * inner clickable consumes the tap first.
 */
@Composable
internal fun RebalanceRibbon(
    state: RebalanceCardUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    val animationsEnabled = rememberAnimationsEnabled()

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.7f else 1f,
        animationSpec = tween(if (animationsEnabled) 120 else 0),
        label = "ribbonPressAlpha",
    )

    val reduction = (state.baseCalories - state.effectiveCalories).coerceAtLeast(0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerSmall))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .graphicsLayer { alpha = pressAlpha }
            .background(accent.tintedSurface)
            .border(1.dp, accent.tintedBorder, RoundedCornerShape(CornerSmall))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = accent.inkLight,
            modifier = Modifier.size(16.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "WEEKLY REBALANCE",
                style = AppType.metaLabel,
                color = accent.inkLight,
            )
            if (state.dayX == 0) {
                Text(
                    text = "Starts tomorrow",
                    style = AppType.cardSubtitle,
                    color = appColors.textSecondary,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DayDots(dayX = state.dayX, ofY = state.ofY, mini = true)
                    Text(
                        text = "Day ${state.dayX} of ${state.ofY}",
                        style = AppType.cardSubtitle,
                        color = appColors.textSecondary,
                    )
                }
            }
        }
        // Today's lever, right-aligned: the calorie cut if any, else the step boost, else nothing
        // (day-0 reduces nothing today).
        if (state.dayX != 0) {
            val trailing = when {
                reduction > 0 -> "−$reduction kcal"
                state.extraSteps > 0 -> "+${formatK(state.extraSteps)} steps"
                else -> null
            }
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = AppType.cardSubtitle,
                    color = accent.inkLight,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = accent.inkLight,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Previews (dark theme) ──────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewRebalanceRibbon() {
    RebalanceRibbon(
        state = RebalanceCardUiState(
            face = RebalanceCardUiState.Face.PROGRESS,
            dayX = 2,
            ofY = 4,
            effectiveCalories = 1950,
            baseCalories = 2200,
        ),
        onClick = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewRebalanceRibbonDayZero() {
    RebalanceRibbon(
        state = RebalanceCardUiState(
            face = RebalanceCardUiState.Face.PROGRESS,
            dayX = 0,
            ofY = 3,
            effectiveCalories = 1950,
            baseCalories = 2200,
        ),
        onClick = {},
    )
}
