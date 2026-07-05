package com.zack.recomptracker.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.ui.component.GlassBottomSheet
import com.zack.recomptracker.ui.component.GlassSegmentedToggle
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.TintedCard
import com.zack.recomptracker.ui.component.charts.ChartDefaults
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/** Customize-toggle labels, in [RebalanceMode] enum order (spec §3). */
private val CUSTOMIZE_OPTIONS = listOf("Eat less", "Balanced", "Move more")

private fun RebalanceMode.toIndex(): Int = when (this) {
    RebalanceMode.EAT_LESS -> 0
    RebalanceMode.BALANCED -> 1
    RebalanceMode.MOVE_MORE -> 2
}

private fun Int.toRebalanceMode(): RebalanceMode = when (this) {
    0 -> RebalanceMode.EAT_LESS
    2 -> RebalanceMode.MOVE_MORE
    else -> RebalanceMode.BALANCED
}

/**
 * The Weekly Rebalance dashboard card — the offer / progress / note faces of the same conditional
 * slot (spec §3). Renders **nothing** when [state]'s face is [RebalanceCardUiState.Face.NONE] (mirrors
 * [CoachTodaySlot]'s early-return-null contract). The ViewModel has already decided every number and
 * every word (fallback or phrased); this composable only lays them out.
 *
 * @param onAccept "Start Weekly Rebalance" (OFFER face).
 * @param onDecline "Keep My Normal Plan" (OFFER face).
 * @param onDismiss the NOTE face's close affordance.
 * @param onCustomize invoked with the newly-selected [RebalanceMode] from the Customize sheet.
 */
@Composable
fun RebalanceCard(
    state: RebalanceCardUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
    onCustomize: (RebalanceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.face == RebalanceCardUiState.Face.NONE) return
    val appColors = LocalAppColors.current

    TintedCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Weekly Rebalance")
            if (state.face == RebalanceCardUiState.Face.NOTE) {
                DismissButton(onDismiss)
            }
        }
        Spacer(Modifier.height(6.dp))

        when (state.face) {
            RebalanceCardUiState.Face.OFFER -> OfferFaceContent(state, onAccept, onDecline, onCustomize)
            RebalanceCardUiState.Face.PROGRESS -> ProgressFaceContent(state)
            RebalanceCardUiState.Face.NOTE -> {
                Text(text = state.body, style = AppType.body, color = appColors.textPrimary)
            }
            RebalanceCardUiState.Face.NONE -> Unit // unreachable — early-returned above
        }
    }
}

@Composable
private fun OfferFaceContent(
    state: RebalanceCardUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCustomize: (RebalanceMode) -> Unit,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    var showCustomize by remember { mutableStateOf(false) }

    Text(text = state.headline, style = AppType.cardTitle, color = appColors.textPrimary)
    Spacer(Modifier.height(4.dp))
    Text(text = state.body, style = AppType.body, color = appColors.textSecondary)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LiquidActionButton(
            text = "Start Weekly Rebalance",
            onClick = onAccept,
            isPrimary = true,
            small = true,
            modifier = Modifier.weight(1f),
        )
        LiquidActionButton(
            text = "Keep My Normal Plan",
            onClick = onDecline,
            isPrimary = false,
            small = true,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Customize",
        style = AppType.label,
        color = accent.inkLight,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = { showCustomize = true })
            .semantics { contentDescription = "Customize rebalance preference" },
    )

    if (showCustomize) {
        CustomizeSheet(
            mode = state.mode,
            onSelect = onCustomize,
            onDismiss = { showCustomize = false },
        )
    }
}

@Composable
private fun CustomizeSheet(
    mode: RebalanceMode,
    onSelect: (RebalanceMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    GlassBottomSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            SectionLabel("Preference")
            GlassSegmentedToggle(
                options = CUSTOMIZE_OPTIONS,
                selectedIndex = mode.toIndex(),
                onSelect = { index -> onSelect(index.toRebalanceMode()) },
            )
            Text(
                text = "Your plan recomputes instantly — no need to restart.",
                style = AppType.cardSubtitle,
                color = appColors.textMuted,
            )
            LiquidPrimaryButton(text = "Done", onClick = onDismiss)
        }
    }
}

@Composable
private fun ProgressFaceContent(state: RebalanceCardUiState) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current

    if (state.dayX == 0) {
        // Accepted late in the day — starts-tomorrow line, no chip / fill bar (spec §10).
        Text(text = state.body, style = AppType.body, color = appColors.textPrimary)
        return
    }

    Text(
        text = "Day ${state.dayX} of ${state.ofY}",
        style = AppType.statValueSmall,
        color = appColors.textPrimary,
    )
    Spacer(Modifier.height(8.dp))

    val fillBrush = remember(accent.accent, accent.accentLight) {
        Brush.horizontalGradient(listOf(accent.accent, accent.accentLight))
    }
    val animatedFraction by animateFloatAsState(
        targetValue = state.progressFraction,
        animationSpec = ChartDefaults.AnimSpec.progressBar,
        label = "rebalanceProgress",
    )
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
    Spacer(Modifier.height(8.dp))

    // state.body is the already-composed PROGRESS_LINE copy (fallback or phrased), which itself
    // states today's effective kcal — the VM never hands the composable a bare number to re-append.
    Text(
        text = state.body,
        style = AppType.cardSubtitle,
        color = appColors.textSecondary,
    )
    if (state.extraSteps > 0) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = "+${state.extraSteps} steps today",
            style = AppType.cardSubtitle,
            color = appColors.textMuted,
        )
    }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit) {
    val appColors = LocalAppColors.current
    // Compact 32dp target (not the 48dp min) — mirrors CoachTodaySlot's DismissButton; a low-stakes,
    // reversible dismiss affordance, so the smaller touch area is an acceptable density trade.
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onDismiss)
            .semantics { contentDescription = "Dismiss rebalance note" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            tint = appColors.textVeryMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}
