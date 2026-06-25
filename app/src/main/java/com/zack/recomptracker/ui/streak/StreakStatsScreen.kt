package com.zack.recomptracker.ui.streak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.domain.streak.StreakResult
import com.zack.recomptracker.domain.streak.StreakType
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
        item { StreakDetailCard(type = StreakType.WORKOUT, result = ui.streaks.workout, showStepGoalHint = false) }
        item { StreakDetailCard(type = StreakType.CALORIE, result = ui.streaks.calorie, showStepGoalHint = false) }
        item {
            StreakDetailCard(
                type = StreakType.STEPS,
                result = ui.streaks.steps,
                showStepGoalHint = ui.stepGoal == null,
            )
        }
    }
}

@Composable
private fun StreakDetailCard(
    type: StreakType,
    result: StreakResult,
    showStepGoalHint: Boolean,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    FrostedCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StreakCoin(type = type)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StreakCountFlame(count = result.current, numberStyle = AppType.displayLarge)
                Text(
                    text = "${streakLabel(type)} streak",
                    style = AppType.cardSubtitle,
                    color = appColors.textMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${result.longest}",
                    style = AppType.statValue,
                    color = accent.accentLighter,
                )
                Text(text = "BEST", style = AppType.metaLabel, color = appColors.textVeryMuted)
            }
        }
        if (result.last7Marks.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            StreakWeekStrip(marks = result.last7Marks, dotSize = 13.dp)
        }
        if (showStepGoalHint) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Set a daily step goal in Profile to start this streak.",
                style = AppType.cardSubtitle,
                color = appColors.textMuted,
            )
        }
    }
}
