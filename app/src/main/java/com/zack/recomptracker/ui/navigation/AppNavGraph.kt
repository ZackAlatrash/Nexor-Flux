package com.zack.recomptracker.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.zack.recomptracker.ui.LocalAppContainer
import com.zack.recomptracker.ui.dashboard.DashboardScreen
import com.zack.recomptracker.ui.dashboard.DashboardViewModel
import com.zack.recomptracker.ui.dashboard.HomeDashboardScreen
import com.zack.recomptracker.ui.review.WeeklyReviewViewModel
import com.zack.recomptracker.ui.foods.FoodsScreen
import com.zack.recomptracker.ui.foods.FoodsViewModel
import com.zack.recomptracker.ui.more.MoreScreen
import com.zack.recomptracker.ui.plan.PlanScreen
import com.zack.recomptracker.ui.plan.PlanViewModel
import com.zack.recomptracker.ui.progress.ProgressScreen
import com.zack.recomptracker.ui.progress.ProgressViewModel
import com.zack.recomptracker.ui.settings.SettingsViewModel
import com.zack.recomptracker.ui.onboarding.OnboardingScreen
import com.zack.recomptracker.ui.onboarding.OnboardingViewModel
import com.zack.recomptracker.ui.profile.ProfileScreen
import com.zack.recomptracker.ui.profile.ProfileViewModel
import com.zack.recomptracker.ui.aicoach.AiCoachScreen
import com.zack.recomptracker.ui.aicoach.AiCoachViewModel
import com.zack.recomptracker.ui.appearance.AppearanceScreen
import com.zack.recomptracker.ui.appearance.AppearanceViewModel
import com.zack.recomptracker.ui.integrations.IntegrationsScreen
import com.zack.recomptracker.ui.databackup.DataBackupScreen
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
import com.zack.recomptracker.ui.recipes.RecipeBuilderScreen
import com.zack.recomptracker.ui.recipes.RecipeBuilderViewModel
import com.zack.recomptracker.ui.scanner.BarcodeScannerScreen
import com.zack.recomptracker.ui.scanner.BarcodeScannerViewModel
import com.zack.recomptracker.ui.streak.StreakStatsScreen
import com.zack.recomptracker.ui.streak.StreakViewModel
import com.zack.recomptracker.ui.train.ActiveSessionScreen
import com.zack.recomptracker.ui.train.ActiveSessionViewModel
import com.zack.recomptracker.ui.train.ExercisePickerScreen
import com.zack.recomptracker.ui.train.ExercisePickerViewModel
import com.zack.recomptracker.ui.train.RoutineBuilderScreen
import com.zack.recomptracker.ui.train.RoutineBuilderViewModel
import com.zack.recomptracker.ui.train.SessionDetailScreen
import com.zack.recomptracker.ui.train.SessionDetailViewModel
import com.zack.recomptracker.ui.train.SessionSummaryScreen
import com.zack.recomptracker.ui.train.SessionSummaryViewModel
import com.zack.recomptracker.ui.train.TrainHomeScreen
import com.zack.recomptracker.ui.train.TrainViewModel
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class TopLevelDestination(
    val route: String,
    val label: String,
) {
    Home("home", "Home"),
    Body("body", "Body"),
    Coach("coach", "Coach"),
    More("more", "More"),
}

object Routes {
    const val Food      = "food"
    const val Onboarding = "onboarding"
    const val CalorieDecision = "calorie_decision"
    const val Trends    = "trends"
    const val StreakStats = "streak_stats"
    const val Plan      = "plan"
    const val FoodLibrary = "food_library"
    const val Foods     = "foods"
    const val Profile      = "profile"
    const val Appearance   = "appearance"
    const val AiCoach      = "ai_coach"
    const val Integrations = "integrations"
    const val DataBackup   = "data_backup"
    const val Train          = "train"
    const val ActiveSession  = "active_session"
    const val ExercisePicker = "exercise_picker"
    // Single composable, optional replaceTargetId arg: omitted (-1) = add mode; >=0 = replace that
    // session_exercises row. Navigate to ExercisePicker for add, exercisePickerReplace(id) for replace.
    const val ExercisePickerRoute = "exercise_picker?replaceTargetId={replaceTargetId}"
    fun exercisePickerReplace(sessionExerciseId: Long) = "exercise_picker?replaceTargetId=$sessionExerciseId"
    const val RoutineBuilder = "routine_builder?workoutId={workoutId}"
    fun routineBuilder(workoutId: Long? = null) = "routine_builder?workoutId=${workoutId ?: -1L}"
    const val SessionSummary = "session_summary/{sessionId}"
    fun sessionSummary(id: Long) = "session_summary/$id"
    const val SessionDetail = "session_detail/{sessionId}"
    fun sessionDetail(id: Long) = "session_detail/$id"
    const val ExerciseStats = "exercise_stats/{exerciseId}"
    fun exerciseStats(exerciseId: Long) = "exercise_stats/$exerciseId"
    const val BodyHistory = "body_history"
    const val BodyEdit  = "body_edit/{date}"
    fun bodyEdit(date: LocalDate) = "body_edit/$date"

    const val BarcodeScanner = "barcode_scanner?slotId={slotId}&slotName={slotName}&pickerMode={pickerMode}&date={date}"
    fun barcodeScanner(slotId: Long?, slotName: String, pickerMode: Boolean = false, date: String = "") =
        "barcode_scanner?slotId=${slotId ?: -1L}&slotName=${java.net.URLEncoder.encode(slotName, "UTF-8")}&pickerMode=$pickerMode&date=$date"

    const val RecipeBuilder = "recipe_builder?recipeId={recipeId}&seedIngredients={seedIngredients}"
    fun recipeBuilder(recipeId: Long? = null, seedIngredients: String? = null): String {
        val seedPart = seedIngredients?.let { "&seedIngredients=$it" }.orEmpty()
        return "recipe_builder?recipeId=${recipeId ?: -1L}$seedPart"
    }

    fun foodLibraryPicker() = "${FoodLibrary}?pickerMode=true"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = TopLevelDestination.Home.route,
    modifier: Modifier = Modifier,
) {
    val factory = LocalAppContainer.current.viewModelFactory
    val tabEnter = fadeIn(tween(220))
    val tabExit  = fadeOut(tween(200))
    val screenEnter = slideInVertically(tween(280)) { it / 16 } + fadeIn(tween(280))
    val screenExit  = slideOutVertically(tween(220)) { it / 16 } + fadeOut(tween(220))
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(route = Routes.Onboarding) {
            OnboardingScreen(
                viewModel = viewModel<OnboardingViewModel>(factory = factory),
                onComplete = {
                    navController.navigate(TopLevelDestination.Home.route) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = TopLevelDestination.Home.route,
            enterTransition = { tabEnter },
            exitTransition  = { tabExit },
        ) {
            HomeDashboardScreen(
                viewModel = viewModel<DashboardViewModel>(factory = factory),
                weeklyReviewViewModel = viewModel<WeeklyReviewViewModel>(factory = factory),
                streakViewModel = viewModel<StreakViewModel>(factory = factory),
                onOpenCoach = {
                    navController.navigate(TopLevelDestination.Coach.route) {
                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(TopLevelDestination.More.route) {
                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenStreaks = { navController.navigate(Routes.StreakStats) },
                onOpenFoodLog = {
                    navController.navigate(Routes.Food) {
                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenBody = {
                    navController.navigate(TopLevelDestination.Body.route) {
                        popUpTo(TopLevelDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(
            route = Routes.Food,
            enterTransition = { tabEnter },
            exitTransition  = { tabExit },
        ) {
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
                onCreateRecipeFromSelection = { ingredients ->
                    val json = Json.encodeToString(ingredients)
                    val seed = java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(json.toByteArray(Charsets.UTF_8))
                    navController.navigate(Routes.recipeBuilder(seedIngredients = seed))
                },
            )
        }
        composable(
            route = TopLevelDestination.Body.route,
            enterTransition = { tabEnter },
            exitTransition  = { tabExit },
        ) {
            BodyRecoveryScreen(
                viewModel = viewModel<TodayViewModel>(factory = factory),
                onViewHistory = { navController.navigate(Routes.BodyHistory) },
            )
        }
        composable(
            route = TopLevelDestination.Coach.route,
            enterTransition = { tabEnter },
            exitTransition  = { tabExit },
        ) {
            com.zack.recomptracker.ui.coach.CoachScreen(
                viewModel = viewModel<com.zack.recomptracker.ui.coach.CoachViewModel>(factory = factory),
            )
        }
        composable(
            route = Routes.Train,
            enterTransition = { tabEnter },
            exitTransition  = { tabExit },
        ) {
            TrainHomeScreen(
                viewModel = viewModel<TrainViewModel>(factory = factory),
                onCreateRoutine = { navController.navigate(Routes.routineBuilder()) },
                onEditRoutine = { id -> navController.navigate(Routes.routineBuilder(id)) },
                onStart = { navController.navigate(Routes.ActiveSession) },
                onResume = { navController.navigate(Routes.ActiveSession) },
                onOpenSession = { id -> navController.navigate(Routes.sessionDetail(id)) },
                onOpenExerciseStats = { exerciseId -> navController.navigate(Routes.exerciseStats(exerciseId)) },
                modifier = Modifier,
            )
        }
        composable(
            route = Routes.ActiveSession,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) { backStackEntry ->
            val pickedIds by backStackEntry.savedStateHandle
                .getStateFlow<LongArray?>("picked_exercise_ids", null)
                .collectAsStateWithLifecycle()
            // [targetSessionExerciseId, chosenExerciseId] returned from the picker in replace mode.
            val replacementResult by backStackEntry.savedStateHandle
                .getStateFlow<LongArray?>("replacement_result", null)
                .collectAsStateWithLifecycle()

            ActiveSessionScreen(
                viewModel = viewModel<ActiveSessionViewModel>(factory = factory),
                pickedExerciseIds = pickedIds,
                onPickedConsumed = {
                    backStackEntry.savedStateHandle.remove<LongArray>("picked_exercise_ids")
                },
                replacementResult = replacementResult,
                onReplacementConsumed = {
                    backStackEntry.savedStateHandle.remove<LongArray>("replacement_result")
                },
                onReplaceExercise = { sessionExerciseId ->
                    navController.navigate(Routes.exercisePickerReplace(sessionExerciseId))
                },
                onAddExercise = { navController.navigate(Routes.ExercisePicker) },
                onMinimize = { navController.popBackStack() },
                onFinish = { sid ->
                    navController.navigate(Routes.sessionSummary(sid)) {
                        popUpTo(Routes.Train)
                    }
                },
                onDiscard = {
                    navController.navigate(Routes.Train) {
                        popUpTo(Routes.Train) { inclusive = true }
                    }
                },
                modifier = Modifier,
            )
        }
        composable(
            route = Routes.SessionSummary,
            arguments = listOf(
                androidx.navigation.navArgument("sessionId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
            ),
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            SessionSummaryScreen(
                viewModel = viewModel<SessionSummaryViewModel>(factory = factory),
                onDone = {
                    navController.navigate(Routes.Train) {
                        popUpTo(Routes.Train) { inclusive = true }
                    }
                },
                modifier = Modifier,
            )
        }
        composable(
            route = Routes.SessionDetail,
            arguments = listOf(
                androidx.navigation.navArgument("sessionId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
            ),
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            SessionDetailScreen(
                viewModel = viewModel<SessionDetailViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
                modifier = Modifier,
            )
        }
        composable(
            route = Routes.ExerciseStats,
            arguments = listOf(
                androidx.navigation.navArgument("exerciseId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
            ),
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            com.zack.recomptracker.ui.train.ExerciseStatsScreen(
                viewModel = viewModel<com.zack.recomptracker.ui.train.ExerciseStatsViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
                modifier = Modifier,
            )
        }
        composable(
            route = Routes.ExercisePickerRoute,
            arguments = listOf(
                androidx.navigation.navArgument("replaceTargetId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
            ),
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) { backStackEntry ->
            val replaceTargetId = backStackEntry.arguments?.getLong("replaceTargetId") ?: -1L
            ExercisePickerScreen(
                viewModel = viewModel<ExercisePickerViewModel>(factory = factory),
                onClose = { navController.popBackStack() },
                onConfirm = { ids ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("picked_exercise_ids", ids.toLongArray())
                    navController.popBackStack()
                },
                replaceMode = replaceTargetId >= 0,
                onReplacePick = { chosenExerciseId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("replacement_result", longArrayOf(replaceTargetId, chosenExerciseId))
                    navController.popBackStack()
                },
                modifier = Modifier,
            )
        }
        composable(
            route = Routes.RoutineBuilder,
            arguments = listOf(
                androidx.navigation.navArgument("workoutId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
            ),
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getLong("workoutId") ?: -1L
            val workoutId = if (rawId == -1L) null else rawId

            val vm = viewModel<RoutineBuilderViewModel>(factory = factory)

            // Load existing workout data on first composition (edit mode only)
            androidx.compose.runtime.LaunchedEffect(workoutId) {
                vm.loadWorkout(workoutId)
            }

            // Observe picked exercise IDs returned from the Exercise Picker
            val pickedIds by backStackEntry.savedStateHandle
                .getStateFlow<LongArray?>("picked_exercise_ids", null)
                .collectAsStateWithLifecycle()

            RoutineBuilderScreen(
                viewModel = vm,
                pickedExerciseIds = pickedIds,
                onPickedConsumed = {
                    backStackEntry.savedStateHandle.remove<LongArray>("picked_exercise_ids")
                },
                onClose = { navController.popBackStack() },
                onAddExercise = { navController.navigate(Routes.ExercisePicker) },
                onSaved = { navController.popBackStack() },
                modifier = Modifier,
            )
        }
        composable(
            route = Routes.BodyHistory,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
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
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            BodyEditScreen(
                viewModel = viewModel<BodyEditViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.CalorieDecision,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            DashboardScreen(viewModel<DashboardViewModel>(factory = factory), onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.Trends,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            ProgressScreen(viewModel<ProgressViewModel>(factory = factory), onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.Plan,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            PlanScreen(viewModel<PlanViewModel>(factory = factory), onBack = { navController.popBackStack() })
        }
        composable(
            route = TopLevelDestination.More.route,
            enterTransition = { tabEnter },
            exitTransition  = { tabExit },
        ) {
            MoreScreen(
                dashboardViewModel = viewModel<DashboardViewModel>(factory = factory),
                onCalorieDecision = { navController.navigate(Routes.CalorieDecision) },
                onTrends = { navController.navigate(Routes.Trends) },
                onProfile = { navController.navigate(Routes.Profile) },
                onPlan = { navController.navigate(Routes.Plan) },
                onAppearance = { navController.navigate(Routes.Appearance) },
                onAiCoach = { navController.navigate(Routes.AiCoach) },
                onIntegrations = { navController.navigate(Routes.Integrations) },
                onDataBackup = { navController.navigate(Routes.DataBackup) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.Foods,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            FoodsScreen(viewModel<FoodsViewModel>(factory = factory), onBack = { navController.popBackStack() })
        }
        composable(
            route = "${Routes.FoodLibrary}?slotId={slotId}&slotName={slotName}&editEntryId={editEntryId}&date={date}&pickerMode={pickerMode}",
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
                androidx.navigation.navArgument("pickerMode") {
                    type = androidx.navigation.NavType.BoolType
                    defaultValue = false
                },
            ),
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) { backStackEntry ->
            val slotId = backStackEntry.arguments?.getLong("slotId")?.takeIf { it != -1L }
            val slotName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("slotName").orEmpty(),
                "UTF-8"
            )
            val editEntryId = backStackEntry.arguments?.getLong("editEntryId")?.takeIf { it != -1L }
            val logDate = backStackEntry.arguments?.getString("date").orEmpty()
            val pickerMode = backStackEntry.arguments?.getBoolean("pickerMode") ?: false

            val scannedFoodJson by backStackEntry.savedStateHandle
                .getStateFlow<String?>("scanned_food", null)
                .collectAsStateWithLifecycle()

            FoodLibraryScreen(
                viewModel   = viewModel<FoodLibraryViewModel>(factory = factory),
                slotId      = slotId,
                slotName    = slotName,
                onBack      = { navController.popBackStack() },
                editEntryId = editEntryId,
                logDate     = logDate,
                onScanBarcode = {
                    navController.navigate(Routes.barcodeScanner(slotId, slotName, pickerMode = pickerMode, date = logDate))
                },
                pickerMode        = pickerMode,
                scannedFoodJson   = scannedFoodJson,
                onScannedFoodConsumed = {
                    backStackEntry.savedStateHandle.remove<String>("scanned_food")
                },
                onIngredientPicked = { ingredientJson ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("picked_ingredient", ingredientJson)
                },
                onCreateRecipe = {
                    navController.navigate(Routes.recipeBuilder())
                },
                onEditRecipe = { recipeId ->
                    navController.navigate(Routes.recipeBuilder(recipeId))
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
                androidx.navigation.navArgument("pickerMode") {
                    type = androidx.navigation.NavType.BoolType
                    defaultValue = false
                },
                androidx.navigation.navArgument("date") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                },
            ),
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) { backStackEntry ->
            val slotId = backStackEntry.arguments?.getLong("slotId")?.takeIf { it != -1L }
            val slotName = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("slotName").orEmpty(), "UTF-8"
            )
            val pickerMode = backStackEntry.arguments?.getBoolean("pickerMode") ?: false
            val logDate = backStackEntry.arguments?.getString("date").orEmpty()
            BarcodeScannerScreen(
                viewModel = viewModel<BarcodeScannerViewModel>(factory = factory),
                slotId = slotId,
                slotName = slotName,
                pickerMode = pickerMode,
                logDate = logDate,
                onFoodPicked = { foodJson ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("scanned_food", foodJson)
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.RecipeBuilder,
            arguments = listOf(
                androidx.navigation.navArgument("recipeId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                },
                androidx.navigation.navArgument("seedIngredients") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) { backStackEntry ->
            val pickedIngredientJson by backStackEntry.savedStateHandle
                .getStateFlow<String?>("picked_ingredient", null)
                .collectAsStateWithLifecycle()

            RecipeBuilderScreen(
                viewModel = viewModel<RecipeBuilderViewModel>(factory = factory),
                pickedIngredientJson = pickedIngredientJson,
                onPickedIngredientConsumed = {
                    backStackEntry.savedStateHandle.remove<String>("picked_ingredient")
                },
                onNavigateToFoodPicker = {
                    navController.navigate(Routes.foodLibraryPicker())
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.StreakStats,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            StreakStatsScreen(
                viewModel = viewModel<StreakViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.Profile,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            ProfileScreen(
                viewModel = viewModel<ProfileViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
                onOpenBody = { navController.navigate(TopLevelDestination.Body.route) },
            )
        }
        composable(
            route = Routes.Appearance,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            AppearanceScreen(
                viewModel<AppearanceViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.AiCoach,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            AiCoachScreen(
                viewModel = viewModel<AiCoachViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.Integrations,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            IntegrationsScreen(
                viewModel<SettingsViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.DataBackup,
            enterTransition = { screenEnter },
            exitTransition  = { screenExit },
        ) {
            DataBackupScreen(
                viewModel<SettingsViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
