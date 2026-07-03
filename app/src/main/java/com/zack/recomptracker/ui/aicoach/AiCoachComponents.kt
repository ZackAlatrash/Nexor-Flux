package com.zack.recomptracker.ui.aicoach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ui.component.VioletToggle
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

// ── Cloud provider quick-fill presets ────────────────────────────────────────────

private val CLOUD_PRESETS = listOf(
    "OpenRouter" to "https://openrouter.ai/api/v1",
    "OpenAI" to "https://api.openai.com/v1",
    "Groq" to "https://api.groq.com/openai/v1",
    "NVIDIA" to "https://integrate.api.nvidia.com/v1",
)

@Composable
internal fun ProviderPresetChips(onPick: (String) -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CLOUD_PRESETS.forEach { (label, url) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(appColors.cardSurface)
                    .border(1.dp, appColors.cardBorder, RoundedCornerShape(7.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPick(url) },
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = appColors.textPrimary.copy(alpha = 0.60f))
            }
        }
    }
}

// ── Cloud connection status line ─────────────────────────────────────────────────

@Composable
internal fun CloudStatusLine(
    testingConnection: Boolean,
    testConnectionResult: String?,
    cloudHasKey: Boolean,
) {
    val appColors = LocalAppColors.current
    val ok = testConnectionResult == "Connection OK"
    val (dot, label) = when {
        testingConnection -> Color(0xFFFBBF24) to "Testing…"
        ok -> Color(0xFF4ADE80) to "Connected"
        testConnectionResult != null -> Color(0xFFFC8181) to testConnectionResult
        cloudHasKey -> appColors.textDim to "Configured · not tested"
        else -> appColors.textDim to "Not configured"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(label, fontSize = 12.sp, color = dot.copy(alpha = 0.95f), lineHeight = 16.sp)
    }
}

// ── Notification setting rows (Phase 5 — quiet-by-default opt-out) ────────────────

/**
 * A labelled toggle row for a single notification preference, styled to match the "Enable AI"
 * master toggle: a title + supporting subtitle on the left, a [VioletToggle] on the right.
 * Rendered inside a [TintedCard] by the caller (AI-feature surface).
 */
@Composable
internal fun NotificationSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AppType.cardTitle, color = appColors.textPrimary)
            Text(text = subtitle, style = AppType.label, color = appColors.textMuted)
        }
        VioletToggle(
            label = "",
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.width(64.dp),
        )
    }
}

/**
 * The read-only quiet-hours row: title + subtitle + the configured window on the trailing edge.
 * Read-only for now — there is no existing in-app time-range picker pattern to reuse, and the
 * window is editable via the data layer / defaults. See the task note.
 */
@Composable
internal fun QuietHoursRow(
    subtitle: String,
    windowDisplay: String,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Quiet hours", style = AppType.cardTitle, color = appColors.textPrimary)
            Text(text = subtitle, style = AppType.label, color = appColors.textMuted)
        }
        Text(text = windowDisplay, style = AppType.cardTitle, color = appColors.textSecondary)
    }
}
