package com.zack.recomptracker.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.vector.ImageVector
import com.zack.recomptracker.domain.streak.StreakDayMark
import com.zack.recomptracker.domain.streak.StreakResult
import com.zack.recomptracker.domain.streak.StreakType
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerPill
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors

// ─────────────────────────────────────────────────────────────────────────────
// Shared liquid-glass streak UI kit. One file, called by four screens
// (Dashboard, Food Log, Training, Body). Replaces the old emoji UI.
//
// The only hardcoded colors in this file are the two named vals below — they are
// INTENTIONAL fixed accents (a flame is warm-orange, goal-met is green) that must
// read the same in light and dark themes. Everything else uses design tokens.
// ─────────────────────────────────────────────────────────────────────────────

/** Intentional fixed accent: the warm-orange "streak" flame, theme-independent. */
val StreakFlameColor: Color = Color(0xFFFB923C)

/** Intentional fixed accent: the goal-met green, theme-independent. */
val StreakGoalMetColor: Color = Color(0xFF4ADE80)

/** Material icon that identifies a streak's modality (shown inside the coin tile). */
fun streakIcon(type: StreakType): ImageVector = when (type) {
    StreakType.WORKOUT -> Icons.Filled.FitnessCenter
    StreakType.CALORIE -> Icons.Filled.LocalFireDepartment
    StreakType.STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
}

/** Human-readable streak name. */
fun streakLabel(type: StreakType): String = when (type) {
    StreakType.WORKOUT -> "Workout"
    StreakType.CALORIE -> "Calorie"
    StreakType.STEPS -> "Steps"
}

// ── Coin ──────────────────────────────────────────────────────────────────────

/** Accent-tinted rounded tile carrying the streak's modality icon. */
@Composable
fun StreakCoin(
    type: StreakType,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val accent = LocalAppAccent.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(accent.tintedSurface)
            .border(1.dp, accent.tintedBorder, RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = streakIcon(type),
            contentDescription = null,
            tint = accent.accentLight,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

// ── Week strip ────────────────────────────────────────────────────────────────

/** A row of last-7 day dots. Empty list renders nothing. */
@Composable
fun StreakWeekStrip(
    marks: List<StreakDayMark>,
    modifier: Modifier = Modifier,
    dotSize: Dp = 9.dp,
) {
    if (marks.isEmpty()) return
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        marks.forEach { mark ->
            when (mark) {
                StreakDayMark.HIT -> Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(accent.accent),
                )
                StreakDayMark.REST -> Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .border(1.5.dp, accent.accent, CircleShape),
                )
                StreakDayMark.MISS -> Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(appColors.cardBorder),
                )
            }
        }
    }
}

// ── Count + flame ─────────────────────────────────────────────────────────────

/** Inline streak count (textPrimary) followed by a small warm flame. */
@Composable
fun StreakCountFlame(
    count: Int,
    numberStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = count.toString(), style = numberStyle, color = appColors.textPrimary)
        Icon(
            imageVector = Icons.Filled.Whatshot,
            contentDescription = null,
            tint = StreakFlameColor,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Compact row ───────────────────────────────────────────────────────────────

/**
 * Compact list row: coin + inline count/flame + label + week strip, with a
 * trailing "Best N" stat. Used in stacked lists (e.g. the streak detail screen).
 */
@Composable
fun StreakRow(
    result: StreakResult,
    type: StreakType,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StreakCoin(type = type, size = 40.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StreakCountFlame(count = result.current, numberStyle = AppType.statValueSmall)
                Text(
                    text = streakLabel(type),
                    style = AppType.metaLabel,
                    color = appColors.textMuted,
                )
            }
            StreakWeekStrip(marks = result.last7Marks)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "Best", style = AppType.metaLabel, color = appColors.textMuted)
            Text(
                text = result.longest.toString(),
                style = AppType.statValueSmall,
                color = accent.accentLighter,
            )
        }
    }
}

// ── Hero banner ───────────────────────────────────────────────────────────────

private val WeekdayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

/** Compact tinted-glass banner for a single streak (the Training hero). */
@Composable
fun StreakHeroBanner(
    result: StreakResult,
    type: StreakType,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    FrostedCard(
        modifier = modifier,
        contentPadding = 14.dp,
        surfaceTint = accent.accent.copy(alpha = 0.10f),
        borderColor = accent.tintedBorder,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StreakCoin(type = type, size = 38.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                StreakCountFlame(count = result.current, numberStyle = AppType.statValueSmall)
                Text(
                    text = "${streakLabel(type)} streak",
                    style = AppType.metaLabel,
                    color = appColors.textMuted,
                )
            }
            if (result.last7Marks.isNotEmpty()) {
                StreakWeekStrip(marks = result.last7Marks, dotSize = 8.dp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = result.longest.toString(),
                    style = AppType.statValueSmall,
                    color = accent.accentLighter,
                )
                Text(text = "BEST", style = AppType.metaLabel, color = appColors.textMuted)
            }
        }
    }
}

// ── Goal ring ─────────────────────────────────────────────────────────────────

/**
 * Frosted card with a progress ring against a daily goal (e.g. steps). When
 * [goalValue] is null the ring renders at zero and a "set a goal" hint is shown.
 */
@Composable
fun StreakGoalRing(
    result: StreakResult,
    type: StreakType,
    todayValue: Int,
    goalValue: Int?,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    val progress = if (goalValue != null && goalValue > 0) {
        (todayValue.toFloat() / goalValue.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    FrostedCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(74.dp),
                contentAlignment = Alignment.Center,
            ) {
                val trackColor = appColors.cardBorder
                val progressColor =
                    if (goalValue != null && todayValue >= goalValue) StreakGoalMetColor else accent.accent
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 7.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                Icon(
                    imageVector = streakIcon(type),
                    contentDescription = null,
                    tint = accent.accentLight,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StreakCountFlame(count = result.current, numberStyle = AppType.statValueSmall)
                    Text(
                        text = "${streakLabel(type).lowercase()} streak",
                        style = AppType.metaLabel,
                        color = appColors.textMuted,
                    )
                }
                when {
                    goalValue == null -> Text(
                        text = "Set a daily ${streakLabel(type).lowercase()} goal in Profile.",
                        style = AppType.cardSubtitle,
                        color = appColors.textMuted,
                    )
                    todayValue >= goalValue -> Text(
                        text = "$todayValue / $goalValue — goal hit",
                        style = AppType.cardSubtitle,
                        color = StreakGoalMetColor,
                    )
                    else -> Text(
                        text = "$todayValue / $goalValue",
                        style = AppType.cardSubtitle,
                        color = appColors.textMuted,
                    )
                }
                Text(
                    text = "Best ${result.longest} days",
                    style = AppType.cardSubtitle,
                    color = appColors.textMuted,
                )
            }
        }
    }
}

// ── Calorie badge (compact pill) ──────────────────────────────────────────────

/** Small tappable pill — a warm flame + the current count. For inline placement. */
@Composable
fun CalorieStreakBadge(
    current: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(CornerPill))
            .background(accent.tintedSurface)
            .border(1.dp, accent.tintedBorder, RoundedCornerShape(CornerPill))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Calorie streak: $current days" }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Whatshot,
            contentDescription = null,
            tint = StreakFlameColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = current.toString(),
            style = AppType.label,
            color = appColors.textPrimary,
        )
    }
}

// ── Detail popup dialog ───────────────────────────────────────────────────────

private fun streakFooter(type: StreakType, current: Int): String = when (type) {
    StreakType.WORKOUT -> "You've trained $current days running. Keep the chain alive."
    StreakType.CALORIE -> "You've stayed inside your calorie zone $current days running."
    StreakType.STEPS -> "You've hit your step goal $current days running."
}

/**
 * The streak detail card body. Designed to expand INLINE as part of a screen (e.g. under the
 * day's calorie card) — not a floating modal. Wrap it in an `AnimatedVisibility` at the call
 * site for the expand/collapse animation. Pass [onCollapse] to show a collapse chevron.
 */
@Composable
fun StreakDetailContent(
    result: StreakResult,
    type: StreakType,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null,
) {
    val appColors = LocalAppColors.current
    val accent = LocalAppAccent.current
    FrostedCard(modifier = modifier, contentPadding = 18.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StreakCoin(type = type, size = 44.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StreakCountFlame(count = result.current, numberStyle = AppType.statValue)
                Text(
                    text = "${streakLabel(type)} streak",
                    style = AppType.metaLabel,
                    color = appColors.textMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = result.longest.toString(),
                        style = AppType.statValueSmall,
                        color = accent.accentLighter,
                    )
                    Text(text = "/ BEST", style = AppType.metaLabel, color = appColors.textMuted)
                }
            }
            if (onCollapse != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onCollapse,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Collapse",
                        tint = appColors.textMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (result.last7Marks.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            StreakWeekStrip(marks = result.last7Marks, dotSize = 13.dp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val count = result.last7Marks.size
                WeekdayLetters.takeLast(count).forEach { letter ->
                    Box(modifier = Modifier.size(13.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = letter,
                            style = AppType.metaLabel,
                            color = appColors.textVeryMuted,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = streakFooter(type, result.current),
            style = AppType.body,
            color = appColors.textSecondary,
        )
    }
}

// ── Speech bubble ─────────────────────────────────────────────────────────────

/**
 * Comic-style speech bubble: a small tail pointing up toward the streak badge, with the
 * [StreakDetailContent] card below it. Wrap it in an `AnimatedVisibility` whose enter
 * scales from the top-right (the badge) for a "pop" effect.
 */
@Composable
fun StreakSpeechBubble(
    result: StreakResult,
    type: StreakType,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        // Tail pointing up at the badge that opened the bubble.
        Canvas(
            modifier = Modifier
                .padding(end = 20.dp)
                .size(width = 20.dp, height = 10.dp),
        ) {
            val tail = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(tail, color = appColors.frostedSurfaceFallback)
        }
        StreakDetailContent(
            result = result,
            type = type,
            onCollapse = onCollapse,
            // Overlap the tail base by 1dp so there's no seam between tail and card.
            modifier = Modifier.offset(y = (-1).dp),
        )
    }
}
