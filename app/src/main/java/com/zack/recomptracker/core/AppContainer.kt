package com.zack.recomptracker.core

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.core.time.SystemDateProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.preferences.AppPreferences
import com.zack.recomptracker.data.health.HealthConnectRepository
import com.zack.recomptracker.data.health.HealthSyncCoordinator
import com.zack.recomptracker.data.health.WorkManagerBackgroundSyncScheduler
import com.zack.recomptracker.data.repository.BackupRepository
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.FoodCatalogRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PersonalFoodRepository
import com.zack.recomptracker.data.repository.PlanHistoryInitializer
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.toPlanTargets
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.data.usage.RoomUsageTracker
import com.zack.recomptracker.data.usage.UsageTracker
import com.zack.recomptracker.domain.adjustment.AdjustmentEngine
import com.zack.recomptracker.domain.adjustment.AdjustmentThresholds
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.trend.TrendCalculator
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.CloudCoachCoordinator
import com.zack.recomptracker.ai.CloudInsightCoordinator
import com.zack.recomptracker.ai.CoachCoordinator
import com.zack.recomptracker.ai.CoachToolExecutor
import com.zack.recomptracker.ai.CoachHandoffStore
import com.zack.recomptracker.ai.CoachToolsAdapter
import com.zack.recomptracker.ai.CloudRecipeNamer
import com.zack.recomptracker.ai.RecipeNamer
import com.zack.recomptracker.ai.CLOUD_COACH_TOOL_SCHEMAS
import com.zack.recomptracker.ai.knowledge.KeywordKnowledgeRetriever
import com.zack.recomptracker.ai.knowledge.KnowledgeCorpus
import com.zack.recomptracker.ai.knowledge.KnowledgeInjector
import com.zack.recomptracker.ai.knowledge.NoOpKnowledgeInjector
import com.zack.recomptracker.ai.knowledge.RetrievalKnowledgeInjector
import com.zack.recomptracker.data.preferences.SecureKeyStore
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import com.zack.recomptracker.data.remote.TavilyWebSearchProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import com.zack.recomptracker.ui.coach.CoachViewModel
import com.zack.recomptracker.ui.body.BodyEditViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.zack.recomptracker.ui.aicoach.AiCoachViewModel
import com.zack.recomptracker.ui.aicoach.CoachMemoryViewModel
import com.zack.recomptracker.ui.appearance.AppearanceViewModel
import com.zack.recomptracker.ui.body.BodyHistoryViewModel
import com.zack.recomptracker.ui.dashboard.CoachTodayViewModel
import com.zack.recomptracker.ui.dashboard.DashboardViewModel
import com.zack.recomptracker.ui.dashboard.RebalanceViewModel
import com.zack.recomptracker.ui.developer.DeveloperViewModel
import com.zack.recomptracker.ui.foodlibrary.FoodLibraryViewModel
import com.zack.recomptracker.ui.foods.FoodsViewModel
import com.zack.recomptracker.ui.onboarding.OnboardingViewModel
import com.zack.recomptracker.ui.plan.PlanViewModel
import com.zack.recomptracker.ui.profile.ProfileViewModel
import com.zack.recomptracker.ui.streak.StreakViewModel
import com.zack.recomptracker.ui.progress.ProgressViewModel
import com.zack.recomptracker.ui.settings.SettingsViewModel
import com.zack.recomptracker.ui.today.FoodLogViewModel
import com.zack.recomptracker.ui.today.TodayViewModel
import com.zack.recomptracker.ui.train.ActiveSessionViewModel
import com.zack.recomptracker.ui.train.ExercisePickerViewModel
import com.zack.recomptracker.ui.train.RoutineBuilderViewModel
import com.zack.recomptracker.ui.train.ExerciseStatsViewModel
import com.zack.recomptracker.ui.train.SessionDetailViewModel
import com.zack.recomptracker.ui.train.SessionSummaryViewModel
import com.zack.recomptracker.ui.train.TrainViewModel
import com.zack.recomptracker.ui.usage.UsageStatsViewModel
import com.zack.recomptracker.ui.train.component.MuscleArt
import com.zack.recomptracker.data.remote.OpenFoodFactsApi
import com.zack.recomptracker.data.repository.BarcodeRepository
import com.zack.recomptracker.data.repository.RecipeRepository
import com.zack.recomptracker.data.repository.StreakRepository
import com.zack.recomptracker.ui.recipes.RecipeBuilderViewModel
import com.zack.recomptracker.ui.scanner.BarcodeScannerViewModel
import com.zack.recomptracker.ai.CoachPhrasingService
import com.zack.recomptracker.ai.RebalanceCopyService
import com.zack.recomptracker.ai.WeeklyBriefingGenerator
import com.zack.recomptracker.ai.WeeklyCoachNote
import com.zack.recomptracker.data.coach.CoachContextBuilder
import com.zack.recomptracker.data.coach.CoachContextCache
import com.zack.recomptracker.data.coach.CoachDigestCoordinator
import com.zack.recomptracker.data.coach.CoachExperimentStore
import com.zack.recomptracker.data.coach.CoachInboxRepository
import com.zack.recomptracker.data.coach.CoachJourneyStore
import com.zack.recomptracker.data.coach.CoachMemoryStore
import com.zack.recomptracker.data.coach.AndroidCoachNotifier
import com.zack.recomptracker.data.coach.CoachNotificationPreferences
import com.zack.recomptracker.data.coach.CoachPushEmitter
import com.zack.recomptracker.data.coach.WorkManagerCoachDigestScheduler
import com.zack.recomptracker.domain.coach.RateLimiter
import com.zack.recomptracker.domain.coach.CoachSignalEngine
import com.zack.recomptracker.domain.coach.CoachSurface
import com.zack.recomptracker.domain.coach.SignalSelector
import com.zack.recomptracker.data.repository.WeeklyBriefingRepository
import com.zack.recomptracker.domain.activity.ActivitySummary
import com.zack.recomptracker.domain.review.WeeklyActivity
import com.zack.recomptracker.domain.review.WeeklyReviewComputer
import com.zack.recomptracker.domain.review.WeeklyTrainingBuilder
import com.zack.recomptracker.domain.streak.StreakCalculator
import com.zack.recomptracker.domain.review.WeeklyReviewData
import com.zack.recomptracker.ui.review.WeeklyReviewConfig
import com.zack.recomptracker.ui.review.WeeklyReviewViewModel
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.adherence.NutritionDay
import com.zack.recomptracker.domain.insight.DayNutrition
import com.zack.recomptracker.domain.insight.InsightEngine
import com.zack.recomptracker.domain.insight.NutritionTargets
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
import com.zack.recomptracker.domain.plan.PlanHistory
import com.zack.recomptracker.domain.plan.PlanVersion
import com.zack.recomptracker.data.rebalance.DataStoreRebalanceStore
import com.zack.recomptracker.data.rebalance.RebalanceCoordinator
import com.zack.recomptracker.domain.rebalance.RebalanceEvaluationInput
import com.zack.recomptracker.domain.trend.MeasurementPoint
import com.zack.recomptracker.domain.trend.PerformancePoint
import com.zack.recomptracker.domain.trend.RecoveryPoint
import com.zack.recomptracker.data.repository.macroTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

class AppContainer(context: Context) {
    val dateProvider: DateProvider = SystemDateProvider()
    private val _database by lazy { RecompDatabase.create(context) }
    val database: RecompDatabase get() = _database
    private val appPreferences = AppPreferences(context.applicationContext)
    val uiPreferences = UiPreferences(context.applicationContext)
    val userProfilePreferencesStore = UserProfilePreferencesStore(context.applicationContext)
    val planRepository = PlanRepository(
        appPreferences = appPreferences,
        planVersionDao = database.planVersionDao(),
        dateProvider = dateProvider,
    )
    val logRepository = LogRepository(
        dailyLogDao = database.dailyLogDao(),
        mealEntryDao = database.mealEntryDao(),
        savedFoodDao = database.savedFoodDao(),
        savedMealDao = database.savedMealDao(),
        performanceDao = database.performanceDao(),
        weeklyReviewDao = database.weeklyReviewDao(),
        mealSlotDao = database.mealSlotDao(),
    )
    val foodCatalogRepository = FoodCatalogRepository(database)
    val openFoodFactsApi = OpenFoodFactsApi()
    val barcodeRepository = BarcodeRepository(openFoodFactsApi)
    val personalFoodRepository = PersonalFoodRepository(database.savedFoodDao())
    val recipeRepository = RecipeRepository(database.recipeDao())
    val healthConnectRepository = HealthConnectRepository(context.applicationContext)
    val adjustmentEngine = AdjustmentEngine()
    val trendCalculator = TrendCalculator()
    val adherenceCalculator = AdherenceCalculator()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Fully-local, privacy-first usage tracking (Room, no external SDK). Fire-and-forget on
     * [Dispatchers.IO] via [appScope]; read back on the in-app Usage screen. Lazy so the DAO is
     * only touched on first use. Exposed to composables via `LocalAppContainer.current.usageTracker`.
     */
    val usageTracker: UsageTracker by lazy {
        RoomUsageTracker(dao = database.usageEventDao(), scope = appScope)
    }

    val exerciseLibraryRepository = ExerciseLibraryRepository(database.exerciseDao())
    val workoutRepository = WorkoutRepository(database.workoutDao())
    val workoutSessionRepository = WorkoutSessionRepository(
        database.workoutSessionDao(),
        dailyLogDao = database.dailyLogDao(),
    )
    val streakCalculator = StreakCalculator()
    // Persisted weekly-rebalance state + the once-daily evaluation gate (own `rebalance` DataStore).
    // Declared here (ahead of the consumers that read effective targets — StreakRepository, the coach
    // context builder/adapter/executor, the VM factory) so each can be threaded the same store.
    val rebalanceStore = DataStoreRebalanceStore(context.applicationContext)
    val backupRepository = BackupRepository(database, planRepository, rebalanceStore)
    val streakRepository = StreakRepository(
        logRepository = logRepository,
        workoutSessionRepository = workoutSessionRepository,
        planRepository = planRepository,
        userProfileStore = userProfilePreferencesStore,
        dateProvider = dateProvider,
        calculator = streakCalculator,
        rebalanceStore = rebalanceStore,
    )
    val planHistoryInitializer = PlanHistoryInitializer.from(database.planVersionDao(), planRepository)
    val healthSyncCoordinator = HealthSyncCoordinator(
        hcRepository = healthConnectRepository,
        logRepository = logRepository,
        planRepository = planRepository,
        dateProvider = dateProvider,
        appScope = appScope,
        backgroundScheduler = WorkManagerBackgroundSyncScheduler(context.applicationContext),
    )

    // Knowledge base: read + JSON-parsed once from assets (~116 KB). The read/parse is done off the
    // main thread (see the appScope.launch in init) — mirroring the exercise-library and MuscleArt
    // loads — so it can never block cold start. Until the parse completes, and if it fails (missing
    // or invalid corpus, so a bad ingestion run can never crash the app), the injector delegates to a
    // no-op that emits no REFERENCE block; the swap is invisible to consumers because they only call
    // referenceBlock() at generation time (a chat turn / insight / briefing), long after startup.
    // Shared by the cloud coach chat and the weekly briefing so both ground prose in the same corpus.
    // Declared BEFORE the init block that hands it to the IO coroutine, like every other property
    // an init-launched load touches — property initializers and init blocks run in source order.
    private val knowledgeInjector = DeferredKnowledgeInjector()

    init {
        appScope.launch {
            runCatching {
                planHistoryInitializer.seedIfEmpty()
            }.onFailure {
                Log.w("RecompPlan", "Plan history baseline seed failed", it)
            }
        }
        appScope.launch {
            runCatching {
                exerciseLibraryRepository.seedIfEmpty(ExerciseLibraryRepository.VERSION) {
                    context.applicationContext.assets.open("exercises/exercises.json")
                }
            }.onFailure {
                Log.w("RecompWorkout", "Exercise library seed failed — library will be empty", it)
            }
        }
        appScope.launch(Dispatchers.IO) {
            runCatching { MuscleArt.load(context.applicationContext) }
        }
        // Read + parse the ~116 KB knowledge corpus off the main thread, then swap the deferred
        // injector's delegate to the real retriever. On failure it stays a no-op (coach/briefing run
        // without knowledge injection). Consumers only read referenceBlock() at generation time, so
        // the brief startup window before this completes is never observed.
        appScope.launch(Dispatchers.IO) {
            runCatching {
                val raw = context.applicationContext.assets
                    .open("knowledge/corpus.json")
                    .bufferedReader()
                    .use { it.readText() }
                RetrievalKnowledgeInjector(KeywordKnowledgeRetriever(KnowledgeCorpus.fromJson(raw).chunks))
            }.onSuccess { knowledgeInjector.setDelegate(it) }
                .onFailure {
                    Log.w("RecompKnowledge", "Knowledge corpus load failed — coach will run without knowledge injection", it)
                }
        }
    }

    val secureKeyStore = SecureKeyStore(context)
    val openAiCompatClient = OpenAiCompatClient()

    // Effective cloud config: non-null only when base URL, model id, and API key are all present.
    private val cloudConfigFlow: StateFlow<CloudConfig?> =
        combine(
            uiPreferences.cloudBaseUrl,
            uiPreferences.cloudModelId,
            secureKeyStore.hasKey,
        ) { baseUrl, model, hasKey ->
            if (baseUrl.isNotBlank() && model.isNotBlank() && hasKey) {
                CloudConfig(baseUrl = baseUrl, apiKey = secureKeyStore.getApiKey(), model = model)
            } else {
                null
            }
        }.stateIn(appScope, SharingStarted.Eagerly, null)

    private val cloudConfigComplete: StateFlow<Boolean> =
        combine(uiPreferences.cloudConfigPresent, secureKeyStore.hasKey) { present, hasKey ->
            present && hasKey
        }.stateIn(appScope, SharingStarted.Eagerly, false)

    private val webSearchProvider = TavilyWebSearchProvider(
        keyProvider = { secureKeyStore.getWebSearchKey() },
    )
    // Coach freeform memory: user-editable facts the coach reads into its chat prompt and can
    // write via remember/forget. Constructed once here; also wired into the adapter (below) and
    // the CoachMemoryViewModel factory.
    val coachMemoryStore = CoachMemoryStore(context.applicationContext, dateProvider)

    private val coachToolExecutor = CoachToolExecutor(
        logRepository = logRepository,
        planRepository = planRepository,
        dateProvider = dateProvider,
        webSearchProvider = webSearchProvider,
        workoutSessionRepository = workoutSessionRepository,
        workoutRepository = workoutRepository,
        exerciseLibraryRepository = exerciseLibraryRepository,
        coachMemory = coachMemoryStore,
        rebalanceState = { rebalanceStore.current() },
    )
    val coachHandoffStore = CoachHandoffStore()

    // Multi-week coach memory: one shared journey ledger. The digest records fired signals + weekly
    // verdicts into it; the briefing and chat prompts read its narrative. See Phase 5 / §10.
    val coachJourneyStore = CoachJourneyStore(context.applicationContext, dateProvider)

    val weeklyReviewComputer = WeeklyReviewComputer()
    val weeklyBriefingGenerator = WeeklyBriefingGenerator(
        openAiCompatClient,
        knowledgeInjector = knowledgeInjector,
        journey = coachJourneyStore,
    )
    val weeklyBriefingRepository = WeeklyBriefingRepository(database.weeklyReviewDao())

    /** Feature gate: cloud config complete, AI enabled. */
    val cloudBriefingActive: StateFlow<Boolean> = combine(
        cloudConfigComplete,
        uiPreferences.aiInsightsEnabled,
    ) { complete, enabled ->
        complete && enabled
    }.stateIn(appScope, SharingStarted.Eagerly, false)

    /** Current review week's deterministic data, recomputed from the same sources as the dashboard. */
    val weeklyReviewDataFlow: Flow<WeeklyReviewData?> = combine(
        combine(
            logRepository.observeDailyLogs(),
            logRepository.observeMealEntriesSince(dateProvider.today().minusDays(27)),
            logRepository.observePerformances(),
            planRepository.preferences,
            planRepository.observeVersions(),
        ) { logs, meals, performances, prefs, versions ->
            NutritionBodyInputs(logs, meals, performances, prefs, versions)
        },
        workoutSessionRepository.observeCompletedSessions(),
    ) { base, sessions ->
        computeWeeklyReviewData(
            base.logs, base.meals, base.performances, base.prefs, base.versions, sessions,
        )
    }

    /** Bundles the five nutrition/body flows so the review data can also fold in training sessions. */
    private data class NutritionBodyInputs(
        val logs: List<DailyLogEntity>,
        val meals: List<MealEntryEntity>,
        val performances: List<LiftPerformanceEntity>,
        val prefs: PlanPreferences,
        val versions: List<PlanVersion>,
    )

    fun cloudConfigForBriefing(): CloudConfig? = cloudConfigFlow.value

    private fun computeWeeklyReviewData(
        logs: List<DailyLogEntity>,
        allMeals: List<MealEntryEntity>,
        performances: List<LiftPerformanceEntity>,
        prefs: PlanPreferences,
        versions: List<PlanVersion>,
        completedSessions: List<com.zack.recomptracker.domain.workout.WorkoutSession>,
    ): WeeklyReviewData? {
        val today = dateProvider.today()
        val meals = allMeals.filterNot { it.planned }
        val last14Start = today.minusDays(13)
        val last28Start = today.minusDays(27)
        val logs28 = logs.filter { LocalDate.parse(it.date) in last28Start..today }
        val meals14 = meals.filter { LocalDate.parse(it.date) in last14Start..today }
        val mealsByDate = meals14.groupBy { LocalDate.parse(it.date) }
        val weekTargets = PlanHistory.resolve(versions, (0..13).map { last14Start.plusDays(it.toLong()) })
        val nutritionDays = (0..13).map { off ->
            val d = last14Start.plusDays(off.toLong())
            NutritionDay(d, mealsByDate[d].orEmpty().macroTotals().calories, weekTargets[d]?.calories ?: prefs.targetCalories)
        }
        val loggedDates = logs28.map { it.date }.toSet() + meals14.map { it.date }.toSet()
        val daysLogged = loggedDates.count { LocalDate.parse(it) in last14Start..today }
        if (daysLogged == 0) return null
        val weightPoints = logs28.map { MeasurementPoint(LocalDate.parse(it.date), it.bodyWeightKg) }
        val waistPoints = logs28.map { MeasurementPoint(LocalDate.parse(it.date), it.waistCm) }
        val perfPoints = performances
            .filter { LocalDate.parse(it.date) in last28Start..today }
            .map { PerformancePoint(LocalDate.parse(it.date), it.weight, it.reps, it.sets) }
        val recPoints = logs
            .filter { LocalDate.parse(it.date) in last14Start..today }
            .map { RecoveryPoint(LocalDate.parse(it.date), it.sleepHours, it.energyScore, it.sorenessScore) }
        val weeksSincePhase = prefs.maintenancePhaseStartDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) / 7 }?.toInt() ?: 4
        val input = AdjustmentInput(
            daysLogged = daysLogged,
            adherencePercent = adherenceCalculator.calculate(nutritionDays),
            weeksSincePhaseStart = weeksSincePhase,
            weightTrendKgPerWeek = trendCalculator.trendPerWeek(weightPoints),
            waistTrendCmPerWeek = trendCalculator.trendPerWeek(waistPoints),
            performanceTrend = trendCalculator.performanceTrend(perfPoints),
            recoveryTrend = trendCalculator.recoveryTrend(recPoints),
        )
        val thresholds = AdjustmentThresholds(
            weightTrendThresholdKgPerWeek = prefs.weightTrendThresholdKgPerWeek,
            waistIncreaseThresholdCmAcrossTwoWeeks = prefs.waistIncreaseThresholdCm,
            adherenceMinimumPercent = prefs.adherenceMinimumPercent,
        )
        val result = AdjustmentEngine(thresholds).evaluate(input)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        val weekEndTarget = PlanHistory.planOnOrFallback(versions, today, prefs.toPlanTargets()).calories
        // Activity domain (steps) — the fourth domain fed into the weekly check-in. Derived from the
        // step logs via the shared ActivitySummary (no duplicated math), this week vs last week.
        val stepsByDate = logs28.mapNotNull { l -> l.steps?.let { LocalDate.parse(l.date) to it } }.toMap()
        val activity = WeeklyActivity(
            avgSteps7 = ActivitySummary.averageDailySteps(stepsByDate, today, 7),
            avgStepsPrev7 = ActivitySummary.averageDailySteps(stepsByDate, today.minusDays(7), 7),
            stepGoal = null,
        )
        // Training domain (SUPPORTING) — reuse the shared TrainingDerivations e1RM/trend math over the
        // completed-session window (matches get_training_summary). Null when nothing was logged.
        val datedSessions = completedSessions
            .mapNotNull { s -> runCatching { LocalDate.parse(s.date) }.getOrNull()?.let { it to s } }
            .filter { (d, _) -> d in last28Start..today }
        val training = WeeklyTrainingBuilder.build(datedSessions, today)
        // Weekly Pattern Spotlight (Phase 2D) — the top two deterministic pattern facts over the same
        // 14-day window, computed by InsightEngine (relocated from the dashboard card into the briefing).
        val patternDays = (0..13).map { off ->
            val d = last14Start.plusDays(off.toLong())
            val t = mealsByDate[d].orEmpty().macroTotals()
            DayNutrition(d, t.calories, t.proteinG, t.carbsG, t.fatG, logged = t.calories > 0)
        }
        val patternTargets = NutritionTargets(
            calories = prefs.targetCalories,
            proteinG = prefs.targetProteinG,
            carbsG = prefs.targetCarbsG,
            fatG = prefs.targetFatG,
            calorieZoneLower = prefs.calorieZoneLowerBound,
            calorieZoneUpper = prefs.calorieZoneUpperBound,
        )
        val patternFacts = InsightEngine.detectTopFacts(patternDays, patternTargets, n = 2)
        return weeklyReviewComputer.build(
            weekStart, input, result, weekEndTarget, activity, training, patternFacts,
        )
    }

    /**
     * Assembles the flattened [RebalanceEvaluationInput] for the once-daily rebalance run — the
     * `computeWeeklyReviewData` one-shot pattern: plain suspend reads folded into date-keyed maps for
     * the pure [com.zack.recomptracker.domain.rebalance.RebalanceEngine]. The judged window is the
     * trailing 7 days ending **yesterday** (today is never judged, spec §5). Base targets/eaten/counts
     * are read over that window; steps are pulled from the daily logs (house pattern:
     * `observeDailyLogs().first()` filtered to the window); the step goal + fitness goal come from the
     * profile; the sticky mode + existing state from the persisted rebalance store.
     */
    private suspend fun buildRebalanceInput(): RebalanceEvaluationInput {
        val today = dateProvider.today()
        val start = today.minusDays(7)
        val end = today.minusDays(1)
        val window = (0..6).map { start.plusDays(it.toLong()) } // today-7 .. today-1 inclusive
        val eatenByDate = logRepository.getWeekCalories(start, end)
        val mealCountByDate = logRepository.getWeekMealCounts(start, end)
        val baseTargetsByDate = planRepository.targetsByDate(window)
        val stepsByDate = logRepository.observeDailyLogs().first()
            .mapNotNull { l ->
                val d = runCatching { LocalDate.parse(l.date) }.getOrNull()
                if (d != null && d in start..end && l.steps != null) d to l.steps!! else null
            }
            .toMap()
        val profile = userProfilePreferencesStore.preferences.first()
        val state = rebalanceStore.current()
        return RebalanceEvaluationInput(
            today = today,
            baseTargetsByDate = baseTargetsByDate,
            eatenByDate = eatenByDate,
            mealCountByDate = mealCountByDate,
            stepsByDate = stepsByDate,
            baseStepGoal = profile.dailyStepGoal,
            goal = profile.goal,
            mode = state.mode,
            existing = state,
        )
    }

    /**
     * SUPPORTING "what the coach noticed this week" note for the briefing (D45): the deterministic
     * engine's WEEKLY-surface winner, mapped to number-safe text. A passive READ — it runs the same
     * catalog the digest does but selects with an empty seen-ledger (no cooldown side effects, no push,
     * no inbox write). Null when the engine has nothing weekly to say. The briefing verdict/numbers
     * remain authoritative; this only colours prose (see WeeklyBriefingGenerator/PromptBuilder).
     */
    suspend fun weeklyCoachNote(): WeeklyCoachNote? {
        val ctx = runCatching { coachContextCache.get() }.getOrNull() ?: return null
        val signals = coachSignalEngine.evaluate(ctx)
        val winner = signalSelector.selectForSurface(
            CoachSurface.WEEKLY, signals, emptyMap(), dateProvider.today(),
        ).winner ?: return null
        return WeeklyCoachNote(statement = winner.verdict, rationale = winner.fallbackText)
    }

    // ── Cloud coordinators ─────────────────────────────────────────────────────────
    // Exposed (was private) so the migrated insight cards (Trends PROGRESS_TREND, Body
    // RECOVERY_READINESS) bind straight to the cloud coordinator, no longer via the router (Q6a,
    // Phase-8 enabler). Grounded in the same knowledge corpus the coach + briefing use (D33).
    val cloudInsightCoordinator: AiInsightCoordinator = CloudInsightCoordinator(
        aiEnabledFlow = uiPreferences.aiInsightsEnabled,
        configFlow = cloudConfigFlow,
        client = openAiCompatClient,
        scope = appScope,
        knowledgeInjector = knowledgeInjector,
    )
    private val cloudReadyFlow = combine(
        uiPreferences.aiInsightsEnabled,
        cloudConfigComplete,
    ) { enabled, complete -> enabled && complete }

    private val cloudCoachCoordinator: CoachCoordinator = CloudCoachCoordinator(
        cloudReadyFlow = cloudReadyFlow,
        configFlow = cloudConfigFlow,
        client = openAiCompatClient,
        tools = CoachToolsAdapter(
            toolExecutor = coachToolExecutor,
            planRepository = planRepository,
            userProfileStore = userProfilePreferencesStore,
            dateProvider = dateProvider,
            handoffStore = coachHandoffStore,
            journey = coachJourneyStore,
            coachMemory = coachMemoryStore,
            rebalanceState = { rebalanceStore.current() },
        ),
        scope = appScope,
        knowledgeInjector = knowledgeInjector,
        toolSchemas = CLOUD_COACH_TOOL_SCHEMAS,
    )

    // ── Proactive coaching spine (deterministic engine → inbox; cloud phrasing on open) ──
    val coachContextBuilder = CoachContextBuilder(
        logRepository = logRepository,
        planRepository = planRepository,
        workoutSessionRepository = workoutSessionRepository,
        streakRepository = streakRepository,
        userProfileStore = userProfilePreferencesStore,
        dateProvider = dateProvider,
        rebalanceStore = rebalanceStore,
    )
    val coachContextCache = CoachContextCache(coachContextBuilder, dateProvider).also { cache ->
        // A plan or profile edit must invalidate the proactive-engine context snapshot so the next
        // context build (weekly coach note, next digest run, forced refresh) reflects the new targets.
        // Without this the cache only rebuilds on a calendar-day rollover, so a same-day change to
        // calorie/macro targets (or the profile's gym-session target) would be served stale until the
        // next day. `drop(1)` skips each flow's initial (current-value) emission so we invalidate only
        // on an actual change, not on startup.
        appScope.launch {
            merge(
                planRepository.preferences.distinctUntilChanged().drop(1).map { },
                userProfilePreferencesStore.preferences.distinctUntilChanged().drop(1).map { },
            ).collect { cache.invalidate() }
        }
    }
    val coachSignalEngine: CoachSignalEngine = CoachSignalEngine.default()
    val signalSelector = SignalSelector()
    val coachInboxRepository = CoachInboxRepository(context.applicationContext)

    /** Phase 5: the single active Cross-Signal Discovery experiment (Track this → evaluation). */
    val coachExperimentStore = CoachExperimentStore(context.applicationContext)

    /** Stage-2 phrasing decoration for the featured signal, on demand when a surface opens. */
    val coachPhrasingService = CoachPhrasingService(openAiCompatClient) { cloudConfigFlow.value }

    // ── Weekly Rebalance (deterministic engine → DataStore state; cloud phrasing on open) ──
    /**
     * Cloud phrasing DECORATION for the rebalance card — the [CoachPhrasingService] shape, read per
     * call so a settings change takes effect immediately. Consumed by the card VM (Task 7); the
     * deterministic engine has already decided every number, this only rephrases the fallback copy.
     */
    internal val rebalanceCopyService = RebalanceCopyService(openAiCompatClient) { cloudConfigFlow.value }

    // ── Phase-5 push layer ───────────────────────────────────────────────────────────
    val pushHistoryStore = com.zack.recomptracker.data.coach.PushHistoryStore(
        context.applicationContext, dateProvider,
    )
    val coachNotificationPreferences = CoachNotificationPreferences(context.applicationContext)
    val coachNotifier = AndroidCoachNotifier(context.applicationContext)
    val coachPushEmitter = CoachPushEmitter(
        notifier = coachNotifier,
        pushHistory = pushHistoryStore,
        preferences = coachNotificationPreferences,
        rateLimiter = RateLimiter(),
        now = { java.time.LocalDateTime.now() },
    )

    val coachDigestCoordinator = CoachDigestCoordinator(
        contextProvider = { coachContextCache.get() },
        engine = coachSignalEngine,
        selector = signalSelector,
        inbox = coachInboxRepository,
        aiEnabledFlow = uiPreferences.aiInsightsEnabled,
        dateProvider = dateProvider,
        appScope = appScope,
        journey = coachJourneyStore,
        scheduler = WorkManagerCoachDigestScheduler(context.applicationContext),
        pushEmitter = coachPushEmitter,
        notificationPreferences = coachNotificationPreferences,
        experiments = coachExperimentStore,
    )

    /**
     * Weekly Rebalance spine: once-daily reconcile-then-evaluate + the accept/decline/customize
     * transitions and the cancel-on-plan-edit hook. Input assembly is [buildRebalanceInput]. [start]
     * launches the version observer below alongside the other appScope observers. `usageTracker` fires
     * the `REBALANCE_*` events (fire-and-forget, spec §7's analytics line).
     */
    val rebalanceCoordinator = RebalanceCoordinator(
        store = rebalanceStore,
        buildInput = { buildRebalanceInput() },
        planVersions = planRepository.observeVersions(),
        dateProvider = dateProvider,
        usageTracker = usageTracker,
        scope = appScope,
    ).also { it.start() }

    // ── Cloud coordinators handed out to ViewModels (cloud-only, Phase 8) ────────────
    val aiInsightCoordinator: AiInsightCoordinator = cloudInsightCoordinator

    // Naming is available exactly when the cloud insight path is usable (AI enabled + cloud config
    // complete) — the same signal the insight cards gate on.
    private val recipeNamerAvailable: StateFlow<Boolean> =
        cloudReadyFlow.stateIn(appScope, SharingStarted.Eagerly, false)

    val recipeNamer: RecipeNamer = CloudRecipeNamer(
        client = openAiCompatClient,
        configFlow = cloudConfigFlow,
        available = recipeNamerAvailable,
    )

    val coachCoordinator: CoachCoordinator = cloudCoachCoordinator
    val viewModelFactory: ViewModelProvider.Factory = AppViewModelFactory(this)

    /**
     * A [KnowledgeInjector] whose backing delegate is installed asynchronously once the corpus has
     * been parsed off the main thread (see the init block). Starts as [NoOpKnowledgeInjector] so it
     * is safe to call before the parse finishes — it just returns no REFERENCE block — and stays a
     * no-op if the load fails. The delegate is read/written through @Volatile for cross-thread
     * visibility; referenceBlock() itself stays non-suspend so every existing consumer is unchanged.
     */
    private class DeferredKnowledgeInjector : KnowledgeInjector {
        @Volatile private var delegate: KnowledgeInjector = NoOpKnowledgeInjector
        fun setDelegate(injector: KnowledgeInjector) { delegate = injector }
        override fun referenceBlock(query: String): String = delegate.referenceBlock(query)
    }
}

private class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when (modelClass) {
            FoodLogViewModel::class.java -> FoodLogViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                rebalanceStore = container.rebalanceStore,
                dateProvider = container.dateProvider,
            )
            TodayViewModel::class.java -> TodayViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                healthSyncCoordinator = container.healthSyncCoordinator,
                // Q6a: Body RECOVERY_READINESS card now binds directly to the cloud coordinator.
                aiInsightCoordinator = container.cloudInsightCoordinator,
            )
            DashboardViewModel::class.java -> DashboardViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                trendCalculator = container.trendCalculator,
                adherenceCalculator = container.adherenceCalculator,
                adjustmentEngine = container.adjustmentEngine,
                aiInsightCoordinator = container.aiInsightCoordinator,
                userProfileStore = container.userProfilePreferencesStore,
                rebalanceStore = container.rebalanceStore,
            )
            ProgressViewModel::class.java -> ProgressViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                adherenceCalculator = container.adherenceCalculator,
                // Q6a: Trends PROGRESS_TREND card now binds directly to the cloud coordinator.
                aiInsightCoordinator = container.cloudInsightCoordinator,
                userProfileStore = container.userProfilePreferencesStore,
                workoutSessionRepository = container.workoutSessionRepository,
                exerciseLibraryRepository = container.exerciseLibraryRepository,
                rebalanceStore = container.rebalanceStore,
            )
            PlanViewModel::class.java -> PlanViewModel(
                planRepository = container.planRepository,
                userProfileStore = container.userProfilePreferencesStore,
                logRepository = container.logRepository,
            )
            ProfileViewModel::class.java -> ProfileViewModel(
                userProfileStore = container.userProfilePreferencesStore,
                logRepository = container.logRepository,
                dateProvider = container.dateProvider,
            )
            StreakViewModel::class.java -> StreakViewModel(
                streakRepository = container.streakRepository,
                userProfileStore = container.userProfilePreferencesStore,
            )
            CoachTodayViewModel::class.java -> CoachTodayViewModel(
                inbox = container.coachInboxRepository,
                phrase = container.coachPhrasingService::phrase,
                onVisibleRefresh = container.coachDigestCoordinator::runIfDue,
                experiments = container.coachExperimentStore,
                dateProvider = container.dateProvider,
            )
            RebalanceViewModel::class.java -> RebalanceViewModel(
                store = container.rebalanceStore,
                coordinator = container.rebalanceCoordinator,
                copyService = container.rebalanceCopyService,
                dateProvider = container.dateProvider,
            )
            DeveloperViewModel::class.java -> DeveloperViewModel(
                coordinator = container.rebalanceCoordinator,
                store = container.rebalanceStore,
            )
            OnboardingViewModel::class.java -> OnboardingViewModel(
                userProfileStore = container.userProfilePreferencesStore,
                planRepository = container.planRepository,
                logRepository = container.logRepository,
                uiPreferences = container.uiPreferences,
                dateProvider = container.dateProvider,
            )
            AppearanceViewModel::class.java -> AppearanceViewModel(
                uiPreferences = container.uiPreferences,
            )
            FoodsViewModel::class.java -> FoodsViewModel(
                logRepository = container.logRepository,
                dateProvider = container.dateProvider,
            )
            SettingsViewModel::class.java -> SettingsViewModel(
                backupRepository = container.backupRepository,
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                hcRepository = container.healthConnectRepository,
                healthSyncCoordinator = container.healthSyncCoordinator,
                foodCatalogRepository = container.foodCatalogRepository,
                personalFoodRepository = container.personalFoodRepository,
                userProfileStore = container.userProfilePreferencesStore,
                uiPreferences = container.uiPreferences,
            )
            FoodLibraryViewModel::class.java -> FoodLibraryViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                foodCatalogRepository = container.foodCatalogRepository,
                barcodeRepository = container.barcodeRepository,
                recipeRepository = container.recipeRepository,
            )
            BodyHistoryViewModel::class.java -> BodyHistoryViewModel(
                logRepository = container.logRepository,
                dateProvider = container.dateProvider,
            )
            BodyEditViewModel::class.java -> BodyEditViewModel(
                logRepository = container.logRepository,
                savedStateHandle = extras.createSavedStateHandle(),
            )
            BarcodeScannerViewModel::class.java -> BarcodeScannerViewModel(
                barcodeRepository = container.barcodeRepository,
                logRepository = container.logRepository,
                dateProvider = container.dateProvider,
            )
            AiCoachViewModel::class.java -> AiCoachViewModel(
                uiPreferences = container.uiPreferences,
                aiInsightCoordinator = container.aiInsightCoordinator,
                secureKeyStore = container.secureKeyStore,
                openAiCompatClient = container.openAiCompatClient,
                coachDigestCoordinator = container.coachDigestCoordinator,
                coachNotificationPreferences = container.coachNotificationPreferences,
            )
            CoachViewModel::class.java -> CoachViewModel(
                coachCoordinator = container.coachCoordinator,
            )
            CoachMemoryViewModel::class.java -> CoachMemoryViewModel(container.coachMemoryStore)
            WeeklyReviewViewModel::class.java -> WeeklyReviewViewModel(
                WeeklyReviewConfig(
                    cloudActiveFlow = container.cloudBriefingActive,
                    reviewDataFlow = container.weeklyReviewDataFlow,
                    signatureOf = { container.weeklyReviewComputer.signature(it) },
                    briefingFor = { weekStart, signature, generate ->
                        container.weeklyBriefingRepository.briefingFor(weekStart, signature, generate)
                    },
                    generate = { data ->
                        val config = container.cloudConfigForBriefing() ?: error("Cloud not configured")
                        val coachNote = container.weeklyCoachNote()
                        container.weeklyBriefingGenerator.generate(config, data, coachNote)
                            ?: error("Generation failed")
                    },
                    saveCalorieTarget = { target ->
                        val prefs = container.planRepository.preferences.first()
                        container.planRepository.save(prefs.withCalorieTarget(target))
                    },
                    markSeen = { signature -> container.uiPreferences.setLastSeenBriefingSignature(signature) },
                    lastSeenSignatureFlow = container.uiPreferences.lastSeenBriefingSignature,
                    startCoachHandoff = { data, briefing ->
                        container.coachHandoffStore.set(buildCoachHandoffContext(data, briefing))
                        container.coachCoordinator.clearHistory()
                    },
                ),
            )
            RecipeBuilderViewModel::class.java -> RecipeBuilderViewModel(
                recipeRepository = container.recipeRepository,
                recipeNamer = container.recipeNamer,
                savedStateHandle = extras.createSavedStateHandle(),
            )
            TrainViewModel::class.java -> TrainViewModel(
                workoutRepository = container.workoutRepository,
                sessionRepository = container.workoutSessionRepository,
                exerciseLibraryRepository = container.exerciseLibraryRepository,
                logRepository = container.logRepository,
                userProfileStore = container.userProfilePreferencesStore,
                dateProvider = container.dateProvider,
            )
            ExercisePickerViewModel::class.java -> ExercisePickerViewModel(
                repository = container.exerciseLibraryRepository,
            )
            RoutineBuilderViewModel::class.java -> RoutineBuilderViewModel(
                workoutRepository = container.workoutRepository,
                exerciseLibraryRepository = container.exerciseLibraryRepository,
            )
            ActiveSessionViewModel::class.java -> ActiveSessionViewModel(
                sessionRepository = container.workoutSessionRepository,
                exerciseLibraryRepository = container.exerciseLibraryRepository,
            )
            SessionSummaryViewModel::class.java -> SessionSummaryViewModel(
                sessionRepository = container.workoutSessionRepository,
                exerciseLibraryRepository = container.exerciseLibraryRepository,
                savedStateHandle = extras.createSavedStateHandle(),
            )
            SessionDetailViewModel::class.java -> SessionDetailViewModel(
                sessionRepository = container.workoutSessionRepository,
                savedStateHandle = extras.createSavedStateHandle(),
            )
            ExerciseStatsViewModel::class.java -> ExerciseStatsViewModel(
                sessionRepository = container.workoutSessionRepository,
                exerciseLibraryRepository = container.exerciseLibraryRepository,
                savedStateHandle = extras.createSavedStateHandle(),
            )
            UsageStatsViewModel::class.java -> UsageStatsViewModel(
                dao = container.database.usageEventDao(),
            )
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}

private fun buildCoachHandoffContext(
    data: WeeklyReviewData,
    briefing: com.zack.recomptracker.ai.WeeklyBriefing,
): String = buildString {
    appendLine("=== WEEKLY BRIEFING CONTEXT ===")
    appendLine("The user just read this week's briefing and opened chat to ask about it.")
    appendLine("Do NOT re-explain or summarize the briefing. Greet in at most one short line, then wait for their question and answer concisely from the data below.")
    appendLine()
    appendLine("Week starting: ${data.weekStart} | Phase: ${data.phase.name}")
    appendLine("Verdict: ${briefing.action.verdict} | Days logged: ${data.daysLogged} | Adherence: ${data.input.adherencePercent.toInt()}%")
    appendLine("Recommended calorie change: ${data.result.recommendedCalorieChange} kcal")
    appendLine("Signals:")
    briefing.signals.forEach { appendLine("- ${it.label}: ${it.value} — ${it.interpretation}") }
    appendLine("Headline shown: ${briefing.headline}")
    appendLine("Narrative shown: ${briefing.narrative}")
    append("=== END WEEKLY BRIEFING CONTEXT ===")
}
