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
import com.zack.recomptracker.ai.AiInsightCoordinator
import com.zack.recomptracker.ai.CoachCoordinator
import com.zack.recomptracker.ai.CoachToolExecutor
import com.zack.recomptracker.ai.GemmaServiceHolder
import com.zack.recomptracker.ai.GemmaCoachCoordinator
import com.zack.recomptracker.ai.GemmaInsightCoordinator
import com.zack.recomptracker.data.preferences.UiPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
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
    val healthConnectRepository = HealthConnectRepository(context.applicationContext)
    val adjustmentEngine = AdjustmentEngine()
    val trendCalculator = TrendCalculator()
    val adherenceCalculator = AdherenceCalculator()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val gemmaServiceHolder = GemmaServiceHolder(context)
    val aiInsightCoordinator: AiInsightCoordinator = GemmaInsightCoordinator(
        context = context,
        aiEnabledFlow = uiPreferences.aiInsightsEnabled,
        scope = appScope,
        serviceHolder = gemmaServiceHolder,
        uiPreferences = uiPreferences,
    )
    val coachCoordinator: CoachCoordinator = GemmaCoachCoordinator(
        serviceHolder = gemmaServiceHolder,
        insightCoordinator = aiInsightCoordinator,
        toolExecutor = CoachToolExecutor(
            logRepository = logRepository,
            planRepository = planRepository,
            dateProvider = dateProvider,
        ),
        planRepository = planRepository,
        userProfileStore = userProfilePreferencesStore,
        dateProvider = dateProvider,
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
            )
            TodayViewModel::class.java -> TodayViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                hcRepository = container.healthConnectRepository,
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
            )
            FoodLibraryViewModel::class.java -> FoodLibraryViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                foodCatalogRepository = container.foodCatalogRepository,
                barcodeRepository = container.barcodeRepository,
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
            )
            CoachViewModel::class.java -> CoachViewModel(
                coachCoordinator = container.coachCoordinator,
            )
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
