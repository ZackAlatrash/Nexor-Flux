package com.zack.recomptracker.ui.train.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.domain.workout.MuscleTrainingAggregator.RecoveryBand
import com.zack.recomptracker.ui.component.NeutralCard
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerChip
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.theme.Spacing
import com.zack.recomptracker.ui.train.MuscleRecovery

// Status-dot palette for the recovery bands. Deliberately a warm "still cooling down" ramp
// (amber → red) so it reads as *rest state*, and never collides with the Stats-tab body-map heat
// (a green→red *volume* scale — a different meaning). Fresh muscles are omitted, so no fresh colour.
private val RecoveringDot = Color(0xFFE0574B) // hammered — needs rest
private val ModerateDot = Color(0xFFE0A73E)   // partway recovered

/**
 * A compact "muscles still recovering" strip for the Train-home Training Readiness area — an added
 * layer beneath the whole-body readiness card. Lists only the muscle groups that are **not** fresh
 * (RECOVERING first, then MODERATE), each as a small status chip. Fully-fresh groups are omitted to
 * keep it uncluttered; when nothing is recovering (including empty history) the strip renders nothing
 * at all. Reuses [NeutralCard] + design tokens; the caller supplies the [modifier] padding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleRecoveryStrip(
    recovery: List<MuscleRecovery>,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    // Only surface groups that still need rest; RECOVERING ahead of MODERATE (the input is already
    // sorted lowest-score-first, which is exactly this order).
    val needsRest = recovery.filter { it.band != RecoveryBand.FRESH }
    if (needsRest.isEmpty()) return

    NeutralCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = appColors.textSecondary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "Still recovering",
                style = AppType.metaLabel,
                color = appColors.textSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            needsRest.forEach { m -> RecoveryChip(m) }
        }
    }
}

@Composable
private fun RecoveryChip(muscle: MuscleRecovery) {
    val appColors = LocalAppColors.current
    val dot = if (muscle.band == RecoveryBand.RECOVERING) RecoveringDot else ModerateDot
    Row(
        modifier = Modifier
            .background(appColors.cardSurface, RoundedCornerShape(CornerChip))
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(CornerChip))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dot, CircleShape),
        )
        Text(
            text = muscle.category.displayName,
            style = AppType.label,
            color = appColors.textPrimary,
        )
    }
}
