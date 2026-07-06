package com.zack.recomptracker.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * A tappable ribbon that rides the Today card's own space, summarizing an in-progress Weekly
 * Rebalance plan (day-X-of-Y + today's reduction) and opening [RebalanceProgressDetailOverlay] on
 * tap. Renders its content unconditionally — this composable does NOT early-return on
 * [state]'s face and does NOT wrap itself in `AnimatedVisibility`. The Task-7 call site inside
 * `TodayCard` is responsible for wrapping `RebalanceRibbon` in
 * `AnimatedVisibility(visible = state.face == RebalanceCardUiState.Face.PROGRESS)` so the
 * fade/expand enter-exit (mirroring [com.zack.recomptracker.ui.component.WeekCalorieStrip]'s
 * "Today" pill idiom) can play across composition, not just within this function.
 *
 * Has its OWN `Modifier.clickable` (rather than relying on a parent row's click) so the Today
 * card's separate Food-Log tap target is not hijacked — the inner clickable consumes the tap
 * first.
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .graphicsLayer { alpha = pressAlpha }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.dayX == 0) {
            // Nothing is reduced today — no dots, no chevron, just the starts-tomorrow label.
            Text(
                text = "Rebalance · starts tomorrow",
                style = AppType.sectionLabel,
                color = accent.accentLighter,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DayDots(dayX = state.dayX, ofY = state.ofY, mini = true)
                Text(
                    text = "Rebalance · day ${state.dayX} of ${state.ofY}",
                    style = AppType.sectionLabel,
                    color = accent.accentLighter,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "−${(state.baseCalories - state.effectiveCalories).coerceAtLeast(0)} kcal",
                style = AppType.cardSubtitle,
                color = appColors.textMuted,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent.accentLight,
                modifier = Modifier.size(18.dp),
            )
        }
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
