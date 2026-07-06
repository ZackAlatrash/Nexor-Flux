package com.zack.recomptracker.ui.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.data.rebalance.RebalanceDebugScenario
import com.zack.recomptracker.ui.component.NeutralCard
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.liquidglass.LiquidActionButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Developer-only tool screen (reached from the More hub). Its single purpose right now: force the
 * redesigned Weekly Rebalance dashboard card into any phase so it can be eyeballed on-device without
 * waiting for real data to trigger each state. Each row fires
 * [com.zack.recomptracker.data.rebalance.RebalanceCoordinator.debugApply] via the ViewModel; the
 * destructive "Clear" reset sits apart in a red-tinted card at the bottom.
 */
@Composable
internal fun DeveloperScreen(
    viewModel: DeveloperViewModel,
    onBack: () -> Unit,
) {
    val appColors = LocalAppColors.current
    val currentPhase by viewModel.currentPhase.collectAsStateWithLifecycle()

    // Every scenario except the destructive Clear, which is surfaced separately below.
    val scenarios = RebalanceDebugScenario.entries.filter { it != RebalanceDebugScenario.CLEAR }

    com.zack.recomptracker.ui.component.ScreenScaffold(withNavBarInset = false) {
        item {
            SubScreenHeader(
                title = "Developer",
                onBack = onBack,
                subtitle = "Weekly Rebalance phase tester",
            )
        }

        item { SectionLabel("Weekly Rebalance") }
        item {
            Text(
                text = "Force each phase, then open Home to see it. Currently showing: $currentPhase.",
                style = AppType.body,
                color = appColors.textMuted,
            )
        }

        item {
            NeutralCard {
                scenarios.forEachIndexed { index, scenario ->
                    ScenarioRow(
                        title = scenario.label,
                        detail = scenario.description,
                        onApply = { viewModel.apply(scenario) },
                        showDivider = index != scenarios.lastIndex,
                    )
                }
            }
        }

        item { SectionLabel("Reset") }
        item {
            DangerCard {
                ScenarioRow(
                    title = RebalanceDebugScenario.CLEAR.label,
                    detail = RebalanceDebugScenario.CLEAR.description,
                    onApply = { viewModel.apply(RebalanceDebugScenario.CLEAR) },
                    showDivider = false,
                    danger = true,
                )
            }
        }
    }
}

// ── Danger card (red-tinted) — mirrors DataBackupScreen's destructive container ──────────────────────

@Composable
private fun DangerCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(ErrorRed.copy(alpha = 0.06f))
            .border(1.dp, ErrorRed.copy(alpha = 0.25f), RoundedCornerShape(CornerCard))
            .padding(vertical = 4.dp),
        content = content,
    )
}

/** One scenario row: label + description on the left, an "Apply" action button on the right. */
@Composable
private fun ScenarioRow(
    title: String,
    detail: String,
    onApply: () -> Unit,
    showDivider: Boolean,
    danger: Boolean = false,
) {
    val appColors = LocalAppColors.current
    val titleColor = if (danger) ErrorRed else appColors.textPrimary
    val dividerColor = if (danger) ErrorRed.copy(alpha = 0.12f) else appColors.cardBorder

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = AppType.cardTitle, color = titleColor)
                Text(detail, style = AppType.label, color = appColors.textMuted)
            }
            LiquidActionButton(
                text = "Apply",
                onClick = onApply,
                small = true,
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor),
            )
        }
    }
}
