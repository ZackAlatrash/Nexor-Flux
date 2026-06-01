package com.zack.recomptracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.zack.recomptracker.ui.plan.PlanScreen
import com.zack.recomptracker.ui.plan.PlanViewModel
import com.zack.recomptracker.ui.progress.ProgressScreen
import com.zack.recomptracker.ui.progress.ProgressViewModel
import com.zack.recomptracker.ui.settings.SettingsScreen
import com.zack.recomptracker.ui.settings.SettingsViewModel
import com.zack.recomptracker.ui.foodlibrary.FoodLibraryScreen
import com.zack.recomptracker.ui.foodlibrary.FoodLibraryViewModel
import com.zack.recomptracker.ui.today.BodyRecoveryScreen
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
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Default.Home),
    Food("food", "Food", Icons.Default.Restaurant),
    Body("body", "Body", Icons.Default.Favorite),
    More("more", "More", Icons.Default.MoreHoriz),
}

object Routes {
    const val Stats = "stats"
    const val Charts = "charts"
    const val Plan = "plan"
    const val FoodLibrary = "food_library"
    const val Foods = "foods"
    const val Settings = "settings"
    const val BodyHistory = "body_history"
    const val BodyEdit = "body_edit/{date}"
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
                onLogFood = { navController.navigate(TopLevelDestination.Food.route) },
                onUpdateBody = { navController.navigate(TopLevelDestination.Body.route) },
            )
        }
        composable(TopLevelDestination.Food.route) {
            FoodScreen(
                viewModel = viewModel(factory = factory),
                onAddToSlot = { slotId, slotName ->
                    navController.navigate(
                        "${Routes.FoodLibrary}?slotId=$slotId&slotName=${java.net.URLEncoder.encode(slotName, "UTF-8")}"
                    )
                },
                onBrowseLibrary = { navController.navigate(Routes.FoodLibrary) },
                onEditEntryAmount = { slotId, slotName, entryId ->
                    navController.navigate(
                        "${Routes.FoodLibrary}?slotId=${slotId ?: -1L}&slotName=${java.net.URLEncoder.encode(slotName, "UTF-8")}&editEntryId=$entryId"
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
                onStatsClick = { navController.navigate(Routes.Stats) },
                onChartsClick = { navController.navigate(Routes.Charts) },
                onPlanClick = { navController.navigate(Routes.Plan) },
                onSettingsClick = { navController.navigate(Routes.Settings) },
            )
        }
        composable(Routes.Foods) {
            FoodsScreen(viewModel<FoodsViewModel>(factory = factory))
        }
        composable(
            route = "${Routes.FoodLibrary}?slotId={slotId}&slotName={slotName}&editEntryId={editEntryId}",
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
            ),
        ) { backStackEntry ->
            val slotId = backStackEntry.arguments?.getLong("slotId")?.takeIf { it != -1L }
            val slotName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("slotName").orEmpty(),
                "UTF-8"
            )
            val editEntryId = backStackEntry.arguments?.getLong("editEntryId")?.takeIf { it != -1L }
            FoodLibraryScreen(
                viewModel = viewModel<FoodLibraryViewModel>(factory = factory),
                slotId = slotId,
                slotName = slotName,
                onBack = { navController.popBackStack() },
                editEntryId = editEntryId,
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
