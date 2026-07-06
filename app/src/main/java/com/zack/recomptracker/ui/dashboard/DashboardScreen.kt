package com.zack.recomptracker.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zack.recomptracker.core.util.formatPercent
import com.zack.recomptracker.core.util.formatSignedOneDecimal
import com.zack.recomptracker.data.usage.UsageEvents
import com.zack.recomptracker.domain.adjustment.AdjustmentResult
import com.zack.recomptracker.domain.adjustment.AdjustmentVerdict
import com.zack.recomptracker.ui.LocalAppContainer
import com.zack.recomptracker.ui.component.AiBadge
import coil.compose.AsyncImage
import com.zack.recomptracker.ui.component.charts.CalorieProgressBar
import com.zack.recomptracker.ui.component.charts.ChartDefaults
import com.zack.recomptracker.ui.component.charts.SparklineChart
import com.zack.recomptracker.ui.component.FrostedCard
import com.zack.recomptracker.ui.component.VioletBadge
import com.zack.recomptracker.ui.component.SectionLabel
import com.zack.recomptracker.ui.component.TintedCard
import com.zack.recomptracker.ui.component.rememberAnimationsEnabled
import com.zack.recomptracker.ui.FloatingNavHeight
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.theme.ErrorRed
import com.zack.recomptracker.ui.theme.LocalAppAccent
import com.zack.recomptracker.ui.theme.LocalAppColors
import com.zack.recomptracker.ui.review.WeeklyBriefingOverlay
import com.zack.recomptracker.ui.review.WeeklyReviewViewModel
import com.zack.recomptracker.ui.streak.StreakViewModel
import com.zack.recomptracker.ui.streak.StreakRow
import com.zack.recomptracker.ui.toast.LocalToastController
import com.zack.recomptracker.ui.toast.ToastMessage
import com.zack.recomptracker.ui.toast.ToastType
import kotlinx.coroutines.launch
import com.zack.recomptracker.ui.streak.StreakGoalRing
import com.zack.recomptracker.domain.streak.StreakType
import com.zack.recomptracker.domain.streak.Streaks
import com.zack.recomptracker.domain.activity.ActivityMetrics
import com.zack.recomptracker.ui.theme.AppType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Coach action types the dashboard can navigate for — a Today's-Coaching button shows only for these
 *  (mirrors the `onCoachAction` mapping), so a signal never renders a dead button. */
private val SUPPORTED_COACH_ACTIONS = setOf(
    com.zack.recomptracker.domain.coach.CoachActionType.OPEN_WEEKLY_REVIEW,
    com.zack.recomptracker.domain.coach.CoachActionType.LOG_WEIGHT,
    com.zack.recomptracker.domain.coach.CoachActionType.LOG_STEPS,
    com.zack.recomptracker.domain.coach.CoachActionType.CONFIRM_PLANNED_MEALS,
    com.zack.recomptracker.domain.coach.CoachActionType.OPEN_FOOD_LOG,
    com.zack.recomptracker.domain.coach.CoachActionType.OPEN_TRAINING,
)

// `internal` (not `fun`): the Weekly Rebalance card's ViewModel carries the `internal`
// `RebalanceCopyService` (see its kdoc), so this screen entry point must stay `internal` too —
// its only caller, `AppNavGraph`, is same-module. `HomeDashboardContent` below stays public/
// preview-friendly since `RebalanceCardUiState` itself has no internal types in its API.
@Composable
internal fun HomeDashboardScreen(
    viewModel: DashboardViewModel,
    weeklyReviewViewModel: WeeklyReviewViewModel,
    streakViewModel: StreakViewModel,
    coachTodayViewModel: CoachTodayViewModel,
    rebalanceViewModel: RebalanceViewModel,
    onOpenCoach: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStreaks: () -> Unit,
    onOpenFoodLog: () -> Unit,
    onOpenBody: () -> Unit,
    onOpenTraining: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val coachTodayState by coachTodayViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { coachTodayViewModel.onShown() }
    val rebalanceCardState by rebalanceViewModel.uiState.collectAsStateWithLifecycle()
    val offerMinimized by rebalanceViewModel.offerMinimized.collectAsStateWithLifecycle()
    val phrasing by rebalanceViewModel.phrasing.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { rebalanceViewModel.onShown() }
    // Which face-scoped overlay/pill are open, owned here (the screen root Box) so the hoisted
    // Dialogs/pill are siblings of HomeDashboardContent, mirroring WeeklyBriefingOverlay. The ribbon
    // that opens the progress detail lives deep inside HomeDashboardContent → TodayCard, so the
    // setter is threaded down as onRebalanceRibbonClick.
    var progressDetailOpen by rememberSaveable { mutableStateOf(false) }
    val reviewState by weeklyReviewViewModel.uiState.collectAsStateWithLifecycle()
    val badge by weeklyReviewViewModel.badge.collectAsStateWithLifecycle()
    val pendingApply by weeklyReviewViewModel.pendingApply.collectAsStateWithLifecycle()
    val headerAvatar by viewModel.headerAvatar.collectAsStateWithLifecycle()
    val streakState by streakViewModel.uiState.collectAsStateWithLifecycle()

    // Local usage tracking: WEEKLY_CHECKIN_OPENED on every path that opens the weekly briefing
    // (badge/card tap or a Today's-Coaching action). Fire-and-forget — never blocks the UI.
    val usageTracker = LocalAppContainer.current.usageTracker
    val openWeeklyReview: () -> Unit = {
        usageTracker.track(UsageEvents.WEEKLY_CHECKIN_OPENED)
        weeklyReviewViewModel.open()
    }

    // A minimized offer's reopen pill takes over the Weekly Review pill's slot (the two never stack):
    // when it's showing, hide the review pill and drop the reopen pill's stacking offset.
    val reopenPillVisible = rebalanceCardState.face == RebalanceCardUiState.Face.OFFER && offerMinimized
    val toastController = LocalToastController.current
    val scope = rememberCoroutineScope()

    HomeDashboardContent(
        state = state,
        avatarPhotoUri = headerAvatar.photoUri,
        avatarInitials = headerAvatar.initials,
        showWeeklyReviewBadge = badge,
        onOpenWeeklyReview = openWeeklyReview,
        hideWeeklyReviewPill = reopenPillVisible,
        onExpandRebalance = { rebalanceViewModel.onExpandOffer() },
        onOpenSettings = onOpenSettings,
        onOpenFoodLog = onOpenFoodLog,
        onOpenBody = onOpenBody,
        coachTodaySignal = coachTodayState.signal,
        coachTodayText = coachTodayState.displayText,
        onCoachAction = { type ->
            when (type) {
                com.zack.recomptracker.domain.coach.CoachActionType.OPEN_WEEKLY_REVIEW -> openWeeklyReview()
                // Weight and steps are both logged in the body check-in.
                com.zack.recomptracker.domain.coach.CoachActionType.LOG_WEIGHT,
                com.zack.recomptracker.domain.coach.CoachActionType.LOG_STEPS -> onOpenBody()
                com.zack.recomptracker.domain.coach.CoachActionType.CONFIRM_PLANNED_MEALS,
                com.zack.recomptracker.domain.coach.CoachActionType.OPEN_FOOD_LOG -> onOpenFoodLog()
                com.zack.recomptracker.domain.coach.CoachActionType.OPEN_TRAINING -> onOpenTraining()
                else -> Unit // unmapped action → no navigation (button not shown for these)
            }
        },
        onDismissCoach = coachTodayViewModel::dismiss,
        onTrackExperiment = coachTodayViewModel::onTrackExperiment,
        rebalanceCardState = rebalanceCardState,
        onRebalanceDismiss = rebalanceViewModel::onDismiss,
        onRebalanceRibbonClick = { progressDetailOpen = true },
        rebalanceToday = state.rebalanceToday,
        streaks = streakState.streaks,
        stepGoal = streakState.stepGoal,
        activity = streakState.activity,
        onOpenStreaks = onOpenStreaks,
    )

    WeeklyBriefingOverlay(
        state = reviewState,
        pendingApply = pendingApply,
        onDismiss = { weeklyReviewViewModel.dismiss() },
        onRegenerate = { weeklyReviewViewModel.regenerate() },
        onRequestApply = { weeklyReviewViewModel.requestApply(it) },
        onConfirmApply = { weeklyReviewViewModel.confirmApply() },
        onCancelApply = { weeklyReviewViewModel.cancelApply() },
        onDiscussWithCoach = {
            weeklyReviewViewModel.discussWithCoach()
            weeklyReviewViewModel.dismiss()
            onOpenCoach()
        },
        onOpenSettings = {
            weeklyReviewViewModel.dismiss()
            onOpenSettings()
        },
    )

    // Weekly Rebalance offer/progress overlays + the minimized-offer reopen pill, hoisted as
    // siblings of HomeDashboardContent (like WeeklyBriefingOverlay above). Called UNCONDITIONALLY:
    // each Dialog early-returns internally on its own face/open gate and the pill gates on `visible`
    // via its own AnimatedVisibility, so keeping them mounted lets their dismiss/exit animations play
    // — an outer `if` would cut those animations off (prior-review constraint).
    RebalanceOfferOverlay(
        state = rebalanceCardState,
        minimized = offerMinimized,
        phrasing = phrasing,
        onAccept = {
            val days = rebalanceCardState.ofY
            rebalanceViewModel.onAccept()
            scope.launch {
                toastController.show(
                    ToastMessage(
                        text = if (days > 0) {
                            "Rebalance started — lighter targets for the next $days days"
                        } else {
                            "Rebalance started"
                        },
                        type = ToastType.Success,
                    ),
                )
            }
        },
        onDecline = { rebalanceViewModel.onDecline() },
        onMinimize = { rebalanceViewModel.onMinimizeOffer() },
        onCustomizeMode = { rebalanceViewModel.onCustomize(it) },
        onCustomizeIntensity = { rebalanceViewModel.onCustomizeIntensity(it) },
    )
    RebalanceProgressDetailOverlay(
        open = progressDetailOpen,
        state = rebalanceCardState,
        onClose = { progressDetailOpen = false },
        onCancel = { rebalanceViewModel.onCancelActive() },
    )
    // The reopen pill is NOT hoisted here — it renders inside HomeDashboardContent's root Box (the
    // Weekly Review pill's slot) so it shares the dashboard's liquid-glass backdrop; hoisting it to a
    // sibling Box outside that scope made its LiquidGlassButton render backdrop-less (near-invisible).
}

@Composable
fun HomeDashboardContent(
    state: DashboardUiState,
    avatarPhotoUri: String? = null,
    avatarInitials: String? = null,
    modifier: Modifier = Modifier,
    showWeeklyReviewBadge: Boolean = false,
    onOpenWeeklyReview: (() -> Unit)? = null,
    hideWeeklyReviewPill: Boolean = false,
    onExpandRebalance: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    onOpenFoodLog: (() -> Unit)? = null,
    onOpenBody: (() -> Unit)? = null,
    streaks: Streaks = Streaks.EMPTY,
    stepGoal: Int? = null,
    activity: ActivityMetrics = ActivityMetrics(),
    onOpenStreaks: (() -> Unit)? = null,
    coachTodaySignal: com.zack.recomptracker.domain.coach.CoachSignal? = null,
    coachTodayText: String = "",
    onCoachAction: (com.zack.recomptracker.domain.coach.CoachActionType) -> Unit = {},
    onDismissCoach: () -> Unit = {},
    onTrackExperiment: (com.zack.recomptracker.domain.coach.CoachSignal) -> Unit = {},
    rebalanceCardState: RebalanceCardUiState = RebalanceCardUiState(),
    onRebalanceDismiss: () -> Unit = {},
    onRebalanceRibbonClick: () -> Unit = {},
    rebalanceToday: com.zack.recomptracker.domain.rebalance.PlanDayInfo? = null,
) {
    val accent = LocalAppAccent.current
    val ambientOrbBrush1 = remember(accent.accent) {
        Brush.radialGradient(listOf(accent.accent.copy(alpha = 0.20f), Color.Transparent))
    }
    val ambientOrbBrush2 = remember(accent.accentLight) {
        Brush.radialGradient(listOf(accent.accentLight.copy(alpha = 0.08f), Color.Transparent))
    }
    Box(modifier = modifier.fillMaxSize()) {
        // Ambient orb 1 — top-left bloom
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-70).dp, y = (-90).dp)
                .background(ambientOrbBrush1),
        )
        // Ambient orb 2 — right-center secondary bloom
        Box(
            modifier = Modifier
                .size(220.dp)
                .offset(x = 200.dp, y = 260.dp)
                .background(ambientOrbBrush2),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                onOpenSettings = onOpenSettings,
                avatarPhotoUri = avatarPhotoUri,
                avatarInitials = avatarInitials,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = FloatingNavHeight + 72.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Today's Coaching — the single staged winner from the proactive spine. Silent
                // (renders nothing) when no signal clears the bar; CoachTodaySlot early-returns.
                if (coachTodaySignal != null) {
                    item {
                        CoachTodaySlot(
                            signal = coachTodaySignal,
                            displayText = coachTodayText,
                            onAction = onCoachAction,
                            onDismiss = onDismissCoach,
                            onTrackExperiment = onTrackExperiment,
                            isActionSupported = { it in SUPPORTED_COACH_ACTIONS },
                        )
                    }
                }
                // Weekly Rebalance NOTE — the only face that still renders inline (spec §3). The
                // OFFER face is the hoisted popup + reopen pill and the PROGRESS face is the Today-card
                // ribbon + detail overlay; both live outside this LazyColumn now. RebalanceNoteCard
                // early-returns for any non-NOTE face, but gating the item here also keeps the item out
                // of the list entirely so no empty slot spacing is left behind.
                if (rebalanceCardState.face == RebalanceCardUiState.Face.NOTE) {
                    item { RebalanceNoteCard(state = rebalanceCardState, onDismiss = onRebalanceDismiss) }
                }
                item { MotivationalCard(state.motivationalMessage) }
                item {
                    TodayCard(
                        state,
                        onClick = onOpenFoodLog,
                        rebalanceCardState = rebalanceCardState,
                        onRebalanceRibbonClick = onRebalanceRibbonClick,
                    )
                }
                item {
                    StatTilesRow(
                        state.adherencePercent,
                        state.weightTrendKgPerWeek,
                        onTrendClick = onOpenBody,
                    )
                }
                item { SevenDayChartCard(state) }
                item {
                    // Step-ring DISPLAY boost only (spec §6): on an active plan day with extra steps,
                    // show the boosted goal. Judgment (streaks) stays on the base goal elsewhere —
                    // this only changes what number the ring itself renders.
                    val boostedStepGoal = rebalanceToday?.plan
                        ?.takeIf { it.extraDailySteps > 0 && it.baseStepGoal != null }
                        ?.let { it.baseStepGoal!! + it.extraDailySteps }
                        ?: stepGoal
                    StreakGoalRing(
                        result = streaks.steps,
                        type = StreakType.STEPS,
                        todayValue = state.todaySteps,
                        goalValue = boostedStepGoal,
                    )
                }
                if (activity.weeklyGymSessionsTarget != null || activity.weeklyTrainingFrequency > 0.0) {
                    item { TrainingFrequencyTile(activity) }
                }
                if (onOpenStreaks != null) {
                    item { StreaksCard(streaks = streaks, onClick = onOpenStreaks) }
                }
            }
        }

        // Floating wide liquid-glass Weekly Review pill above the nav bar. Hidden while a minimized
        // rebalance offer's reopen pill occupies this slot (they never stack).
        if (onOpenWeeklyReview != null && !hideWeeklyReviewPill) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = FloatingNavHeight + 12.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Accent glow halo behind the pill — brighter when a fresh review is waiting.
                // A remembered radial gradient (colour fading to transparent) fakes the soft
                // edge a blur pass would produce, without an offscreen render each frame. Sized
                // ~26dp taller than the 48dp pill so the glow spreads beyond it, same as before.
                val weeklyReviewGlowBrush = remember(accent.accent, showWeeklyReviewBadge) {
                    Brush.radialGradient(
                        listOf(
                            accent.accent.copy(alpha = if (showWeeklyReviewBadge) 0.55f else 0.32f),
                            Color.Transparent,
                        ),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(weeklyReviewGlowBrush),
                )
                LiquidGlassButton(
                    onClick = onOpenWeeklyReview,
                    modifier = Modifier.fillMaxWidth(),
                    tint = accent.accent,
                    surfaceColor = Color.White.copy(alpha = 0.10f),
                ) {
                    Text(
                        text = "✦  Weekly Review",
                        style = AppType.cardTitle,
                        color = accent.onAccent,
                    )
                }
            }
        }

        // Rebalance reopen pill — occupies the Weekly Review pill's slot (the two never show at once,
        // see hideWeeklyReviewPill). A plain `if` (not AnimatedVisibility) so the LiquidGlassButton's
        // backdrop sampling isn't defeated by a graphics layer, and inside this Box so it shares the
        // dashboard's live LocalBackdrop.
        if (hideWeeklyReviewPill) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                RebalanceReopenPill(
                    stackedAboveWeeklyReview = false,
                    onExpand = onExpandRebalance,
                )
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    onOpenSettings: (() -> Unit)? = null,
    avatarPhotoUri: String? = null,
    avatarInitials: String? = null,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val dateStr = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEE, MMMM d", Locale.getDefault()))
    }
    com.zack.recomptracker.ui.component.ScreenHeader(
        title = "Dashboard",
        subtitle = dateStr,
        modifier = modifier,
        trailing = if (onOpenSettings != null) {
            { HeaderProfileButton(photoUri = avatarPhotoUri, initials = avatarInitials, onClick = onOpenSettings) }
        } else null,
    )
}

@Composable
private fun HeaderProfileButton(
    photoUri: String?,
    initials: String?,
    onClick: () -> Unit,
) {
    val accent = LocalAppAccent.current
    val gradient = remember(accent.accent, accent.accentDark) {
        Brush.linearGradient(listOf(accent.accent, accent.accentDark))
    }
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Profile and more" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(gradient)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                photoUri != null -> AsyncImage(
                    model = photoUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
                initials != null -> Text(
                    text = initials,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent.onAccent,
                )
                else -> Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = accent.onAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ── Card 1: TODAY ─────────────────────────────────────────────────────────────

@Composable
private fun TodayCard(
    state: DashboardUiState,
    onClick: (() -> Unit)? = null,
    rebalanceCardState: RebalanceCardUiState = RebalanceCardUiState(),
    onRebalanceRibbonClick: () -> Unit = {},
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val prefs    = state.preferences
    val calories = state.todayTotals.calories
    val zoneLow  = prefs.calorieZoneLowerBound
    val zoneHigh = prefs.calorieZoneUpperBound
    val scaleMax = ((zoneHigh * 1.2).toInt()).coerceAtLeast(1)
    val calFrac  = (calories.toFloat() / scaleMax).coerceIn(0f, 1f)

    val isInZone = calories in zoneLow..zoneHigh
    val isOver   = calories > zoneHigh
    val badgeText = when {
        isInZone -> "In zone"
        isOver   -> "Over"
        else     -> "Below"
    }
    val remainText = when {
        isInZone -> ""
        isOver   -> " · ${calories - zoneHigh} over"
        else     -> " · ${zoneLow - calories} to zone"
    }

    val proteinFrac = safeFrac(state.todayTotals.proteinG, prefs.targetProteinG)
    val carbsFrac   = safeFrac(state.todayTotals.carbsG,   prefs.targetCarbsG)
    val fatFrac     = safeFrac(state.todayTotals.fatG,     prefs.targetFatG)

    FrostedCard(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        // Weekly Rebalance progress ribbon — rides the Today card's space for an in-progress plan
        // (spec §3). Wrapped in AnimatedVisibility (not RebalanceRibbon's job) so the fade/expand
        // enter + fade/shrink exit play across composition; the ribbon has its own inner clickable,
        // so a tap on it opens the detail overlay while taps elsewhere on the card still open Food Log.
        val ribbonAnimations = rememberAnimationsEnabled()
        AnimatedVisibility(
            visible = rebalanceCardState.face == RebalanceCardUiState.Face.PROGRESS,
            enter = if (ribbonAnimations) {
                fadeIn(tween(220)) + expandVertically(tween(220), Alignment.Top)
            } else {
                fadeIn(tween(0))
            },
            exit = if (ribbonAnimations) {
                fadeOut(tween(150)) + shrinkVertically(tween(170), Alignment.Top)
            } else {
                fadeOut(tween(0))
            },
        ) {
            Column {
                RebalanceRibbon(rebalanceCardState, onRebalanceRibbonClick)
                Spacer(Modifier.height(12.dp))
            }
        }

        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TODAY",
                style = AppType.metaLabel,
                color = appColors.textMuted,
            )
            VioletBadge(text = badgeText)
        }
        Spacer(Modifier.height(10.dp))

        // Big calorie number
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = String.format(Locale.US, "%,d", calories),
                style = AppType.displayLarge,
                color = appColors.textPrimary,
                lineHeight = 36.sp,
            )
            Text(
                text = "kcal",
                style = AppType.body,
                color = appColors.textMuted,
            )
            if (remainText.isNotEmpty()) {
                Text(
                    text = remainText,
                    style = AppType.label,
                    color = appColors.textMuted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Calorie progress bar with striped zone
        CalorieProgressBar(
            progress = calFrac,
            zoneLowFrac = (zoneLow.toFloat() / scaleMax).coerceIn(0f, 1f),
            zoneHighFrac = (zoneHigh.toFloat() / scaleMax).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
        Spacer(Modifier.height(4.dp))

        // Zone labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", style = AppType.metaLabel, color = appColors.textVeryMuted)
            Text(
                "▌ $zoneLow–$zoneHigh",
                style = AppType.metaLabel,
                color = accent.inkBase.copy(alpha = 0.65f),
            )
            Text("$scaleMax", style = AppType.metaLabel, color = appColors.textVeryMuted)
        }
        Spacer(Modifier.height(12.dp))

        // Macros row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MacroBarItem(
                label = "Protein",
                value = "${state.todayTotals.proteinG.toInt()}g",
                fraction = proteinFrac,
                modifier = Modifier.weight(1f),
            )
            MacroBarItem(
                label = "Carbs",
                value = "${state.todayTotals.carbsG.toInt()}g",
                fraction = carbsFrac,
                modifier = Modifier.weight(1f),
            )
            MacroBarItem(
                label = "Fat",
                value = "${state.todayTotals.fatG.toInt()}g",
                fraction = fatFrac,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MacroBarItem(
    label: String,
    value: String,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val fillBrush = remember(accent.accent, accent.accentLight) {
        Brush.horizontalGradient(listOf(accent.accent, accent.accentLight))
    }
    val animatedFrac by animateFloatAsState(
        targetValue = fraction,
        animationSpec = ChartDefaults.AnimSpec.progressBar,
        label = "macroFill",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = AppType.metaLabel,
                color = appColors.textMuted,
            )
            Text(text = value, style = AppType.metaLabel, color = appColors.textDim)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(appColors.cardBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFrac)
                    .height(6.dp)
                    .background(
                        fillBrush,
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

// ── Card: TRAINING FREQUENCY ──────────────────────────────────────────────────

@Composable
private fun TrainingFrequencyTile(activity: ActivityMetrics) {
    val appColors = LocalAppColors.current
    FrostedCard {
        SectionLabel("Training")
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = String.format(java.util.Locale.US, "%.1f×", activity.weeklyTrainingFrequency),
                style = AppType.statValue,
                color = appColors.textPrimary,
            )
            val caption = activity.weeklyGymSessionsTarget
                ?.let { "/ wk · target $it" }
                ?: "/ wk · last 4 weeks"
            Text(
                text = caption,
                style = AppType.cardSubtitle,
                color = appColors.textSecondary,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

// ── Card: STREAKS SUMMARY ─────────────────────────────────────────────────────

@Composable
private fun StreaksCard(streaks: Streaks, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    FrostedCard(modifier = Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Streaks")
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = appColors.textVeryMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StreakRow(result = streaks.workout, type = StreakType.WORKOUT)
            StreakRow(result = streaks.calorie, type = StreakType.CALORIE)
            StreakRow(result = streaks.steps, type = StreakType.STEPS)
        }
    }
}

// ── Card 2: LAST 7 DAYS CHART ─────────────────────────────────────────────────

@Composable
private fun SevenDayChartCard(state: DashboardUiState) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val inZone = state.inZoneDays7

    FrostedCard {
        val days = state.last7DaysCalories
        var scrubIndex by remember { mutableStateOf<Int?>(null) }
        // Selected day drives the header kcal + the macro row. Defaults to today (the last
        // point, under the glow dot) when not actively scrubbing the chart.
        val selectedDay = scrubIndex?.let { days.getOrNull(it) } ?: days.lastOrNull()
        val isScrubbing = scrubIndex != null

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isScrubbing && selectedDay != null) {
                Text(
                    text = String.format(java.util.Locale.US, "%s · %,d kcal", selectedDay.label, selectedDay.calories),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = appColors.textPrimary,
                    letterSpacing = (-0.3).sp,
                )
            } else {
                Text(
                    text = "LAST 7 DAYS",
                    style = AppType.metaLabel,
                    color = accent.inkLight.copy(alpha = 0.85f),
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent.accentLight),
                )
                Text(
                    text = "$inZone of 7 in zone",
                    style = AppType.label,
                    color = accent.inkLighter,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        SparklineChart(
            values       = days.map { it.calories.toFloat() },
            height       = 90.dp,
            showGlowDot  = true,
            showScrubber = true,
            zoneLow      = state.preferences.calorieZoneLowerBound.toFloat(),
            zoneHigh     = state.preferences.calorieZoneUpperBound.toFloat(),
            onScrubIndex = { scrubIndex = it },
        )

        // Day labels — the scrubbed day (or today by default) is emphasised.
        if (days.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                days.forEachIndexed { index, day ->
                    val highlighted = if (isScrubbing) index == scrubIndex else day.isToday
                    Text(
                        text = day.label,
                        fontSize = 9.sp,
                        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                        color = if (highlighted) accent.accentLighter else appColors.textVeryMuted,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Divider
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(appColors.cardBorder),
        )
        Spacer(Modifier.height(10.dp))

        // Macro row — mirrors the scrubbed day's calories. Tracks the chart scrubber so
        // protein / carbs / fat update in lockstep with the kcal in the header above.
        Row(modifier = Modifier.fillMaxWidth()) {
            MacroChartStat(
                grams = selectedDay?.proteinG ?: 0,
                label = "Protein",
                dotColor = accent.accent,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .align(Alignment.CenterVertically)
                    .background(appColors.cardBorder),
            )
            MacroChartStat(
                grams = selectedDay?.carbsG ?: 0,
                label = "Carbs",
                dotColor = accent.accentLight,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .align(Alignment.CenterVertically)
                    .background(appColors.cardBorder),
            )
            MacroChartStat(
                grams = selectedDay?.fatG ?: 0,
                label = "Fat",
                dotColor = accent.accentLighter,
                modifier = Modifier.weight(1f),
            )
        }
    }
}


@Composable
private fun MacroChartStat(
    grams: Int,
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = "${grams}g",
                style = AppType.statValueSmall,
                color = appColors.textPrimary,
            )
        }
        Text(
            text = label,
            style = AppType.metaLabel,
            color = appColors.textMuted,
        )
    }
}

// ── Card: MOTIVATIONAL MESSAGE ────────────────────────────────────────────────

@Composable
private fun MotivationalCard(message: String) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current
    val backgroundBrush = remember(accent.accentDark) {
        Brush.linearGradient(
            colors = listOf(accent.accentDark.copy(alpha = 0.14f), accent.accentDark.copy(alpha = 0.06f)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundBrush)
            .border(1.dp, accent.accent.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(
            text = message,
            style = AppType.statValueSmall,
            color = if (appColors.isDark) Color(0xFFEDE9FE) else accent.inkBase,
            lineHeight = 24.sp,
        )
    }
}

// ── Stat Tiles Row ────────────────────────────────────────────────────────────

@Composable
private fun StatTilesRow(
    adherencePercent: Double,
    weightTrendKgPerWeek: Double,
    onTrendClick: (() -> Unit)? = null,
) {
    val accent = LocalAppAccent.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(
            value = adherencePercent.formatPercent(),
            label = "Adherence",
            valueColor = accent.accentLight,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = "${weightTrendKgPerWeek.formatSignedOneDecimal()} kg",
            label = "Trend / week",
            valueColor = if (weightTrendKgPerWeek <= 0.0) ErrorRed else Color(0xFF4ADE80),
            modifier = Modifier.weight(1f),
            onClick = onTrendClick,
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(appColors.cardSurface)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = AppType.statValueSmall,
                color = valueColor,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = AppType.metaLabel,
                color = appColors.textMuted,
            )
        }
    }
}

// ── Legacy Stats sub-screen (accessible via More → Stats) ─────────────────────

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appColors = LocalAppColors.current
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = appColors.textPrimary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Calorie Decision",
                        style = AppType.screenTitle,
                        color = appColors.textPrimary,
                    )
                    Text(
                        text = "Today's recommendation",
                        style = AppType.cardSubtitle,
                        color = appColors.textMuted,
                    )
                }
            }
        }
        item {
            VerdictHero(result = state.result)
        }
        item {
            FrostedCard {
                SectionLabel("Current targets")
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatRow("Calories", "${state.preferences.targetCalories} kcal")
                    StatRow(
                        "Protein / Carbs / Fat",
                        "${state.preferences.targetProteinG} / ${state.preferences.targetCarbsG} / ${state.preferences.targetFatG} g",
                    )
                }
            }
        }
        item {
            FrostedCard {
                SectionLabel("Trend summary")
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatRow("7-day weight average", state.sevenDayWeightAverage?.let { "${String.format(Locale.US, "%.1f", it)} kg" } ?: "No data")
                    StatRow("Weight trend", "${state.weightTrendKgPerWeek.formatSignedOneDecimal()} kg/week")
                    StatRow("Waist trend", "${state.waistTrendCmPerWeek.formatSignedOneDecimal()} cm/week")
                    StatRow("Adherence", state.adherencePercent.formatPercent())
                    StatRow("Logged days", "${state.loggedDaysInWindow} / 14")
                }
            }
        }
    }
}

// ── Verdict HERO ──────────────────────────────────────────────────────────────

@Composable
private fun VerdictHero(result: AdjustmentResult) {
    val accent = LocalAppAccent.current
    val appColors = LocalAppColors.current

    val deltaText = when (result.verdict) {
        AdjustmentVerdict.WAIT_FOR_DATA -> "Not enough data yet"
        else -> {
            val change = result.recommendedCalorieChange
            val sign = if (change > 0) "+" else ""
            "Recommended change · $sign$change kcal/day"
        }
    }

    TintedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "TODAY'S VERDICT",
                style = AppType.metaLabel,
                color = accent.inkLight,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = result.verdict.heroLabel(),
                style = AppType.displayLarge,
                color = appColors.textPrimary,
                lineHeight = 36.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = deltaText,
                style = AppType.body,
                color = accent.inkLighter,
            )

            val reasons = result.reasonCodes.filter { it.isNotBlank() }
            if (reasons.isNotEmpty()) {
                Spacer(Modifier.height(13.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    reasons.forEach { code ->
                        ReasonChip(text = code.humanizeReasonCode())
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasonChip(text: String) {
    val appColors = LocalAppColors.current
    Text(
        text = text,
        style = AppType.label,
        color = appColors.textDim,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(appColors.cardSurface)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color? = null) {
    val appColors = LocalAppColors.current
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = AppType.body, color = appColors.textDim)
        Text(value, style = AppType.body.copy(fontWeight = FontWeight.SemiBold), color = valueColor ?: appColors.textPrimary)
    }
}

private fun AdjustmentVerdict.label(): String = when (this) {
    AdjustmentVerdict.WAIT_FOR_DATA    -> "Wait"
    AdjustmentVerdict.HOLD             -> "Hold"
    AdjustmentVerdict.INCREASE_CALORIES -> "Increase"
    AdjustmentVerdict.REDUCE_CALORIES   -> "Reduce"
}

/** Full-phrase verdict word for the hero card (e.g. "Hold steady"). */
private fun AdjustmentVerdict.heroLabel(): String = when (this) {
    AdjustmentVerdict.WAIT_FOR_DATA     -> "Keep logging"
    AdjustmentVerdict.HOLD              -> "Hold steady"
    AdjustmentVerdict.INCREASE_CALORIES -> "Increase"
    AdjustmentVerdict.REDUCE_CALORIES   -> "Reduce"
}

/**
 * Turns an enum-ish reason code (e.g. "TREND_ON_TRACK") into a tidy chip label
 * ("Trend on track"). Already human-readable strings pass through with just a
 * capitalised first letter.
 */
private fun String.humanizeReasonCode(): String {
    val cleaned = trim()
    val words = if (cleaned.contains('_')) {
        cleaned.split('_').filter { it.isNotBlank() }.joinToString(" ") { it.lowercase() }
    } else {
        cleaned.lowercase()
    }
    return words.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
}

private fun safeFrac(value: Double, target: Int): Float =
    if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
