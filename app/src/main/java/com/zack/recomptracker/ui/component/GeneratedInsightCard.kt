package com.zack.recomptracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Generation-only renderer for the expanded per-kind insights (Progress / Recovery / Food).
 *
 * Renders the streaming ([AiInsightState.Generating]), finished ([AiInsightState.Ready]), and
 * [AiInsightState.Error] states as an [AiInsightCard]. Renders NOTHING for every model-lifecycle
 * state (Disabled / ModelMissing / Downloading / DownloadFailed / ModelVerifying / ModelReady /
 * LoadingModel) — model download/management lives on the dashboard card and the More screen, so
 * these surfaces never duplicate it. The card simply appears once generation produces text.
 */
@Composable
fun GeneratedInsightCard(
    title: String,
    state: AiInsightState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    when (state) {
        is AiInsightState.Generating -> {
            AiInsightCard(borderMode = AiBorderMode.Generating, modifier = modifier) {
                InsightCardHeader(title = title, showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text(text = state.partialText, fontSize = 14.sp, color = appColors.textPrimary, lineHeight = 20.sp)
            }
        }
        is AiInsightState.Ready -> {
            AiInsightCard(borderMode = AiBorderMode.Ready, modifier = modifier) {
                InsightCardHeader(title = title, showRefresh = true, onRefresh = onRetry)
                Spacer(Modifier.height(8.dp))
                Text(text = state.text, fontSize = 14.sp, color = appColors.textPrimary, lineHeight = 20.sp)
            }
        }
        is AiInsightState.Error -> {
            AiInsightCard(borderMode = AiBorderMode.Static, modifier = modifier) {
                InsightCardHeader(title = title, showRefresh = false, onRefresh = {})
                Spacer(Modifier.height(8.dp))
                Text(text = state.message, fontSize = 13.sp, color = appColors.textMuted)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
        else -> Unit
    }
}

@Composable
private fun InsightCardHeader(title: String, showRefresh: Boolean, onRefresh: () -> Unit) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = appColors.textFaint,
            letterSpacing = 0.14.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showRefresh) {
                IconButton(onClick = onRefresh) {
                    Text("↺", fontSize = 14.sp, color = accent.accentLight)
                }
            }
            AiBadge()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewGenerating() {
    GeneratedInsightCard(
        title = "Trend analysis",
        state = AiInsightState.Generating("Your weight held steady while your waist trended down…"),
        onRetry = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewReady() {
    GeneratedInsightCard(
        title = "Recovery readiness",
        state = AiInsightState.Ready("Two short nights with high soreness — prioritize sleep tonight."),
        onRetry = {},
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewError() {
    GeneratedInsightCard(
        title = "Rest of day",
        state = AiInsightState.Error("Something went wrong — try again."),
        onRetry = {},
    )
}
