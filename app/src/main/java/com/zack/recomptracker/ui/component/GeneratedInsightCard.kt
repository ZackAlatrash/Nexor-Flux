package com.zack.recomptracker.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

/**
 * Variant-aware, collapsible AI insight card. One engine; the [variant] sets prominence and
 * default expansion. Collapsed = a glass pill (label + one-line verdict); expanded = verdict,
 * optional [evidence], optional [confidence], and the action row. Backward compatible: the first
 * three params are unchanged, all others default.
 */
@Composable
fun GeneratedInsightCard(
    title: String,
    state: AiInsightState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    variant: InsightCardVariant = InsightCardVariant.STANDARD,
    evidence: String? = null,
    confidence: ConfidenceLevel? = null,
    onTellMeMore: (() -> Unit)? = null,
    onFeedback: ((helpful: Boolean) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val appColors = LocalAppColors.current
    when (state) {
        is AiInsightState.Generating -> {
            AiInsightCard(borderMode = AiBorderMode.Generating, modifier = modifier) {
                InsightCardHeader(title = title, collapsible = false, collapsed = false, onToggle = {})
                Spacer(Modifier.height(10.dp))
                if (state.partialText.isBlank()) {
                    InsightShimmerLines()
                } else {
                    Text(state.partialText, fontSize = 14.sp, color = appColors.textPrimary, lineHeight = 20.sp)
                }
            }
        }
        is AiInsightState.Ready -> ReadyCard(
            title, state.text, variant, evidence, confidence,
            onRetry, onTellMeMore, onFeedback, onDismiss, modifier,
        )
        is AiInsightState.Error -> {
            AiInsightCard(borderMode = AiBorderMode.Static, modifier = modifier) {
                InsightCardHeader(title = title, collapsible = false, collapsed = false, onToggle = {})
                Spacer(Modifier.height(8.dp))
                Text(state.message, fontSize = 13.sp, color = appColors.textMuted)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
        else -> Unit
    }
}

@Composable
private fun ReadyCard(
    title: String,
    verdict: String,
    variant: InsightCardVariant,
    evidence: String?,
    confidence: ConfidenceLevel?,
    onRetry: () -> Unit,
    onTellMeMore: (() -> Unit)?,
    onFeedback: ((Boolean) -> Unit)?,
    onDismiss: (() -> Unit)?,
    modifier: Modifier,
) {
    val appColors = LocalAppColors.current
    var collapsed by remember { mutableStateOf(variant == InsightCardVariant.PILL) }
    val verdictSize = if (variant == InsightCardVariant.HERO) 20.sp else 16.sp

    AiInsightCard(
        borderMode = AiBorderMode.Ready,
        modifier = modifier.animateContentSize(spring()),
        collapsed = collapsed,
        contentPadding = if (collapsed) 12.dp else 16.dp,
    ) {
        InsightCardHeader(
            title = title,
            collapsible = true,
            collapsed = collapsed,
            collapsedVerdict = verdict,
            confidence = confidence,
            onToggle = { collapsed = !collapsed },
        )
        if (!collapsed) {
            Spacer(Modifier.height(10.dp))
            Text(
                verdict,
                fontSize = verdictSize,
                fontWeight = FontWeight.SemiBold,
                color = appColors.textPrimary,
                lineHeight = (verdictSize.value + 5).sp,
            )
            if (evidence != null) {
                Spacer(Modifier.height(6.dp))
                Text(evidence, fontSize = 12.5f.sp, color = appColors.textMuted, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            InsightActions(onTellMeMore, onFeedback, onDismiss, onRetry)
        }
    }
}

@Composable
private fun InsightCardHeader(
    title: String,
    collapsible: Boolean,
    collapsed: Boolean,
    onToggle: () -> Unit,
    collapsedVerdict: String? = null,
    confidence: ConfidenceLevel? = null,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().let { if (collapsible) it.clickable(onClick = onToggle) else it },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✦", fontSize = 13.sp, color = LocalAppAccent.current.inkLight)
        if (collapsed && collapsedVerdict != null) {
            Text(collapsedVerdict, fontSize = 13.sp, color = appColors.textPrimary, maxLines = 1, modifier = Modifier.weight(1f))
        } else {
            Text(
                title.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.textFaint,
                letterSpacing = 0.14.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (confidence != null && !collapsed) ConfidenceBadge(confidence)
        if (collapsible) {
            val rot by animateFloatAsState(if (collapsed) -90f else 0f, tween(450), label = "chev")
            Text("▾", fontSize = 13.sp, color = appColors.textFaint, modifier = Modifier.rotate(rot))
        } else {
            AiBadge()
        }
    }
}

@Composable
private fun ConfidenceBadge(level: ConfidenceLevel) {
    val (text, color) = when (level) {
        ConfidenceLevel.HIGH -> "High" to Color(0xFF6EFFD8)
        ConfidenceLevel.MEDIUM -> "Medium" to Color(0xFFFFD27A)
    }
    Row(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) { Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color) }
}

@Composable
private fun InsightActions(
    onTellMeMore: (() -> Unit)?,
    onFeedback: ((Boolean) -> Unit)?,
    onDismiss: (() -> Unit)?,
    onRetry: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onTellMeMore != null) ActionChip("Tell me more", accent.inkLight, onTellMeMore)
        ActionChip("Refresh", appColors.textMuted, onRetry)
        Spacer(Modifier.weight(1f))
        if (onFeedback != null) {
            Text("♡", fontSize = 15.sp, color = appColors.textFaint,
                modifier = Modifier.clickable { onFeedback(true) }.padding(horizontal = 2.dp))
        }
        if (onDismiss != null) {
            Text("✕", fontSize = 14.sp, color = appColors.textFaint,
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 2.dp))
        }
    }
}

@Composable
private fun ActionChip(text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(text, fontSize = 12.sp, color = color) }
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

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewHero() {
    GeneratedInsightCard(
        title = "Pattern · this week",
        state = AiInsightState.Ready("Weekends are where protein slips."),
        onRetry = {},
        variant = InsightCardVariant.HERO,
        evidence = "Under target 4 of 7 days — Sat & Sun averaged 38g below your 180g goal.",
        confidence = ConfidenceLevel.HIGH,
        onTellMeMore = {},
        onFeedback = {},
        onDismiss = {},
    )
}
