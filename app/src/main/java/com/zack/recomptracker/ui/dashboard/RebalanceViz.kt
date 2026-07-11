package com.zack.recomptracker.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zack.recomptracker.domain.rebalance.RebalanceDayBar
import com.zack.recomptracker.ui.component.charts.ChartDefaults
import com.zack.recomptracker.ui.component.rememberAnimationsEnabled
import com.zack.recomptracker.ui.theme.AppType
import com.zack.recomptracker.ui.theme.CornerSmall
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Over-target history-day color — the ONE intentional hardcoded hex in this file. Exactly the
 * color `WeekBarItem` uses for `summary.calories > targetHigh`
 * (`ui/component/WeekCalorieStrip.kt`), reused so an "over" day reads identically across both
 * charts. Not themed via `LocalAppAccent`/`LocalAppColors` on purpose — it is a fixed semantic
 * (over-target = orange), independent of the user's accent theme.
 */
private val OverTargetOrange = Color(0xFFF97316)

/**
 * "Success green" for the animated effective-target value in [ConvergenceReadout] — the other
 * intentional hardcoded hex in this file. No dedicated success-green design token exists yet; this
 * matches the app's existing `0xFF34D399` literal used elsewhere for the same semantic
 * (`ui/today/FoodScreen.kt`, `ui/scanner/BarcodeScannerScreen.kt`,
 * `ChartDefaults.MacroColors.Carbs`) — which is also `AccentTheme.EMERALD.accentLight`.
 */
private val SuccessGreen = Color(0xFF34D399)

/**
 * Read-only weekly bars viz for the Weekly Rebalance offer popup / progress detail: the trailing
 * history days (that drove the offer) followed by the plan's lighter days ahead, sharing one
 * vertical scale. Mirrors [com.zack.recomptracker.ui.component.WeekCalorieStrip]'s bar draw (color
 * keyed to over/under target, dashed target line) and
 * [com.zack.recomptracker.ui.component.charts.StackedBarChart]'s per-bar stagger animation — a new,
 * simpler composable rather than a reuse of either (both are shaped around a different data model:
 * `DayCalorieSummary` / `DayMacros`, not [RebalanceDayBar]).
 *
 * Uses [OverTargetOrange] for an over-target history bar (see that constant's doc for
 * justification).
 */
@Composable
internal fun WeeklyBarsChart(
    bars: ImmutableList<RebalanceDayBar>,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) return

    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val animationsEnabled = rememberAnimationsEnabled()

    // coerceAtLeast(1f) guards div-by-zero: an all-zero window (every valueKcal & targetKcal == 0)
    // would otherwise make maxScale 0f and turn every bar height / the target line into NaN. The
    // bars.isEmpty() early-return above already keeps the maxOf {} from throwing on an empty list.
    val maxScale = (bars.maxOf { maxOf(it.valueKcal, it.targetKcal) } * 1.15f).coerceAtLeast(1f)
    val lineTargetKcal = bars.firstOrNull { it.isPlanDay }?.targetKcal ?: bars.first().targetKcal
    val lineFrac = (lineTargetKcal.toFloat() / maxScale).coerceIn(0f, 1f)
    val firstPlanIndex = bars.indexOfFirst { it.isPlanDay }

    // One Animatable per bar, staggered — same idiom as StackedBarChart's per-bar rise.
    val animatables = remember(bars) { List(bars.size) { Animatable(0f) } }
    LaunchedEffect(bars, animationsEnabled) {
        if (!animationsEnabled) {
            animatables.forEach { it.snapTo(1f) }
        } else {
            animatables.forEachIndexed { i, anim ->
                launch {
                    delay(i * ChartDefaults.AnimSpec.barStaggerMs)
                    anim.animateTo(1f, animationSpec = ChartDefaults.AnimSpec.barRise)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .drawBehind {
                    val y = size.height * (1f - lineFrac)
                    drawLine(
                        color = accent.accent.copy(alpha = 0.38f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)),
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { i, bar ->
                val heightFrac = (bar.valueKcal / maxScale).coerceIn(0f, 1f)
                val anim = animatables[i].value
                val isDividerBefore = bar.isPlanDay && i > 0 && !bars[i - 1].isPlanDay

                if (isDividerBefore) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(appColors.cardBorder),
                    )
                }

                val barBrush = when {
                    !bar.isPlanDay && bar.isOver ->
                        // Solid fill (same color twice) so this branch can share the Brush type
                        // used by the other two — intentional: matches WeekCalorieStrip's
                        // over-target bar color exactly.
                        Brush.verticalGradient(listOf(OverTargetOrange, OverTargetOrange))
                    bar.isPlanDay ->
                        Brush.verticalGradient(listOf(accent.accentLighter, accent.accent))
                    else ->
                        Brush.verticalGradient(
                            listOf(
                                appColors.textPrimary.copy(alpha = 0.28f),
                                appColors.textPrimary.copy(alpha = 0.12f),
                            ),
                        )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight((heightFrac * anim).coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(barBrush),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            bars.forEachIndexed { i, bar ->
                val emphasize = i == firstPlanIndex
                Text(
                    text = bar.label,
                    style = AppType.metaLabel,
                    color = if (emphasize) accent.accentLighter else appColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val higherDays = bars.count { !it.isPlanDay && it.isOver }
        val lighterDays = bars.count { it.isPlanDay }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "$higherDays higher days",
                style = AppType.cardSubtitle,
                color = OverTargetOrange,
            )
            Text(text = " → ", style = AppType.cardSubtitle, color = appColors.textDim)
            Text(
                text = "$lighterDays lighter days",
                style = AppType.cardSubtitle,
                color = accent.accentLighter,
            )
        }
    }
}

/** Visual state of a single rebalance day-progress dot. */
internal enum class DayDotState { COMPLETED, TODAY, UPCOMING }

/**
 * State of the [i]-th (0-based) dot for a rebalance on its 1-based [dayX] day. dayX=1 is the first
 * day, so dot 0 is TODAY and nothing is COMPLETED; on the final day (dayX==ofY) the last dot is
 * TODAY (it gets the ring). Converting the 1-based dayX to the 0-based index is the P1-13 fix.
 */
internal fun dotStateFor(i: Int, dayX: Int): DayDotState = when {
    i < dayX - 1 -> DayDotState.COMPLETED
    i == dayX - 1 -> DayDotState.TODAY
    else -> DayDotState.UPCOMING
}

/**
 * A row of `ofY` day-progress dots for the Weekly Rebalance progress detail / dashboard ribbon:
 * filled + check for completed days, a glowing hollow ring for today, plain hollow dots for days
 * ahead. [mini] renders the compact 8dp variant used inline in the dashboard ribbon — no
 * connecting track, no numeric labels.
 */
@Composable
internal fun DayDots(
    dayX: Int,
    ofY: Int,
    mini: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (ofY <= 0) return

    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val animationsEnabled = rememberAnimationsEnabled()
    val dotSize = if (mini) 8.dp else 14.dp

    // Per-dot appear stagger — an Animatable list. The pop plays ONCE: a rememberSaveable flag
    // survives this composable being disposed and re-created as the Today card scrolls out of and
    // back into the LazyColumn, so the dots don't replay the stagger (and re-launch `ofY` coroutines)
    // on every scroll — the reported perf toll. Keyed on `ofY` so a genuinely different plan length
    // animates afresh; the progress-detail dialog composes unflagged and still animates on open.
    var hasAnimated by rememberSaveable(ofY) { mutableStateOf(false) }
    val appearAnims = remember(ofY) {
        List(ofY) { Animatable(if (hasAnimated || !animationsEnabled) 1f else 0f) }
    }
    LaunchedEffect(ofY, animationsEnabled) {
        if (hasAnimated || !animationsEnabled) {
            appearAnims.forEach { it.snapTo(1f) }
        } else {
            appearAnims.forEachIndexed { i, anim ->
                launch {
                    delay(i * ChartDefaults.AnimSpec.barStaggerMs)
                    anim.animateTo(1f, animationSpec = ChartDefaults.AnimSpec.dotPop)
                }
            }
            hasAnimated = true
        }
    }

    // Today's glow pulses only while animations are enabled; otherwise a static ring (no
    // rememberInfiniteTransition is created at all in the disabled branch). The `!mini` guard is
    // load-bearing for performance: the mini variant rides the scrolled dashboard ribbon, and an
    // infinite transition there would invalidate every frame forever — the pulse must stay confined
    // to the non-mini variant, which only ever renders inside the transient progress-detail Dialog.
    val pulseAlpha: Float = if (!mini && animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "dayDotsGlow")
        val pulse by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
            label = "dayDotsGlowPulse",
        )
        pulse
    } else {
        0.6f
    }

    // Mini variant (dashboard ribbon): a tight, wrap-content cluster. Deliberately NOT fillMaxWidth +
    // SpaceBetween — that spread the dots edge-to-edge and starved the ribbon's sibling label to zero
    // width (it then wrapped one character per line, exploding the row's height). No track, no labels.
    if (mini) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until ofY) {
                val st = dotStateFor(i, dayX)
                DayDot(
                    dotSize = dotSize,
                    scale = appearAnims[i].value,
                    completed = st == DayDotState.COMPLETED,
                    isToday = st == DayDotState.TODAY,
                    mini = true,
                    pulseAlpha = pulseAlpha,
                )
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dotSize),
        ) {
            // Connecting track behind the dots — filled up to dayX, dim after.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = dotSize / 2),
            ) {
                for (i in 0 until ofY - 1) {
                    // Segment i (dot i → dot i+1) is filled once dot i is a completed day.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                if (dotStateFor(i, dayX) == DayDotState.COMPLETED) accent.accent
                                else appColors.cardBorder,
                            ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (i in 0 until ofY) {
                    val st = dotStateFor(i, dayX)
                    DayDot(
                        dotSize = dotSize,
                        scale = appearAnims[i].value,
                        completed = st == DayDotState.COMPLETED,
                        isToday = st == DayDotState.TODAY,
                        mini = false,
                        pulseAlpha = pulseAlpha,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (i in 0 until ofY) {
                Text(
                    text = "${i + 1}",
                    style = AppType.metaLabel,
                    color = if (dotStateFor(i, dayX) == DayDotState.TODAY) accent.accentLighter
                    else appColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(dotSize),
                )
            }
        }
    }
}

/** A single day-progress dot: filled + check (done), a glowing ring (today), or a hollow dot (ahead). */
@Composable
private fun DayDot(
    dotSize: Dp,
    scale: Float,
    completed: Boolean,
    isToday: Boolean,
    mini: Boolean,
    pulseAlpha: Float,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(dotSize)
            .scale(scale.coerceIn(0f, 1f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            completed -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accent.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = accent.onAccent,
                        modifier = Modifier.size(dotSize * 0.6f),
                    )
                }
            }
            isToday -> {
                if (!mini) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(accent.accentLight.copy(alpha = 0.20f * pulseAlpha)),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .fillMaxHeight(0.72f)
                        .clip(CircleShape)
                        .border(1.5.dp, accent.accentLight, CircleShape),
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .fillMaxHeight(0.72f)
                        .clip(CircleShape)
                        .border(1.dp, appColors.cardBorder, CircleShape),
                )
            }
        }
    }
}

/**
 * Before/after calorie readout for the Weekly Rebalance offer popup: the base target, an arrow,
 * then the animated effective (reduced) target and its restated target — e.g.
 * "2400 → 2150 kcal · target 2150". The count-up on the reduced value is gated by
 * [rememberAnimationsEnabled]; when disabled the final value is shown directly (no
 * `animateIntAsState` call at all).
 */
@Composable
internal fun ConvergenceReadout(
    fromKcal: Int,
    toKcal: Int,
    targetKcal: Int,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val animationsEnabled = rememberAnimationsEnabled()

    val shown = if (animationsEnabled) {
        val animated by animateIntAsState(targetValue = toKcal, animationSpec = tween(600), label = "convergenceKcal")
        animated
    } else {
        toKcal
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "$fromKcal", style = AppType.cardSubtitle, color = appColors.textDim)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = appColors.textMuted,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "$shown kcal",
            style = AppType.cardSubtitle.copy(fontWeight = FontWeight.Bold),
            color = SuccessGreen,
        )
        Text(text = "· target $targetKcal", style = AppType.cardSubtitle, color = appColors.textDim)
    }
}

/**
 * The three at-a-glance "lever" tiles for the Weekly Rebalance offer popup: daily calorie
 * reduction, extra daily steps, and plan length. A tile whose value is exactly 0 renders muted
 * with an em-dash instead of a number (e.g. a calorie-only plan's steps tile reads "—").
 */
@Composable
internal fun LeverTiles(
    reduction: Int,
    extraSteps: Int,
    days: Int,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    // Keyed on Unit (not the values) so the entrance stagger runs ONCE on first composition. Changing
    // reduction/steps/days via Customize then updates the tile labels in place — the tiles do not
    // replay their fade/slide-in (which read as jank). The number labels are small and need no
    // count-up, so an in-place text swap is all that's wanted.
    val visibleStates = remember { List(3) { mutableStateOf(!animationsEnabled) } }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            visibleStates.forEachIndexed { i, state ->
                launch {
                    delay(i * ChartDefaults.AnimSpec.barStaggerMs)
                    state.value = true
                }
            }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LeverTile(
            value = if (reduction == 0) null else "−$reduction",
            caption = "KCAL / DAY",
            visible = visibleStates[0].value,
            modifier = Modifier.weight(1f),
        )
        LeverTile(
            value = if (extraSteps == 0) null else "+${formatK(extraSteps)}",
            caption = "STEPS / DAY",
            visible = visibleStates[1].value,
            modifier = Modifier.weight(1f),
        )
        LeverTile(
            value = if (days == 0) null else "$days",
            caption = "DAYS",
            visible = visibleStates[2].value,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LeverTile(
    value: String?,
    caption: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(220),
        label = "leverTileAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 6f,
        animationSpec = tween(220),
        label = "leverTileOffset",
    )
    val isPlaceholder = value == null

    Column(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha; translationY = offsetY.dp.toPx() }
            .background(appColors.cardSurface, RoundedCornerShape(CornerSmall))
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(CornerSmall))
            .padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value ?: "—",
            style = AppType.statValueSmall,
            color = if (isPlaceholder) appColors.textPrimary.copy(alpha = 0.42f) else appColors.textPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = caption,
            style = AppType.metaLabel,
            color = appColors.textMuted,
        )
    }
}

/** "1200" -> "1.2k"; values under 1000 render as-is with no decimal. */
internal fun formatK(value: Int): String {
    if (value < 1000) return "$value"
    val thousands = value / 1000f
    return if (thousands == thousands.toInt().toFloat()) {
        "${thousands.toInt()}k"
    } else {
        "%.1fk".format(thousands)
    }
}

// ── Previews (dark theme) ──────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewWeeklyBarsChart() {
    val bars = persistentListOf(
        RebalanceDayBar("Mon", 2650, 2200, isPlanDay = false, isOver = true),
        RebalanceDayBar("Tue", 2100, 2200, isPlanDay = false, isOver = false),
        RebalanceDayBar("Wed", 2800, 2200, isPlanDay = false, isOver = true),
        RebalanceDayBar("Thu", 2900, 2200, isPlanDay = false, isOver = true),
        RebalanceDayBar("Fri", 2050, 2200, isPlanDay = false, isOver = false),
        RebalanceDayBar("Sat", 3100, 2200, isPlanDay = false, isOver = true),
        RebalanceDayBar("Sun", 1950, 2200, isPlanDay = false, isOver = false),
        RebalanceDayBar("Mon", 2050, 2200, isPlanDay = true, isOver = false),
        RebalanceDayBar("Tue", 2050, 2200, isPlanDay = true, isOver = false),
        RebalanceDayBar("Wed", 2050, 2200, isPlanDay = true, isOver = false),
        RebalanceDayBar("Thu", 2050, 2200, isPlanDay = true, isOver = false),
    )
    WeeklyBarsChart(bars = bars)
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewDayDots() {
    DayDots(dayX = 2, ofY = 4)
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewConvergenceReadout() {
    ConvergenceReadout(fromKcal = 2400, toKcal = 2150, targetKcal = 2150)
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0818)
@Composable
private fun PreviewLeverTiles() {
    LeverTiles(reduction = 250, extraSteps = 1200, days = 3)
}
