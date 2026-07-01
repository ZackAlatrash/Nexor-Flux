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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.domain.coach.CoachActionType
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.TintedCard
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import com.zack.recomptracker.ui.theme.AppType
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
 * @param onDismiss clears the slot (auto-dismiss on action/seen, §9).
 */
@Composable
fun CoachTodaySlot(
    signal: CoachSignal?,
    displayText: String,
    onAction: (CoachActionType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
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

    TintedCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Today's Coaching")
            DismissButton(onDismiss)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = displayText,
            style = AppType.body,
            color = appColors.textPrimary,
        )
        if (showButton) {
            Spacer(Modifier.height(14.dp))
            LiquidActionButton(
                text = action.label,
                onClick = { onAction(action.type) },
                isPrimary = true,
                small = true,
            )
        }
    }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit) {
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
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
