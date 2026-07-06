package com.zack.recomptracker.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.data.usage.NoOpUsageTracker
import com.zack.recomptracker.data.usage.UsageEvents
import com.zack.recomptracker.ui.LocalAppContainer
import com.zack.recomptracker.ui.liquidglass.LocalBackdrop
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
) {
    val appColors = LocalAppColors.current
    when (state) {
        is AiInsightState.Generating ->
            // Loading state matches the collapsed-by-default cards: a glass pill with a compact
            // shimmer. The full uncollapsed loading card is kept as [InsightGeneratingExpanded].
            InsightGeneratingCollapsed(modifier = modifier)
        is AiInsightState.Ready -> ReadyCard(
            title, state.text, variant, evidence, confidence,
            onRetry, onTellMeMore, onFeedback, modifier,
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

/**
 * Full (uncollapsed) loading card — header + shimmer skeleton, or the streaming text once it
 * arrives. Kept as a reusable building block; the default Generating state renders the collapsed
 * pill via [InsightGeneratingCollapsed].
 */
@Composable
fun InsightGeneratingExpanded(title: String, partialText: String, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    AiInsightCard(borderMode = AiBorderMode.Generating, modifier = modifier) {
        InsightCardHeader(title = title, collapsible = false, collapsed = false, onToggle = {})
        Spacer(Modifier.height(10.dp))
        if (partialText.isBlank()) {
            InsightShimmerLines()
        } else {
            Text(partialText, fontSize = 14.sp, color = appColors.textPrimary, lineHeight = 20.sp)
        }
    }
}

/** Collapsed loading pill: sparkle + a compact shimmer, with the Generating edge glow. */
@Composable
private fun InsightGeneratingCollapsed(modifier: Modifier = Modifier) {
    AiInsightCard(
        borderMode = AiBorderMode.Generating,
        collapsed = true,
        contentPadding = 12.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✦", fontSize = 13.sp, color = LocalAppAccent.current.inkLight)
            InsightShimmerBar(modifier = Modifier.weight(1f))
        }
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
    modifier: Modifier,
) {
    val appColors = LocalAppColors.current
    // All cards start collapsed (a glass pill); tapping the toggle expands. Remembered across scroll.
    var collapsed by rememberSaveable(title) { mutableStateOf(true) }
    val verdictSize = if (variant == InsightCardVariant.HERO) 20.sp else 16.sp

    // Local usage tracking (label = card kind = title). SHOWN fires once per Ready appearance;
    // TAPPED fires on expand; DISMISSED fires on collapse. Fire-and-forget — never blocks the UI.
    // No-op under @Preview (LocalInspectionMode), where no AppContainer is provided.
    val usageTracker = if (LocalInspectionMode.current) NoOpUsageTracker else LocalAppContainer.current.usageTracker
    LaunchedEffect(title) { usageTracker.track(UsageEvents.INSIGHT_SHOWN, label = title) }

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
            onToggle = {
                val nowCollapsed = !collapsed
                collapsed = nowCollapsed
                usageTracker.track(
                    if (nowCollapsed) UsageEvents.INSIGHT_DISMISSED else UsageEvents.INSIGHT_TAPPED,
                    label = title,
                )
            },
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
            InsightActions(onTellMeMore, onFeedback, onRetry)
        }
    }
}

@Composable
internal fun InsightCardHeader(
    title: String,
    collapsible: Boolean,
    collapsed: Boolean,
    onToggle: () -> Unit,
    collapsedVerdict: String? = null,
    confidence: ConfidenceLevel? = null,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth().let {
            if (collapsible) {
                it.clickable(
                    onClickLabel = if (collapsed) "Expand insight" else "Collapse insight",
                    role = Role.Button,
                    onClick = onToggle,
                )
            } else {
                it
            }
        },
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
            AiExpandToggle(collapsed = collapsed)
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
    onRetry: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onTellMeMore != null) ActionChip("Tell me more", accent.inkLight, onTellMeMore)
        if (onFeedback != null) {
            IconAction(glyph = "♡", description = "Mark insight helpful", color = appColors.textFaint) { onFeedback(true) }
        }
        Spacer(Modifier.weight(1f))
        // Small liquid-glass refresh button (nav-bar material).
        GlassIconButton(icon = Icons.Rounded.Refresh, contentDescription = "Refresh insight", onClick = onRetry)
    }
}

@Composable
private fun ActionChip(text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(text, fontSize = 12.sp, color = color) }
}

@Composable
private fun IconAction(glyph: String, description: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 15.sp, color = color)
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
    )
}
