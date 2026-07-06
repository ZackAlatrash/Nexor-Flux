package com.zack.recomptracker.ui.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zack.recomptracker.domain.rebalance.RebalanceDayBar
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.ui.component.AiBadge
import com.zack.recomptracker.ui.component.AiBorderMode
import com.zack.recomptracker.ui.component.AiDialogCard
import com.zack.recomptracker.ui.component.DismissButton
import com.zack.recomptracker.ui.component.GlassSegmentedToggle
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.rememberAnimationsEnabled
import com.zack.recomptracker.ui.review.BriefingGhostButton
import com.zack.recomptracker.ui.review.BriefingPrimaryButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerPill
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private val ModalCorner = 24.dp

/**
 * Customize-toggle labels, in [RebalanceMode] enum order (spec §3) — mirrors the constant that
 * lived in `RebalanceCard.kt` (deleted in Task 7). Not shared from there on purpose: this Dialog
 * surface must not depend on anything in that file.
 */
private val CUSTOMIZE_OPTIONS = listOf("Eat less", "Balanced", "Move more")

/**
 * The floating Weekly Rebalance OFFER popup — a `Dialog` reusing the app's AI-card + edge-glow
 * stack (exactly like [com.zack.recomptracker.ui.review.WeeklyBriefingOverlay]). Renders nothing
 * unless [state]'s face is [RebalanceCardUiState.Face.OFFER] and the card is not [minimized] (a
 * minimize collapses this Dialog down to [RebalanceReopenPill] instead of dismissing the offer).
 *
 * A `Dialog` is a separate Android window with no `LocalBackdrop`, so every surface here is built
 * from [AiDialogCard] + the backdrop-free `Briefing*` buttons — never [com.zack.recomptracker.ui.component.AiInsightCard],
 * `FrostedCard`, `TintedCard`, `LiquidActionButton`, `LiquidPrimaryButton`, or `GlassBottomSheet`.
 *
 * @param onAccept "Start rebalance" — accepts the offered plan.
 * @param onDecline "Keep my normal plan" — declines the offer.
 * @param onMinimize collapses the Dialog to the reopenable pill (tap-outside-to-dismiss + the
 *   header's close affordance both route here — minimizing never declines).
 * @param onCustomize invoked with the newly-selected [RebalanceMode] from the inline Customize row.
 */
@Composable
internal fun RebalanceOfferOverlay(
    state: RebalanceCardUiState,
    minimized: Boolean,
    phrasing: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMinimize: () -> Unit,
    onCustomize: (RebalanceMode) -> Unit,
) {
    if (state.face != RebalanceCardUiState.Face.OFFER || minimized) return

    Dialog(onDismissRequest = onMinimize, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val animationsEnabled = rememberAnimationsEnabled()
        var shown by remember { mutableStateOf(!animationsEnabled) }
        LaunchedEffect(Unit) { shown = true }
        val scale by animateFloatAsState(
            targetValue = if (shown) 1f else 0.94f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "offerScale",
        )
        val cardAlpha by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(200),
            label = "offerAlpha",
        )

        val borderMode = if (phrasing) AiBorderMode.Generating else AiBorderMode.Ready
        AiDialogCard(
            borderMode = borderMode,
            cornerRadius = ModalCorner,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = cardAlpha
                },
        ) {
            OfferBody(
                state = state,
                onAccept = onAccept,
                onDecline = onDecline,
                onMinimize = onMinimize,
                onCustomize = onCustomize,
            )
        }
    }
}

/**
 * The OFFER card's contents, extracted from [RebalanceOfferOverlay] so it can be previewed without
 * the `Dialog` wrapper (a `Dialog` doesn't render in the IDE preview surface).
 */
@Composable
private fun ColumnScope.OfferBody(
    state: RebalanceCardUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMinimize: () -> Unit,
    onCustomize: (RebalanceMode) -> Unit,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    var showCustomize by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(text = "Weekly Rebalance")
            AiBadge()
        }
        DismissButton(onDismiss = onMinimize, contentDescription = "Decide later")
    }

    Spacer(Modifier.height(6.dp))
    Text(text = state.headline, style = AppType.screenTitleCompact, color = appColors.textPrimary)

    Spacer(Modifier.height(4.dp))
    Text(text = state.body, style = AppType.body, color = appColors.textSecondary)

    Spacer(Modifier.height(12.dp))
    WeeklyBarsChart(bars = state.weeklyBars)

    Spacer(Modifier.height(12.dp))
    LeverTiles(
        reduction = (state.baseCalories - state.effectiveCalories).coerceAtLeast(0),
        extraSteps = state.extraSteps,
        days = state.ofY,
    )

    Spacer(Modifier.height(14.dp))
    BriefingPrimaryButton(text = "Start rebalance", onClick = onAccept, modifier = Modifier.fillMaxWidth())
    BriefingGhostButton(
        text = "Keep my normal plan",
        onClick = onDecline,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )

    Spacer(Modifier.height(10.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A clearly-tappable pill (accent-tinted glass + border + a rotating chevron) rather than a
        // bare text label, which read as a caption and didn't invite a tap.
        val chevronRotation by animateFloatAsState(
            targetValue = if (showCustomize) 180f else 0f,
            animationSpec = tween(200),
            label = "adjustChevron",
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(CornerPill))
                .clickable(role = Role.Button, onClick = { showCustomize = !showCustomize })
                .background(accent.tintedSurface)
                .border(1.dp, accent.tintedBorder, RoundedCornerShape(CornerPill))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = accent.inkLight,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = if (showCustomize) "Done adjusting" else "Adjust the balance",
                style = AppType.label,
                color = accent.inkLight,
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = accent.inkLight,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation),
            )
        }
        if (showCustomize) {
            Spacer(Modifier.height(12.dp))
            GlassSegmentedToggle(
                options = CUSTOMIZE_OPTIONS,
                selectedIndex = state.mode.toIndex(),
                onSelect = { index -> onCustomize(index.toMode()) },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your plan recomputes instantly — no need to restart.",
                style = AppType.cardSubtitle,
                color = appColors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Text(
        text = "Tap outside to decide later",
        style = AppType.metaLabel,
        color = appColors.textMuted,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

private fun RebalanceMode.toIndex(): Int = when (this) {
    RebalanceMode.EAT_LESS -> 0
    RebalanceMode.BALANCED -> 1
    RebalanceMode.MOVE_MORE -> 2
}

private fun Int.toMode(): RebalanceMode = when (this) {
    0 -> RebalanceMode.EAT_LESS
    2 -> RebalanceMode.MOVE_MORE
    else -> RebalanceMode.BALANCED
}

// ── Preview (dark theme) ────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewOfferBody() {
    val bars: ImmutableList<RebalanceDayBar> = persistentListOf(
        RebalanceDayBar("Mon", 2650, 2200, isPlanDay = false, isOver = true),
        RebalanceDayBar("Tue", 2100, 2200, isPlanDay = false, isOver = false),
        RebalanceDayBar("Wed", 2800, 2200, isPlanDay = false, isOver = true),
        RebalanceDayBar("Thu", 2900, 2200, isPlanDay = false, isOver = true),
        RebalanceDayBar("Fri", 2050, 2200, isPlanDay = false, isOver = false),
        RebalanceDayBar("Sat", 3100, 2200, isPlanDay = false, isOver = true),
        RebalanceDayBar("Sun", 1950, 2200, isPlanDay = false, isOver = false),
        RebalanceDayBar("Mon", 2050, 2200, isPlanDay = true, isOver = false),
        RebalanceDayBar("Tue", 2050, 2200, isPlanDay = true, isOver = false),
        RebalanceDayBar("Wed", 2050, 2200, isPlanDay = true, isOver = false),
    )
    val state = RebalanceCardUiState(
        face = RebalanceCardUiState.Face.OFFER,
        headline = "You've been running a surplus",
        body = "Your last 3 days averaged 550 kcal over target. A short rebalance brings you back on track.",
        dayX = 0,
        ofY = 3,
        effectiveCalories = 1950,
        extraSteps = 1200,
        mode = RebalanceMode.BALANCED,
        baseCalories = 2200,
        weeklyBars = bars,
    )
    AiDialogCard(borderMode = AiBorderMode.Ready, cornerRadius = ModalCorner) {
        OfferBody(state = state, onAccept = {}, onDecline = {}, onMinimize = {}, onCustomize = {})
    }
}
