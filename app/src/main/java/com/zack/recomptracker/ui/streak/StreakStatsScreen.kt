package com.zack.recomptracker.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.domain.streak.StreakResult
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.ScreenScaffold
import com.zack.recomptracker.ui.component.SubScreenHeader
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

@Composable
fun StreakStatsScreen(
    viewModel: StreakViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    ScreenScaffold(withNavBarInset = false) {
        item { SubScreenHeader(title = "Streaks", onBack = onBack) }
        item { StreakDetailCard(emoji = "🏋️", name = "Workout", result = ui.streaks.workout, hint = null) }
        item { StreakDetailCard(emoji = "🔥", name = "Calorie", result = ui.streaks.calorie, hint = null) }
        item {
            StreakDetailCard(
                emoji = "👟",
                name = "Steps",
                result = ui.streaks.steps,
                hint = if (ui.stepGoal == null) {
                    "Set a daily step goal in Profile to start this streak."
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun StreakDetailCard(
    emoji: String,
    name: String,
    result: StreakResult,
    hint: String?,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    FrostedCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = emoji, style = AppType.statValue)
            Text(text = name, style = AppType.cardTitle, color = appColors.textPrimary)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${result.current}", style = AppType.displayLarge, color = appColors.textPrimary)
                Text(text = "Current (days)", style = AppType.metaLabel, color = appColors.textMuted)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "${result.longest}", style = AppType.displayLarge, color = appColors.textPrimary)
                Text(text = "Best (days)", style = AppType.metaLabel, color = appColors.textMuted)
            }
        }
        if (result.last7.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                result.last7.forEach { met ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (met) accent.accent else appColors.cardBorder),
                    )
                }
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(10.dp))
            Text(text = hint, style = AppType.cardSubtitle, color = appColors.textMuted)
        }
    }
}
