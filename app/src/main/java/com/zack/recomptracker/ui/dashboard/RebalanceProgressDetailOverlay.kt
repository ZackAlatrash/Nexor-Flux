package com.zack.recomptracker.ui.dashboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.ui.component.AiBadge
import com.zack.recomptracker.ui.component.AiBorderMode
import com.zack.recomptracker.ui.component.AiDialogCard
import com.zack.recomptracker.ui.component.DismissButton
import com.zack.recomptracker.ui.component.GlassAlertDialog
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.charts.ChartDefaults
import com.zack.recomptracker.ui.component.rememberAnimationsEnabled
import com.zack.recomptracker.ui.review.BriefingGhostButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

private val ModalCorner = 24.dp

/**
 * The floating Weekly Rebalance PROGRESS detail — a `Dialog` opened by tapping
 * [RebalanceRibbon], reusing the same [AiDialogCard] + edge-glow stack and the same card
 * scale/alpha enter animation as [RebalanceOfferOverlay] (spring scale 0.94→1 combined with a
 * 200ms alpha tween, both gated by [rememberAnimationsEnabled]). Renders nothing unless [open] is
 * true and [state]'s face is [RebalanceCardUiState.Face.PROGRESS].
 *
 * Deliberately does NOT use [ConvergenceReadout] — that atom needs deterministic weekly-average
 * numbers that are not exposed on [RebalanceCardUiState], and this overlay must never invent
 * numbers. The momentum line ([RebalanceCardUiState.body]) already carries that narrative
 * textually; `ConvergenceReadout` remains reserved for a later weekly-average enhancement.
 */
@Composable
internal fun RebalanceProgressDetailOverlay(
    open: Boolean,
    state: RebalanceCardUiState,
    onClose: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!open || state.face != RebalanceCardUiState.Face.PROGRESS) return

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val animationsEnabled = rememberAnimationsEnabled()
        var shown by remember { mutableStateOf(!animationsEnabled) }
        LaunchedEffect(Unit) { shown = true }
        val scale by animateFloatAsState(
            targetValue = if (shown) 1f else 0.94f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "progressDetailScale",
        )
        val cardAlpha by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(200),
            label = "progressDetailAlpha",
        )

        AiDialogCard(
            borderMode = AiBorderMode.Ready,
            cornerRadius = ModalCorner,
            scrollable = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = cardAlpha
                },
        ) {
            ProgressDetailBody(state = state, onClose = onClose, onCancel = onCancel)
        }
    }
}

/**
 * The PROGRESS detail card's contents, extracted from [RebalanceProgressDetailOverlay] so it can
 * be previewed without the `Dialog` wrapper (a `Dialog` doesn't render in the IDE preview
 * surface) — mirrors [RebalanceOfferOverlay]'s `OfferBody` extraction.
 */
@Composable
private fun ColumnScope.ProgressDetailBody(
    state: RebalanceCardUiState,
    onClose: () -> Unit,
    onCancel: () -> Unit,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    val animationsEnabled = rememberAnimationsEnabled()
    var confirmingCancel by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(text = "Weekly Rebalance")
            AiBadge()
        }
        DismissButton(onDismiss = onClose, contentDescription = "Close")
    }

    if (state.dayX == 0) {
        // Accepted late in the day — starts-tomorrow line only, no dots / progress bar (spec §10).
        Spacer(Modifier.height(6.dp))
        Text(text = state.body, style = AppType.body, color = appColors.textPrimary)
    } else {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Day ${state.dayX} of ${state.ofY}",
            style = AppType.screenTitleCompact,
            color = appColors.textPrimary,
        )

        Spacer(Modifier.height(12.dp))
        DayDots(dayX = state.dayX, ofY = state.ofY, mini = false)

        Spacer(Modifier.height(14.dp))
        val fillBrush = remember(accent.accent, accent.accentLight) {
            Brush.horizontalGradient(listOf(accent.accent, accent.accentLight))
        }
        val animatedFraction = if (animationsEnabled) {
            val animated by animateFloatAsState(
                targetValue = state.progressFraction,
                animationSpec = ChartDefaults.AnimSpec.progressBar,
                label = "rebalanceProgressDetail",
            )
            animated
        } else {
            state.progressFraction
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(appColors.cardBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(6.dp)
                    .background(fillBrush, RoundedCornerShape(3.dp)),
            )
        }

        Spacer(Modifier.height(12.dp))
        // state.body is the already-composed PROGRESS_LINE copy (fallback or phrased), which itself
        // states today's effective kcal — this overlay never hands the composable a bare number to
        // re-append (same contract as RebalanceCard.ProgressFaceContent).
        Text(text = state.body, style = AppType.body, color = appColors.textSecondary)
        if (state.extraSteps > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "+${state.extraSteps} steps today",
                style = AppType.cardSubtitle,
                color = appColors.textMuted,
            )
        }
    }

    // Cancel affordance — available on every progress day (including day-0): ends the plan and
    // returns today onward to the base plan. Opens a destructive confirm first.
    Spacer(Modifier.height(16.dp))
    BriefingGhostButton(
        text = "Cancel rebalance",
        onClick = { confirmingCancel = true },
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )

    if (confirmingCancel) {
        GlassAlertDialog(
            onDismiss = { confirmingCancel = false },
            title = "Cancel this rebalance?",
            body = "Today and the days ahead go back to your normal plan. Days you've already " +
                "finished stay as they were.",
            confirmLabel = "Cancel rebalance",
            onConfirm = {
                confirmingCancel = false
                onCancel()
                onClose()
            },
            dismissLabel = "Keep going",
            isDestructive = true,
        )
    }
}

// ── Preview (dark theme) ────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewProgressDetailBody() {
    val state = RebalanceCardUiState(
        face = RebalanceCardUiState.Face.PROGRESS,
        body = "You're almost at your planned average today — 1950 kcal keeps you on track.",
        dayX = 2,
        ofY = 4,
        progressFraction = 0.5f,
        effectiveCalories = 1950,
        extraSteps = 1200,
        mode = RebalanceMode.BALANCED,
        baseCalories = 2200,
    )
    AiDialogCard(borderMode = AiBorderMode.Ready, cornerRadius = ModalCorner, scrollable = false) {
        ProgressDetailBody(state = state, onClose = {}, onCancel = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewProgressDetailBodyDayZero() {
    val state = RebalanceCardUiState(
        face = RebalanceCardUiState.Face.PROGRESS,
        body = "Your rebalance starts tomorrow — today stays your normal plan.",
        dayX = 0,
        ofY = 3,
        effectiveCalories = 1950,
        mode = RebalanceMode.BALANCED,
        baseCalories = 2200,
    )
    AiDialogCard(borderMode = AiBorderMode.Ready, cornerRadius = ModalCorner, scrollable = false) {
        ProgressDetailBody(state = state, onClose = {}, onCancel = {})
    }
}
