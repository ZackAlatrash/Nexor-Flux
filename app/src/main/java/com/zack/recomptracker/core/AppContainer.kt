package com.zack.recomptracker.core

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.core.time.SystemDateProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.preferences.AppPreferences
import com.zack.recomptracker.data.health.HealthConnectRepository
import com.zack.recomptracker.data.repository.BackupRepository
import com.zack.recomptracker.data.repository.FoodCatalogRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PersonalFoodRepository
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.domain.adjustment.AdjustmentEngine
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
import com.zack.recomptracker.ai.GemmaServiceHolder
import com.zack.recomptracker.ai.GemmaCoachCoordinator
import com.zack.recomptracker.ai.GemmaInsightCoordinator
import com.zack.recomptracker.ai.RoutingCoachCoordinator
import com.zack.recomptracker.ai.RoutingInsightCoordinator
import com.zack.recomptracker.data.preferences.SecureKeyStore
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.remote.CloudConfig
import com.zack.recomptracker.data.remote.OpenAiCompatClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.zack.recomptracker.ui.coach.CoachViewModel
import com.zack.recomptracker.ui.body.BodyEditViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.zack.recomptracker.ui.body.BodyHistoryViewModel
import com.zack.recomptracker.ui.dashboard.DashboardViewModel
import com.zack.recomptracker.ui.foodlibrary.FoodLibraryViewModel
import com.zack.recomptracker.ui.foods.FoodsViewModel
import com.zack.recomptracker.ui.more.MoreViewModel
import com.zack.recomptracker.ui.plan.PlanViewModel
import com.zack.recomptracker.ui.progress.ProgressViewModel
import com.zack.recomptracker.ui.settings.SettingsViewModel
import com.zack.recomptracker.ui.today.FoodLogViewModel
import com.zack.recomptracker.ui.today.TodayViewModel
import com.zack.recomptracker.data.remote.OpenFoodFactsApi
import com.zack.recomptracker.data.repository.BarcodeRepository
import com.zack.recomptracker.data.repository.RecipeRepository
import com.zack.recomptracker.ui.recipes.RecipeBuilderViewModel
import com.zack.recomptracker.ui.scanner.BarcodeScannerViewModel

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
    private val coachToolExecutor = CoachToolExecutor(
        logRepository = logRepository,
        planRepository = planRepository,
        dateProvider = dateProvider,
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
    )

    // ── Routers (handed out to ViewModels) ──────────────────────────────────────────
    val aiInsightCoordinator: AiInsightCoordinator = RoutingInsightCoordinator(
        local = gemmaInsightCoordinator,
        cloud = cloudInsightCoordinator,
        backendFlow = uiPreferences.aiBackend,
        cloudConfigCompleteFlow = cloudConfigComplete,
        scope = appScope,
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
            )
            ProgressViewModel::class.java -> ProgressViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                adherenceCalculator = container.adherenceCalculator,
                aiInsightCoordinator = container.aiInsightCoordinator,
            )
            PlanViewModel::class.java -> PlanViewModel(container.planRepository)
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
            MoreViewModel::class.java -> MoreViewModel(
                uiPreferences = container.uiPreferences,
                hcRepository = container.healthConnectRepository,
                backupRepository = container.backupRepository,
                aiInsightCoordinator = container.aiInsightCoordinator,
                secureKeyStore = container.secureKeyStore,
                openAiCompatClient = container.openAiCompatClient,
            )
            CoachViewModel::class.java -> CoachViewModel(
                coachCoordinator = container.coachCoordinator,
            )
            RecipeBuilderViewModel::class.java -> RecipeBuilderViewModel(
                recipeRepository = container.recipeRepository,
                savedStateHandle = extras.createSavedStateHandle(),
            )
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
