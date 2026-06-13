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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.component.MenuIcon
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.TintedCard
import com.zack.recomptracker.ui.dashboard.DashboardViewModel
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.CardSurface
import com.zack.recomptracker.ui.theme.CornerCard
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.TextMuted

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
    modifier: Modifier = Modifier,
) {
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val accent = LocalAppAccent.current
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "More",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.8).sp,
                    )
                    Text(
                        text = "Insights & setup",
                        fontSize = 13.sp,
                        color = TextMuted,
                    )
                }
            }

            // ── Insights ──────────────────────────────────────────────────────
            item { SectionLabel("Insights") }
            item { CalorieDecisionCard(state, onCalorieDecision) }
            item {
                MenuCard {
                    MenuRow(
                        emoji = "📈",
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
                        emoji = "👤",
                        title = "Profile",
                        detail = "You, goal & activity",
                        onClick = onProfile,
                        showDivider = true,
                    )
                    MenuRow(
                        emoji = "🎯",
                        title = "Plan",
                        detail = "Targets, zones & review cadence",
                        onClick = onPlan,
                        showDivider = true,
                    )
                    MenuRow(
                        emoji = "🎨",
                        title = "Appearance",
                        detail = "Font & accent color",
                        onClick = onAppearance,
                        showDivider = true,
                    )
                    MenuRow(
                        emoji = "🤖",
                        title = "AI & Coach",
                        detail = "Engine, cloud & on-device model",
                        onClick = onAiCoach,
                        showDivider = true,
                    )
                    MenuRow(
                        emoji = "🔗",
                        title = "Integrations",
                        detail = "Health Connect, Samsung, food catalogs",
                        onClick = onIntegrations,
                        showDivider = true,
                    )
                    MenuRow(
                        emoji = "💾",
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
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accent.accentLight,
                letterSpacing = 0.12.sp,
            )
            Text("›", fontSize = 16.sp, color = Color(0x59FFFFFF))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = verdictLabel,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = (-1).sp,
            lineHeight = 32.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = TextMuted,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerCard))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(CornerCard)),
    ) {
        content()
    }
}

@Composable
private fun MenuRow(
    emoji: String,
    title: String,
    detail: String,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MenuIcon(emoji)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(detail, fontSize = 11.sp, color = TextMuted)
            }
            Text("›", fontSize = 14.sp, color = Color(0x33FFFFFF))
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x0DFFFFFF)),
            )
        }
    }
}
