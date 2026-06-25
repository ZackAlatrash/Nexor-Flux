package com.zack.recomptracker.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.MenuIcon
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.TintedCard
import com.zack.recomptracker.ui.dashboard.DashboardViewModel
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

@Composable
fun MoreScreen(
    dashboardViewModel: DashboardViewModel,
    onCalorieDecision: () -> Unit,
    onTrends: () -> Unit,
    onProfile: () -> Unit,
    onPlan: () -> Unit,
    onAppearance: () -> Unit,
    onAiCoach: () -> Unit,
    onIntegrations: () -> Unit,
    onDataBackup: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val moreOrbBrush = remember(accent.accent) {
        Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.15f), Color.Transparent))
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Ambient orb
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(moreOrbBrush),
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = FloatingNavHeight + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            item {
                com.zack.recomptracker.ui.component.ScreenHeader(
                    title = "More",
                    subtitle = "Insights & setup",
                    onBack = onBack,
                )
            }

            // ── Insights ──────────────────────────────────────────────────────
            item { SectionLabel("Insights") }
            item { CalorieDecisionCard(state, onCalorieDecision) }
            item {
                MenuCard {
                    MenuRow(
                        icon = Icons.AutoMirrored.Rounded.ShowChart,
                        title = "Trends",
                        detail = "Weight, waist, nutrition & lifts",
                        onClick = onTrends,
                        showDivider = false,
                    )
                }
            }

            // ── Setup ─────────────────────────────────────────────────────────
            item { SectionLabel("Setup") }
            item {
                MenuCard {
                    MenuRow(
                        icon = Icons.Rounded.Person,
                        title = "Profile",
                        detail = "You, goal & activity",
                        onClick = onProfile,
                        showDivider = true,
                    )
                    MenuRow(
                        icon = Icons.Rounded.TrackChanges,
                        title = "Plan",
                        detail = "Targets, zones & review cadence",
                        onClick = onPlan,
                        showDivider = true,
                    )
                    MenuRow(
                        icon = Icons.Rounded.Palette,
                        title = "Appearance",
                        detail = "Font & accent color",
                        onClick = onAppearance,
                        showDivider = true,
                    )
                    MenuRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "AI & Coach",
                        detail = "Engine, cloud & on-device model",
                        onClick = onAiCoach,
                        showDivider = true,
                    )
                    MenuRow(
                        icon = Icons.Rounded.Hub,
                        title = "Integrations",
                        detail = "Health Connect, Samsung, food catalogs",
                        onClick = onIntegrations,
                        showDivider = true,
                    )
                    MenuRow(
                        icon = Icons.Rounded.Backup,
                        title = "Data & Backup",
                        detail = "Export, import & reset",
                        onClick = onDataBackup,
                        showDivider = false,
                    )
                }
            }
        }
    }
}

// ── Featured Calorie Decision Card ──────────────────────────────────────────────

@Composable
private fun CalorieDecisionCard(
    state: com.zack.recomptracker.ui.dashboard.DashboardUiState,
    onClick: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val result = state.result
    val verdictLabel = result.verdict.label()

    val change = result.recommendedCalorieChange
    val changeText = when (result.verdict) {
        AdjustmentVerdict.WAIT_FOR_DATA -> "Not enough data yet"
        else -> {
            val sign = if (change > 0) "+" else ""
            "$sign$change kcal/day"
        }
    }
    val subtitle = "$changeText · adherence ${"%.0f".format(state.adherencePercent)}% · " +
        "${state.loggedDaysInWindow} days logged"

    TintedCard(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "CALORIE DECISION",
                style = AppType.metaLabel,
                color = accent.inkLight,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = appColors.textDim,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = verdictLabel,
            style = AppType.displayLarge,
            color = appColors.textPrimary,
            lineHeight = 40.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = AppType.cardSubtitle,
            color = appColors.textMuted,
            lineHeight = 16.sp,
        )
    }
}

private fun AdjustmentVerdict.label(): String = when (this) {
    AdjustmentVerdict.WAIT_FOR_DATA     -> "Wait"
    AdjustmentVerdict.HOLD              -> "Hold"
    AdjustmentVerdict.INCREASE_CALORIES -> "Increase"
    AdjustmentVerdict.REDUCE_CALORIES   -> "Reduce"
}

// ── Menu Card ─────────────────────────────────────────────────────────────────

@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    val appColors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(appColors.cardSurface)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(CornerCard)),
    ) {
        content()
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val appColors = LocalAppColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MenuIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = AppType.cardTitle, color = appColors.textPrimary)
                Text(detail, style = AppType.cardSubtitle, color = appColors.textMuted)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = appColors.textVeryMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(appColors.cardBorder),
            )
        }
    }
}
