package com.zack.recomptracker.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.domain.coach.CoachAction
import com.zack.recomptracker.domain.coach.CoachActionType
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.coach.isCelebration
import com.zack.recomptracker.domain.coach.isDiscovery
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.TintedCard
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * The "Today's Coaching" home slot — the UI surface for the proactive-coaching spine. Renders the
 * single staged winner ([signal]) as the accent-tinted AI card (project convention for AI surfaces);
 * renders **nothing** when [signal] is `null` (§9: "one item, silent if nothing clears the bar").
 *
 * [displayText] is shown as-is — the ViewModel already seeds it with the engine's number-safe
 * fallback and swaps in the cloud-phrased version when it returns. The slot only displays; it computes
 * no numbers.
 *
 * @param onAction invoked with the signal's [CoachActionType] when the action button is tapped; the
 *   screen maps it to an existing nav lambda. No button is shown for [CoachActionType.NONE] or an
 *   unmapped type.
 * @param onTrackExperiment invoked for a Cross-Signal Discovery card's "Track this" button — records
 *   the hypothesis as the active experiment (NOT navigation). See Phase 5 (5D).
 * @param onDismiss clears the slot (auto-dismiss on action/seen, §9).
 */
@Composable
fun CoachTodaySlot(
    signal: CoachSignal?,
    displayText: String,
    onAction: (CoachActionType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTrackExperiment: (CoachSignal) -> Unit = {},
    /** Whether the host screen can navigate for this action type — no button is shown otherwise, so
     *  a signal never renders a dead button. */
    isActionSupported: (CoachActionType) -> Boolean = { true },
) {
    if (signal == null) return
    val appColors = LocalAppColors.current
    val action = signal.action
    val showButton = action.type != CoachActionType.NONE &&
        action.label.isNotBlank() &&
        isActionSupported(action.type)

    // Cross-Signal Discovery (Phase 5) renders the SAME card in the SAME slot, but with a distinct
    // "Coach spotted a link" header and a "Track this" button that records an experiment (not nav).
    if (signal.isDiscovery) {
        TintedCard(modifier = modifier) {
            CoachSlotBody(
                header = { DiscoveryHeader(onDismiss) },
                displayText = displayText,
                showButton = false,
                action = CoachAction.None,
                onAction = onAction,
            )
            if (action.type == CoachActionType.TRACK_EXPERIMENT && action.label.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                LiquidActionButton(
                    text = action.label,
                    onClick = { onTrackExperiment(signal) },
                    isPrimary = true,
                    small = true,
                )
            }
        }
        return
    }

    // Celebration signals (new PR / recomp win) render the SAME card in the SAME slot, but in a
    // warm/gold skin with a trophy header; every other signal keeps the violet accent tint.
    if (signal.isCelebration) {
        FrostedCard(
            modifier = modifier,
            contentPadding = 14.dp,
            surfaceTint = appColors.celebrationSurface,
            borderColor = appColors.celebrationBorder,
        ) {
            CoachSlotBody(
                header = { CelebrationHeader(onDismiss) },
                displayText = displayText,
                showButton = showButton,
                action = action,
                onAction = onAction,
            )
        }
    } else {
        TintedCard(modifier = modifier) {
            CoachSlotBody(
                header = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("Today's Coaching")
                        DismissButton(onDismiss)
                    }
                },
                displayText = displayText,
                showButton = showButton,
                action = action,
                onAction = onAction,
            )
        }
    }
}

/** The shared card interior — identical for both skins; only [header] differs. */
@Composable
private fun CoachSlotBody(
    header: @Composable () -> Unit,
    displayText: String,
    showButton: Boolean,
    action: CoachAction,
    onAction: (CoachActionType) -> Unit,
) {
    val appColors = LocalAppColors.current
    header()
    Spacer(Modifier.height(6.dp))
    Text(
        text = displayText,
        style = AppType.body,
        color = appColors.textPrimary,
    )
    if (showButton) {
        Spacer(Modifier.height(10.dp))
        LiquidActionButton(
            text = action.label,
            onClick = { onAction(action.type) },
            isPrimary = true,
            small = true,
        )
    }
}

/** Discovery header: sparkle icon + accent "Coach spotted a link" label + dismiss (Phase 5 skin). */
@Composable
private fun DiscoveryHeader(onDismiss: () -> Unit) {
    val accent = LocalAppAccent.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = accent.inkLight,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "Coach spotted a link".uppercase(),
                style = AppType.sectionLabel,
                color = accent.inkLight,
            )
        }
        DismissButton(onDismiss)
    }
}

/** Warm header for a celebration: trophy icon + gold "Today's Coaching · A win" label + dismiss. */
@Composable
private fun CelebrationHeader(onDismiss: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = null,
                tint = appColors.celebrationInk,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "Today's Coaching · A win".uppercase(),
                style = AppType.sectionLabel,
                color = appColors.celebrationInk,
            )
        }
        DismissButton(onDismiss)
    }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit) {
    val appColors = LocalAppColors.current
    // Compact 32dp target (not the 48dp min) so the header row stays tight — this is a low-stakes,
    // reversible dismiss affordance, so the smaller touch area is an acceptable trade for density.
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onDismiss)
            .semantics { contentDescription = "Dismiss coaching" },
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
