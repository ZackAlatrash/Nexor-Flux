package com.zack.recomptracker.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.ui.component.DismissButton
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.rememberAnimationsEnabled
import com.zack.recomptracker.ui.theme.AccentTheme
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppColors

// Fixed green ending skin, independent of the user accent — mirrors the fixed-gold celebration
// skin (AppColors.celebrationSurface/celebrationBorder/celebrationInk). Built off AccentTheme.EMERALD
// so it's a palette color, not a raw literal: a graceful-end / no-adjustment note always reads as a
// calm, reassuring green, regardless of the user's chosen accent theme.
private val GreenSurface = AccentTheme.EMERALD.accent.copy(alpha = 0.10f)
private val GreenBorder = AccentTheme.EMERALD.accent.copy(alpha = 0.30f)
private val GreenInk = AccentTheme.EMERALD.accentLight

/**
 * The inline Weekly Rebalance ending note — the terminal card for a plan that has finished, in one
 * of three distinct skins keyed by [RebalanceCardUiState.noteKind] (spec §3, §8):
 *  - [NoteKind.COMPLETION] — warm/gold "a win" skin (mirrors [CoachTodaySlot]'s celebration card).
 *  - [NoteKind.GRACEFUL_END] / [NoteKind.NO_ADJUSTMENT] — calm, fixed-green "all is fine" skin.
 *
 * Renders **nothing** unless [RebalanceCardUiState.face] is [RebalanceCardUiState.Face.NOTE] —
 * mirrors [CoachTodaySlot]/[RebalanceCard]'s "silent when not applicable" contract. Renders INLINE
 * in the dashboard `LazyColumn` (same composition window as the rest of the dashboard content), so
 * unlike the modal offer/progress overlays, [FrostedCard] (which reads `LocalBackdrop`) is safe here.
 *
 * Plays a one-shot enter animation the first time it appears (fade + slight rise), and the
 * COMPLETION trophy additionally "pops" in with a bouncy scale spring. Both degrade to an instant
 * (zero-duration) transition when [rememberAnimationsEnabled] is `false`. No exit animation is
 * needed — the caller removes this composable's `LazyColumn` item on dismiss.
 *
 * @param onDismiss clears the note (the card's own close affordance).
 */
@Composable
internal fun RebalanceNoteCard(
    state: RebalanceCardUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.face != RebalanceCardUiState.Face.NOTE) return
    val animationsEnabled = rememberAnimationsEnabled()

    // One-shot enter: starts hidden, flips to visible on first composition (never re-triggers on
    // recomposition since the state object itself isn't reset).
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visibleState.targetState = true }

    val enter = if (animationsEnabled) {
        fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 3 }
    } else {
        fadeIn(tween(0))
    }

    AnimatedVisibility(visibleState = visibleState, enter = enter, modifier = modifier) {
        when (state.noteKind) {
            NoteKind.COMPLETION -> CompletionNote(state, onDismiss, animationsEnabled)
            NoteKind.GRACEFUL_END -> GracefulEndNote(state, onDismiss)
            // REASSURANCE (small-end note) reuses the calm green "all is fine" skin for now; a distinct
            // face is a later UI task (spec §12).
            NoteKind.NO_ADJUSTMENT, NoteKind.REASSURANCE -> NoAdjustmentNote(state, onDismiss)
            null -> FallbackNote(state, onDismiss) // defensive — engine should always set a kind
        }
    }
}

/** Warm/gold "a win" skin — mirrors [CoachTodaySlot]'s celebration card exactly. */
@Composable
private fun CompletionNote(state: RebalanceCardUiState, onDismiss: () -> Unit, animationsEnabled: Boolean) {
    val appColors = LocalAppColors.current
    FrostedCard(
        contentPadding = 14.dp,
        surfaceTint = appColors.celebrationSurface,
        borderColor = appColors.celebrationBorder,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Rebalance complete".uppercase(),
                style = AppType.sectionLabel,
                color = appColors.celebrationInk,
            )
            DismissButton(onDismiss, contentDescription = "Dismiss rebalance note")
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shown = remember { MutableTransitionState(false) }
            LaunchedEffect(Unit) { shown.targetState = true }
            val popScale by animateFloatAsState(
                targetValue = if (shown.targetState) 1f else 0f,
                animationSpec = if (animationsEnabled) {
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                } else {
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                },
                label = "trophyPop",
            )
            Text(
                text = "🏆", // trophy emoji — genuine content, not a tap affordance
                style = AppType.statValue,
                modifier = Modifier.graphicsLayer {
                    scaleX = popScale
                    scaleY = popScale
                },
            )
            Text(
                text = "Back on track.",
                style = AppType.screenTitleCompact,
                color = appColors.textPrimary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = state.body, style = AppType.body, color = appColors.textSecondary)
    }
}

/** Fixed-green "easing off, that's fine" skin for an early-ended (graceful) plan. */
@Composable
private fun GracefulEndNote(state: RebalanceCardUiState, onDismiss: () -> Unit) {
    GreenNote(
        state = state,
        onDismiss = onDismiss,
        headerLabel = "Weekly Rebalance",
        emoji = "🌿", // herb — genuine content, not a tap affordance
        title = "Easing off — that's fine.",
    )
}

/** Fixed-green "keep your normal plan" skin for a week that needed no adjustment at all. */
@Composable
private fun NoAdjustmentNote(state: RebalanceCardUiState, onDismiss: () -> Unit) {
    GreenNote(
        state = state,
        onDismiss = onDismiss,
        headerLabel = "Weekly Rebalance",
        emoji = "🧭", // compass — genuine content, not a tap affordance
        title = "Keep your normal plan.",
    )
}

/** Shared body for the two fixed-green endings — only the emoji/title copy differs. */
@Composable
private fun GreenNote(
    state: RebalanceCardUiState,
    onDismiss: () -> Unit,
    headerLabel: String,
    emoji: String,
    title: String,
) {
    val appColors = LocalAppColors.current
    FrostedCard(
        contentPadding = 14.dp,
        surfaceTint = GreenSurface,
        borderColor = GreenBorder,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = headerLabel.uppercase(),
                style = AppType.sectionLabel,
                color = GreenInk,
            )
            DismissButton(onDismiss, contentDescription = "Dismiss rebalance note")
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = emoji, style = AppType.statValue)
            Text(text = title, style = AppType.screenTitleCompact, color = appColors.textPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text(text = state.body, style = AppType.body, color = appColors.textSecondary)
    }
}

/** Defensive fallback when [state]'s [NoteKind] is somehow null — a plain, unskinned note. */
@Composable
private fun FallbackNote(state: RebalanceCardUiState, onDismiss: () -> Unit) {
    val appColors = LocalAppColors.current
    FrostedCard(contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Weekly Rebalance")
            DismissButton(onDismiss, contentDescription = "Dismiss rebalance note")
        }
        Spacer(Modifier.height(6.dp))
        Text(text = state.body, style = AppType.body, color = appColors.textPrimary)
    }
}

// ── Previews (dark theme) ──────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewRebalanceNoteCompletion() {
    RebalanceNoteCard(
        state = RebalanceCardUiState(
            face = RebalanceCardUiState.Face.NOTE,
            body = "You finished all 4 days on the reduced target — nice work staying consistent.",
            noteKind = NoteKind.COMPLETION,
        ),
        onDismiss = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewRebalanceNoteGracefulEnd() {
    RebalanceNoteCard(
        state = RebalanceCardUiState(
            face = RebalanceCardUiState.Face.NOTE,
            body = "Your weight trend flattened out, so we ended the rebalance early — no harm done.",
            noteKind = NoteKind.GRACEFUL_END,
        ),
        onDismiss = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewRebalanceNoteNoAdjustment() {
    RebalanceNoteCard(
        state = RebalanceCardUiState(
            face = RebalanceCardUiState.Face.NOTE,
            body = "Your trend already looks on track, so there's nothing to rebalance this week.",
            noteKind = NoteKind.NO_ADJUSTMENT,
        ),
        onDismiss = {},
    )
}
