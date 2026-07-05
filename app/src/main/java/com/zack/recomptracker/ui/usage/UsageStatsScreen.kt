package com.zack.recomptracker.ui.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.component.ConfirmDialog
import com.zack.recomptracker.ui.component.NeutralCard
import com.zack.recomptracker.ui.component.ScreenScaffold
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Local, on-device usage read-out (see [UsageStatsViewModel]). Utility/stats screen — clean and
 * legible over fancy. Reachable from More.
 */
@Composable
fun UsageStatsScreen(
    viewModel: UsageStatsViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val appColors = LocalAppColors.current
    var showClearConfirm by remember { mutableStateOf(false) }

    ScreenScaffold(withNavBarInset = false) {
        item { SubScreenHeader(title = "Usage", onBack = onBack) }
        item {
            Text(
                text = "Private, on-device only. This data never leaves your phone.",
                style = AppType.cardSubtitle,
                color = appColors.textMuted,
            )
        }

        when {
            ui.loading -> item { LoadingRow() }
            ui.totalEvents == 0 -> item { EmptyStateCard() }
            else -> {
                item { SectionLabel("Last 7 days") }
                item { ByTypeCard(stats = ui.byType) }

                item { SectionLabel("Insight cards") }
                item { CardEngagementCard(entries = ui.cardEngagement) }

                item {
                    LiquidSecondaryButton(
                        text = "Clear usage data",
                        onClick = { showClearConfirm = true },
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear usage data?",
            body = "All recorded usage events on this device will be deleted. This cannot be undone.",
            confirmLabel = "Clear",
            isDestructive = true,
            onConfirm = {
                viewModel.clear()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun LoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = LocalAppColors.current.textMuted)
    }
}

@Composable
private fun EmptyStateCard() {
    val appColors = LocalAppColors.current
    NeutralCard {
        Text(
            text = "No usage recorded yet — use the app and check back.",
            style = AppType.body,
            color = appColors.textMuted,
        )
    }
}

@Composable
private fun ByTypeCard(stats: List<UsageTypeStat>) {
    val appColors = LocalAppColors.current
    NeutralCard {
        stats.forEachIndexed { index, stat ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stat.label, style = AppType.cardTitle, color = appColors.textPrimary)
                Text(text = "${stat.count}", style = AppType.statValueSmall, color = appColors.textSecondary)
            }
            if (index != stats.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CardEngagementCard(entries: List<CardEngagement>) {
    val appColors = LocalAppColors.current
    if (entries.isEmpty()) {
        NeutralCard {
            Text(
                text = "No insight cards shown yet in the last 7 days.",
                style = AppType.body,
                color = appColors.textMuted,
            )
        }
        return
    }
    NeutralCard {
        entries.forEachIndexed { index, entry ->
            Column {
                Text(text = entry.cardKind, style = AppType.cardTitle, color = appColors.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "shown ${entry.shown} · tapped ${entry.tapped} · dismissed ${entry.dismissed}",
                    style = AppType.cardSubtitle,
                    color = appColors.textMuted,
                )
            }
            if (index != entries.lastIndex) Spacer(Modifier.height(14.dp))
        }
    }
}
