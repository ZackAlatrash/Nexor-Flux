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
import com.zack.recomptracker.data.repository.BackupRepository
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.FoodCatalogRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PersonalFoodRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.adjustment.AdjustmentEngine
import com.zack.recomptracker.domain.adjustment.AdjustmentThresholds
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.trend.TrendCalculator
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.zack.recomptracker.ai.AiBackend
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.CloudCoachCoordinator
import com.zack.recomptracker.ai.CloudInsightCoordinator
import com.zack.recomptracker.ai.CoachCoordinator
import com.zack.recomptracker.ai.CoachToolExecutor
import com.zack.recomptracker.ai.CoachHandoffStore
import com.zack.recomptracker.ai.CoachToolsAdapter
import com.zack.recomptracker.ai.AiInsightState
import com.zack.recomptracker.ai.CloudNameGenerator
import com.zack.recomptracker.ai.GemmaServiceHolder
import com.zack.recomptracker.ai.GemmaCoachCoordinator
import com.zack.recomptracker.ai.GemmaInsightCoordinator
import com.zack.recomptracker.ai.LocalNameGenerator
import com.zack.recomptracker.ai.RecipeNamer
import com.zack.recomptracker.ai.RoutingCoachCoordinator
import com.zack.recomptracker.ai.RoutingInsightCoordinator
import com.zack.recomptracker.ai.CLOUD_COACH_TOOL_SCHEMAS
import com.zack.recomptracker.ai.RoutingRecipeNamer
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
import kotlinx.coroutines.flow.stateIn
import com.zack.recomptracker.ui.coach.CoachViewModel
import com.zack.recomptracker.ui.body.BodyEditViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.zack.recomptracker.ui.aicoach.AiCoachViewModel
import com.zack.recomptracker.ui.appearance.AppearanceViewModel
import com.zack.recomptracker.ui.body.BodyHistoryViewModel
import com.zack.recomptracker.ui.dashboard.DashboardViewModel
import com.zack.recomptracker.ui.foodlibrary.FoodLibraryViewModel
import com.zack.recomptracker.ui.foods.FoodsViewModel
import com.zack.recomptracker.ui.onboarding.OnboardingViewModel
import com.zack.recomptracker.ui.plan.PlanViewModel
import com.zack.recomptracker.ui.profile.ProfileViewModel
import com.zack.recomptracker.ui.progress.ProgressViewModel
import com.zack.recomptracker.ui.settings.SettingsViewModel
import com.zack.recomptracker.ui.today.FoodLogViewModel
import com.zack.recomptracker.ui.today.TodayViewModel
import com.zack.recomptracker.ui.train.ActiveSessionViewModel
import com.zack.recomptracker.ui.train.ExercisePickerViewModel
import com.zack.recomptracker.ui.train.RoutineBuilderViewModel
import com.zack.recomptracker.ui.train.SessionDetailViewModel
import com.zack.recomptracker.ui.train.SessionSummaryViewModel
import com.zack.recomptracker.ui.train.TrainViewModel
import com.zack.recomptracker.ui.train.component.MuscleArt
import com.zack.recomptracker.data.remote.OpenFoodFactsApi
import com.zack.recomptracker.data.repository.BarcodeRepository
import com.zack.recomptracker.data.repository.RecipeRepository
import com.zack.recomptracker.ui.recipes.RecipeBuilderViewModel
import com.zack.recomptracker.ui.scanner.BarcodeScannerViewModel
import com.zack.recomptracker.ai.WeeklyBriefingGenerator
import com.zack.recomptracker.data.repository.WeeklyBriefingRepository
import com.zack.recomptracker.domain.review.WeeklyReviewComputer
import com.zack.recomptracker.domain.review.WeeklyReviewData
import com.zack.recomptracker.ui.review.WeeklyReviewConfig
import com.zack.recomptracker.ui.review.WeeklyReviewViewModel
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.domain.adherence.NutritionDay
import com.zack.recomptracker.domain.adjustment.AdjustmentInput
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
    val planRepository = PlanRepository(appPreferences)
    val logRepository = LogRepository(
        dailyLogDao = database.dailyLogDao(),
        mealEntryDao = database.mealEntryDao(),
        savedFoodDao = database.savedFoodDao(),
        savedMealDao = database.savedMealDao(),
        performanceDao = database.performanceDao(),
        weeklyReviewDao = database.weeklyReviewDao(),
        mealSlotDao = database.mealSlotDao(),
    )
    val backupRepository = BackupRepository(database, planRepository)
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
    val exerciseLibraryRepository = ExerciseLibraryRepository(database.exerciseDao())
    val workoutRepository = WorkoutRepository(database.workoutDao())
    val workoutSessionRepository = WorkoutSessionRepository(database.workoutSessionDao())

    init {
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
    }

    val gemmaServiceHolder = GemmaServiceHolder(context)
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

    // ── Local (Gemma) coordinators ───────────────────────────────────────────────
    private val gemmaInsightCoordinator: AiInsightCoordinator = GemmaInsightCoordinator(
        context = context,
        aiEnabledFlow = uiPreferences.aiInsightsEnabled,
        scope = appScope,
        serviceHolder = gemmaServiceHolder,
        uiPreferences = uiPreferences,
    )
    private val webSearchProvider = TavilyWebSearchProvider(
        keyProvider = { secureKeyStore.getWebSearchKey() },
    )
    private val coachToolExecutor = CoachToolExecutor(
        logRepository = logRepository,
        planRepository = planRepository,
        dateProvider = dateProvider,
        webSearchProvider = webSearchProvider,
    )
    private val gemmaCoachCoordinator: CoachCoordinator = GemmaCoachCoordinator(
        serviceHolder = gemmaServiceHolder,
        insightCoordinator = gemmaInsightCoordinator,
        toolExecutor = coachToolExecutor,
        planRepository = planRepository,
        userProfileStore = userProfilePreferencesStore,
        dateProvider = dateProvider,
        scope = appScope,
    )

    val coachHandoffStore = CoachHandoffStore()

    val weeklyReviewComputer = WeeklyReviewComputer()
    val weeklyBriefingGenerator = WeeklyBriefingGenerator(openAiCompatClient)
    val weeklyBriefingRepository = WeeklyBriefingRepository(database.weeklyReviewDao())

    /** Feature gate: cloud backend selected, config complete, AI enabled. */
    val cloudBriefingActive: StateFlow<Boolean> = combine(
        uiPreferences.aiBackend,
        cloudConfigComplete,
        uiPreferences.aiInsightsEnabled,
    ) { backend, complete, enabled ->
        backend == AiBackend.CLOUD && complete && enabled
    }.stateIn(appScope, SharingStarted.Eagerly, false)

    /** Current review week's deterministic data, recomputed from the same sources as the dashboard. */
    val weeklyReviewDataFlow: Flow<WeeklyReviewData?> = combine(
        logRepository.observeDailyLogs(),
        logRepository.observeMealEntriesSince(dateProvider.today().minusDays(27)),
        logRepository.observePerformances(),
        planRepository.preferences,
    ) { logs, meals, performances, prefs ->
        computeWeeklyReviewData(logs, meals, performances, prefs)
    }

    fun cloudConfigForBriefing(): CloudConfig? = cloudConfigFlow.value

    private fun computeWeeklyReviewData(
        logs: List<DailyLogEntity>,
        allMeals: List<MealEntryEntity>,
        performances: List<LiftPerformanceEntity>,
        prefs: PlanPreferences,
    ): WeeklyReviewData? {
        val today = dateProvider.today()
        val meals = allMeals.filterNot { it.planned }
        val last14Start = today.minusDays(13)
        val last28Start = today.minusDays(27)
        val logs28 = logs.filter { LocalDate.parse(it.date) in last28Start..today }
        val meals14 = meals.filter { LocalDate.parse(it.date) in last14Start..today }
        val mealsByDate = meals14.groupBy { LocalDate.parse(it.date) }
        val nutritionDays = (0..13).map { off ->
            val d = last14Start.plusDays(off.toLong())
            NutritionDay(d, mealsByDate[d].orEmpty().macroTotals().calories)
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
            adherencePercent = adherenceCalculator.calculate(nutritionDays, prefs.targetCalories),
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
        return weeklyReviewComputer.build(weekStart, input, result, prefs.targetCalories)
    }

    // ── Cloud coordinators ─────────────────────────────────────────────────────────
    private val cloudInsightCoordinator: AiInsightCoordinator = CloudInsightCoordinator(
        aiEnabledFlow = uiPreferences.aiInsightsEnabled,
        configFlow = cloudConfigFlow,
        client = openAiCompatClient,
        scope = appScope,
    )
    private val cloudReadyFlow = combine(
        uiPreferences.aiInsightsEnabled,
        cloudConfigComplete,
    ) { enabled, complete -> enabled && complete }
    // Knowledge base: loaded once from assets. Degrades to a no-op if the corpus is missing or
    // invalid, so a bad ingestion run can never crash the app. Loaded synchronously here — fine for
    // the small seed corpus; TODO: move to async init if the corpus grows beyond ~50 chunks.
    private val knowledgeInjector: KnowledgeInjector = runCatching {
        val raw = context.applicationContext.assets
            .open("knowledge/corpus.json")
            .bufferedReader()
            .use { it.readText() }
        RetrievalKnowledgeInjector(KeywordKnowledgeRetriever(KnowledgeCorpus.fromJson(raw).chunks))
    }.getOrElse {
        Log.w("RecompKnowledge", "Knowledge corpus load failed — coach will run without knowledge injection", it)
        NoOpKnowledgeInjector
    }

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
        ),
        scope = appScope,
        knowledgeInjector = knowledgeInjector,
        toolSchemas = CLOUD_COACH_TOOL_SCHEMAS,
    )

    // ── Routers (handed out to ViewModels) ──────────────────────────────────────────
    val aiInsightCoordinator: AiInsightCoordinator = RoutingInsightCoordinator(
        local = gemmaInsightCoordinator,
        cloud = cloudInsightCoordinator,
        backendFlow = uiPreferences.aiBackend,
        cloudConfigCompleteFlow = cloudConfigComplete,
        scope = appScope,
    )

    // Effective backend for naming — same rule as RoutingInsightCoordinator.
    private val recipeNamerBackend: StateFlow<AiBackend> =
        combine(uiPreferences.aiBackend, cloudConfigComplete) { backend, complete ->
            if (backend == AiBackend.CLOUD && complete) AiBackend.CLOUD else AiBackend.LOCAL
        }.stateIn(appScope, SharingStarted.Eagerly, AiBackend.LOCAL)

    // Naming is available exactly when the routed insight model is usable (covers
    // AI-enabled, backend, cloud-config, and on-device model presence in one signal).
    private val recipeNamerAvailable: StateFlow<Boolean> =
        aiInsightCoordinator.state.map { state ->
            when (state) {
                AiInsightState.Disabled,
                AiInsightState.ModelMissing,
                is AiInsightState.Downloading,
                AiInsightState.DownloadFailed,
                AiInsightState.ModelVerifying -> false
                else -> true
            }
        }.stateIn(appScope, SharingStarted.Eagerly, false)

    val recipeNamer: RecipeNamer = RoutingRecipeNamer(
        local = LocalNameGenerator(gemmaServiceHolder) { gemmaInsightCoordinator.selectedModel.value },
        cloud = CloudNameGenerator(openAiCompatClient, cloudConfigFlow),
        effectiveBackend = recipeNamerBackend,
        available = recipeNamerAvailable,
    )

    val coachCoordinator: CoachCoordinator = RoutingCoachCoordinator(
        local = gemmaCoachCoordinator,
        cloud = cloudCoachCoordinator,
        backendFlow = uiPreferences.aiBackend,
        cloudConfigCompleteFlow = cloudConfigComplete,
        scope = appScope,
    )
    val viewModelFactory: ViewModelProvider.Factory = AppViewModelFactory(this)
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
                dateProvider = container.dateProvider,
                aiInsightCoordinator = container.aiInsightCoordinator,
            )
            TodayViewModel::class.java -> TodayViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                hcRepository = container.healthConnectRepository,
                aiInsightCoordinator = container.aiInsightCoordinator,
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
            )
            ProgressViewModel::class.java -> ProgressViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                adherenceCalculator = container.adherenceCalculator,
                aiInsightCoordinator = container.aiInsightCoordinator,
            )
            PlanViewModel::class.java -> PlanViewModel(
                planRepository = container.planRepository,
                userProfileStore = container.userProfilePreferencesStore,
                logRepository = container.logRepository,
            )
            ProfileViewModel::class.java -> ProfileViewModel(
                userProfileStore = container.userProfilePreferencesStore,
                logRepository = container.logRepository,
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
            )
            CoachViewModel::class.java -> CoachViewModel(
                coachCoordinator = container.coachCoordinator,
            )
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
                        container.weeklyBriefingGenerator.generate(config, data) ?: error("Generation failed")
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
