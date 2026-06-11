package com.zack.recomptracker.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zack.recomptracker.ai.SignalLine
import com.zack.recomptracker.ai.WeeklyBriefing
import com.zack.recomptracker.domain.review.SignalDirection
import com.zack.recomptracker.ui.component.AiBadge
import com.zack.recomptracker.ui.component.AiBorderMode
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.aiIridescentRim
import com.zack.recomptracker.ui.theme.CardBorder
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.TextFaint
import com.zack.recomptracker.ui.theme.TextMuted

private val ModalCorner = 24.dp

@Composable
fun WeeklyBriefingOverlay(
    state: WeeklyReviewUiState,
    pendingApply: Int?,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit,
    onRequestApply: (Int) -> Unit,
    onConfirmApply: () -> Unit,
    onCancelApply: () -> Unit,
    onDiscussWithCoach: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (state is WeeklyReviewUiState.Hidden) return

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val borderMode = when (state) {
            WeeklyReviewUiState.Generating -> AiBorderMode.Generating
            is WeeklyReviewUiState.Ready -> AiBorderMode.Ready
            else -> AiBorderMode.Static
        }
        BriefingGlassCard(
            borderMode = borderMode,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            BriefingHeader(
                showRefresh = state is WeeklyReviewUiState.Ready && pendingApply == null,
                onRefresh = onRegenerate,
            )
            Spacer(Modifier.height(12.dp))

            when (state) {
                WeeklyReviewUiState.Hidden -> Unit
                WeeklyReviewUiState.Upsell -> UpsellBody(onOpenSettings)
                is WeeklyReviewUiState.InsufficientData -> InsufficientBody(state.daysRemaining)
                WeeklyReviewUiState.Generating -> GeneratingBody()
                is WeeklyReviewUiState.Error -> ErrorBody(state.message, onRegenerate)
                is WeeklyReviewUiState.Ready -> ReadyBody(
                    briefing = state.briefing,
                    pendingApply = pendingApply,
                    onRequestApply = onRequestApply,
                    onConfirmApply = onConfirmApply,
                    onCancelApply = onCancelApply,
                    onDiscussWithCoach = onDiscussWithCoach,
                )
            }

            Spacer(Modifier.height(16.dp))
            BriefingGhostButton(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Backdrop-free AI glass card — a dark translucent surface with a top sheen, hairline edge, and
 *  the shared iridescent rim. Renders correctly inside a Dialog window (no backdrop layer). */
@Composable
private fun BriefingGlassCard(
    borderMode: AiBorderMode,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = LocalAppAccent.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ModalCorner))
            .background(Color(0xF2140F22))
            .drawBehind {
                drawRect(accent.accent.copy(alpha = 0.05f))
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        0.10f to Color.Transparent,
                    ),
                )
            }
            .border(1.dp, CardBorder, RoundedCornerShape(ModalCorner))
            .aiIridescentRim(borderMode, ModalCorner)
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        content = content,
    )
}

@Composable
private fun BriefingHeader(showRefresh: Boolean, onRefresh: () -> Unit) {
    val accent = LocalAppAccent.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "WEEKLY REVIEW",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextFaint,
            letterSpacing = 0.14.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showRefresh) {
                Text(
                    text = "↺",
                    fontSize = 15.sp,
                    color = accent.accentLight,
                    modifier = Modifier.clickable(onClick = onRefresh).padding(2.dp),
                )
            }
            AiBadge()
        }
    }
}

// ── States ───────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.UpsellBody(onOpenSettings: () -> Unit) {
    Text(
        text = "Your weekly AI breakdown",
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        letterSpacing = (-0.3).sp,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Weekly Review runs on cloud AI. Turn it on to get an AI read of your week — what's working, and what to change.",
        fontSize = 14.sp,
        color = TextMuted,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(16.dp))
    BriefingPrimaryButton(text = "Enable cloud AI", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.InsufficientBody(daysRemaining: Int) {
    Text(
        text = "Building your first review",
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        letterSpacing = (-0.3).sp,
    )
    Spacer(Modifier.height(6.dp))
    val dayWord = if (daysRemaining == 1) "day" else "days"
    Text(
        text = "Keep logging — about $daysRemaining more $dayWord of data and your first AI briefing unlocks.",
        fontSize = 14.sp,
        color = TextMuted,
        lineHeight = 20.sp,
    )
}

@Composable
private fun ColumnScope.GeneratingBody() {
    val accent = LocalAppAccent.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = accent.accentLight,
        )
        Spacer(Modifier.width(12.dp))
        Text("Reading your week…", fontSize = 14.sp, color = Color.White)
    }
}

@Composable
private fun ColumnScope.ErrorBody(message: String, onRetry: () -> Unit) {
    Text(message, fontSize = 14.sp, color = TextMuted, lineHeight = 20.sp)
    Spacer(Modifier.height(16.dp))
    BriefingPrimaryButton(text = "Try again", onClick = onRetry, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ColumnScope.ReadyBody(
    briefing: WeeklyBriefing,
    pendingApply: Int?,
    onRequestApply: (Int) -> Unit,
    onConfirmApply: () -> Unit,
    onCancelApply: () -> Unit,
    onDiscussWithCoach: () -> Unit,
) {
    // Headline + narrative
    Text(
        text = briefing.headline,
        fontSize = 17.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        lineHeight = 23.sp,
        letterSpacing = (-0.3).sp,
    )
    if (briefing.narrative.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(briefing.narrative, fontSize = 14.sp, color = Color.White.copy(alpha = 0.90f), lineHeight = 20.sp)
    }

    // Signals
    if (briefing.signals.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        BriefingDivider()
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            briefing.signals.forEach { SignalRow(it) }
        }
    }

    // Recommended action
    Spacer(Modifier.height(16.dp))
    BriefingDivider()
    Spacer(Modifier.height(14.dp))
    SectionLabel(text = "Recommended action")
    Spacer(Modifier.height(6.dp))
    Text(
        text = briefing.action.verdict,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color.White,
        letterSpacing = (-0.2).sp,
    )
    if (briefing.action.rationale.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(briefing.action.rationale, fontSize = 13.sp, color = TextMuted, lineHeight = 18.sp)
    }

    val applyTarget = briefing.action.applyTargetCalories
    if (applyTarget != null) {
        Spacer(Modifier.height(14.dp))
        if (pendingApply != null) {
            ApplyConfirm(target = pendingApply, onConfirm = onConfirmApply, onCancel = onCancelApply)
        } else {
            BriefingPrimaryButton(
                text = "Apply — set target to $applyTarget kcal",
                onClick = { onRequestApply(applyTarget) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Watch next week
    if (briefing.watchNext.isNotBlank()) {
        Spacer(Modifier.height(16.dp))
        BriefingDivider()
        Spacer(Modifier.height(14.dp))
        SectionLabel(text = "Watch next week")
        Spacer(Modifier.height(6.dp))
        Text(briefing.watchNext, fontSize = 13.sp, color = TextMuted, lineHeight = 18.sp)
    }

    // Discuss with coach
    Spacer(Modifier.height(18.dp))
    BriefingPrimaryButton(
        text = "✦  Discuss with coach",
        onClick = onDiscussWithCoach,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SignalRow(signal: SignalLine) {
    val accent = LocalAppAccent.current
    val arrow = when (signal.direction) {
        SignalDirection.UP -> "↑"
        SignalDirection.DOWN -> "↓"
        SignalDirection.FLAT -> "→"
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(signal.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.92f))
            Text(
                text = "${signal.value}  $arrow",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accent.accentLight,
            )
        }
        if (signal.interpretation.isNotBlank()) {
            Text(signal.interpretation, fontSize = 12.5.sp, color = TextMuted, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun ApplyConfirm(target: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Set your daily target to $target kcal?",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfirmChip(
                text = "Cancel",
                color = Color(0xFFEF5350),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            ConfirmChip(
                text = "Apply",
                color = Color(0xFF66BB6A),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Buttons & primitives (backdrop-free, Dialog-safe) ─────────────────────────

@Composable
private fun BriefingPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAppAccent.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100))
            .background(accent.accent.copy(alpha = 0.20f))
            .border(1.dp, accent.tintedBorder, RoundedCornerShape(100))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun BriefingGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.65f))
    }
}

@Composable
private fun ConfirmChip(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun BriefingDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CardBorder))
}
