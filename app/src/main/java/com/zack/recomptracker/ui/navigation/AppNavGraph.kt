package com.zack.recomptracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.zack.recomptracker.ui.LocalAppContainer
import com.zack.recomptracker.ui.dashboard.DashboardScreen
import com.zack.recomptracker.ui.dashboard.DashboardViewModel
import com.zack.recomptracker.ui.dashboard.HomeDashboardScreen
import com.zack.recomptracker.ui.foods.FoodsScreen
import com.zack.recomptracker.ui.foods.FoodsViewModel
import com.zack.recomptracker.ui.more.MoreScreen
import com.zack.recomptracker.ui.more.MoreViewModel
import com.zack.recomptracker.ui.plan.PlanScreen
import com.zack.recomptracker.ui.plan.PlanViewModel
import com.zack.recomptracker.ui.progress.ProgressScreen
import com.zack.recomptracker.ui.progress.ProgressViewModel
import com.zack.recomptracker.ui.settings.SettingsScreen
import com.zack.recomptracker.ui.settings.SettingsViewModel
import com.zack.recomptracker.ui.foodlibrary.FoodLibraryScreen
import com.zack.recomptracker.ui.foodlibrary.FoodLibraryViewModel
import com.zack.recomptracker.ui.today.BodyRecoveryScreen
import com.zack.recomptracker.ui.today.FoodLogViewModel
import com.zack.recomptracker.ui.today.FoodScreen
import com.zack.recomptracker.ui.today.TodayViewModel
import com.zack.recomptracker.ui.body.BodyEditViewModel
import com.zack.recomptracker.ui.body.BodyHistoryViewModel
import com.zack.recomptracker.ui.body.BodyHistoryScreen
import com.zack.recomptracker.ui.body.BodyEditScreen
import com.zack.recomptracker.ui.scanner.BarcodeScannerScreen
import com.zack.recomptracker.ui.scanner.BarcodeScannerViewModel
import java.time.LocalDate

enum class TopLevelDestination(
    val route: String,
    val label: String,
) {
    Home("home", "Home"),
    Body("body", "Body"),
    Progress("progress", "Progress"),
    More("more", "More"),
}

object Routes {
    const val Food      = "food"
    const val Stats     = "stats"
    const val Charts    = "charts"
    const val Plan      = "plan"
    const val FoodLibrary = "food_library"
    const val Foods     = "foods"
    const val Settings  = "settings"
    const val BodyHistory = "body_history"
    const val BodyEdit  = "body_edit/{date}"
    fun bodyEdit(date: LocalDate) = "body_edit/$date"
    const val BarcodeScanner = "barcode_scanner?slotId={slotId}&slotName={slotName}"
    fun barcodeScanner(slotId: Long?, slotName: String) =
        "barcode_scanner?slotId=${slotId ?: -1L}&slotName=${java.net.URLEncoder.encode(slotName, "UTF-8")}"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val factory = LocalAppContainer.current.viewModelFactory
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Home.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.Home.route) {
            HomeDashboardScreen(
                viewModel = viewModel<DashboardViewModel>(factory = factory),
                onCheckIn = {
                    navController.navigate(TopLevelDestination.Body.route) {
                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onLogFood = {
                    navController.navigate(Routes.Food) {
                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(Routes.Food) {
            FoodScreen(
                viewModel = viewModel<FoodLogViewModel>(factory = factory),
                onAddToSlot = { slotId, slotName, date ->
                    navController.navigate(
                        "${Routes.FoodLibrary}?slotId=$slotId&slotName=${java.net.URLEncoder.encode(slotName, "UTF-8")}&date=$date"
                    )
                },
                onBrowseLibrary = { navController.navigate(Routes.FoodLibrary) },
                onEditEntryAmount = { slotId, slotName, entryId, date ->
                    navController.navigate(
                        "${Routes.FoodLibrary}?slotId=${slotId ?: -1L}&slotName=${java.net.URLEncoder.encode(slotName, "UTF-8")}&editEntryId=$entryId&date=$date"
                    )
                },
            )
        }
        composable(TopLevelDestination.Body.route) {
            BodyRecoveryScreen(
                viewModel = viewModel<TodayViewModel>(factory = factory),
                onViewHistory = { navController.navigate(Routes.BodyHistory) },
            )
        }
        composable(TopLevelDestination.Progress.route) {
            ProgressScreen(viewModel<ProgressViewModel>(factory = factory))
        }
        composable(Routes.BodyHistory) {
            BodyHistoryScreen(
                viewModel = viewModel<BodyHistoryViewModel>(factory = factory),
                onEditDay = { date -> navController.navigate(Routes.bodyEdit(date)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.BodyEdit,
            arguments = listOf(
                androidx.navigation.navArgument("date") {
                    type = androidx.navigation.NavType.StringType
                },
            ),
        ) {
            BodyEditScreen(
                viewModel = viewModel<BodyEditViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.Stats) {
            DashboardScreen(viewModel<DashboardViewModel>(factory = factory))
        }
        composable(Routes.Charts) {
            ProgressScreen(viewModel<ProgressViewModel>(factory = factory))
        }
        composable(Routes.Plan) {
            PlanScreen(viewModel<PlanViewModel>(factory = factory))
        }
        composable(TopLevelDestination.More.route) {
            MoreScreen(
                viewModel      = viewModel<MoreViewModel>(factory = factory),
                onStatsClick   = { navController.navigate(Routes.Stats) },
                onChartsClick  = { navController.navigate(Routes.Charts) },
                onPlanClick    = { navController.navigate(Routes.Plan) },
            )
        }
        composable(Routes.Foods) {
            FoodsScreen(viewModel<FoodsViewModel>(factory = factory))
        }
        composable(
            route = "${Routes.FoodLibrary}?slotId={slotId}&slotName={slotName}&editEntryId={editEntryId}&date={date}",
            arguments = listOf(
                androidx.navigation.navArgument("slotId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
                androidx.navigation.navArgument("slotName") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
                androidx.navigation.navArgument("editEntryId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
                androidx.navigation.navArgument("date") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val slotId = backStackEntry.arguments?.getLong("slotId")?.takeIf { it != -1L }
            val slotName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("slotName").orEmpty(),
                "UTF-8"
            )
            val editEntryId = backStackEntry.arguments?.getLong("editEntryId")?.takeIf { it != -1L }
            val logDate = backStackEntry.arguments?.getString("date").orEmpty()
            FoodLibraryScreen(
                viewModel   = viewModel<FoodLibraryViewModel>(factory = factory),
                slotId      = slotId,
                slotName    = slotName,
                onBack      = { navController.popBackStack() },
                editEntryId = editEntryId,
                logDate     = logDate,
                onScanBarcode = {
                    navController.navigate(Routes.barcodeScanner(slotId, slotName))
                },
            )
        }
        composable(
            route = Routes.BarcodeScanner,
            arguments = listOf(
                androidx.navigation.navArgument("slotId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
                androidx.navigation.navArgument("slotName") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val slotId = backStackEntry.arguments?.getLong("slotId")?.takeIf { it != -1L }
            val slotName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("slotName").orEmpty(), "UTF-8"
            )
            BarcodeScannerScreen(
                viewModel = viewModel<BarcodeScannerViewModel>(factory = factory),
                slotId = slotId,
                slotName = slotName,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(viewModel<SettingsViewModel>(factory = factory))
        }
    }
}
