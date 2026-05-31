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
import com.zack.recomptracker.ui.body.BodyHistoryViewModel
import com.zack.recomptracker.ui.dashboard.DashboardViewModel
import com.zack.recomptracker.ui.foodlibrary.FoodLibraryViewModel
import com.zack.recomptracker.ui.foods.FoodsViewModel
import com.zack.recomptracker.ui.plan.PlanViewModel
import com.zack.recomptracker.ui.progress.ProgressViewModel
import com.zack.recomptracker.ui.settings.SettingsViewModel
import com.zack.recomptracker.ui.today.TodayViewModel

class AppContainer(context: Context) {
    val dateProvider: DateProvider = SystemDateProvider()
    val database: RecompDatabase = RecompDatabase.create(context)
    private val appPreferences = AppPreferences(context.applicationContext)
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
    val personalFoodRepository = PersonalFoodRepository(database.savedFoodDao())
    val healthConnectRepository = HealthConnectRepository(context.applicationContext)
    val adjustmentEngine = AdjustmentEngine()
    val trendCalculator = TrendCalculator()
    val adherenceCalculator = AdherenceCalculator()
    val viewModelFactory: ViewModelProvider.Factory = AppViewModelFactory(this)
}

private class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
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
            )
            FoodLibraryViewModel::class.java -> FoodLibraryViewModel(
                logRepository = container.logRepository,
                planRepository = container.planRepository,
                dateProvider = container.dateProvider,
                foodCatalogRepository = container.foodCatalogRepository,
            )
            BodyHistoryViewModel::class.java -> BodyHistoryViewModel(
                logRepository = container.logRepository,
                dateProvider = container.dateProvider,
            )
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        } as T
    }
}
