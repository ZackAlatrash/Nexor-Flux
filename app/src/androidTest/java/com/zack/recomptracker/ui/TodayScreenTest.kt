package com.zack.recomptracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.zack.recomptracker.ui.dashboard.DashboardUiState
import com.zack.recomptracker.ui.dashboard.HomeDashboardContent
import com.zack.recomptracker.ui.more.MoreScreen
import com.zack.recomptracker.ui.theme.RecompTrackerTheme
import com.zack.recomptracker.ui.today.BodyRecoveryActions
import com.zack.recomptracker.ui.today.BodyRecoveryContent
import com.zack.recomptracker.ui.today.FoodActions
import com.zack.recomptracker.ui.today.FoodContent
import com.zack.recomptracker.ui.today.TodayUiState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class TodayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun foodScreenShowsNutritionTargetMealsAndLibraryAction() {
        composeRule.setContent {
            RecompTrackerTheme {
                FoodContent(
                    state = TodayUiState(date = LocalDate.of(2026, 5, 30)),
                    actions = noOpFoodActions(),
                    onAddToSlot = { _, _ -> },
                    onBrowseLibrary = {},
                )
            }
        }

        composeRule.onNodeWithText("Food").assertIsDisplayed()
        composeRule.onNodeWithText("Nutrition target").assertIsDisplayed()
        composeRule.onNodeWithText("MEALS").assertIsDisplayed()
        composeRule.onNodeWithText("Browse saved foods & meals").assertIsDisplayed()
    }

    @Test
    fun bodyScreenShowsRecoveryInputsAndSaveAction() {
        composeRule.setContent {
            RecompTrackerTheme {
                BodyRecoveryContent(
                    state = TodayUiState(date = LocalDate.of(2026, 5, 30)),
                    actions = noOpBodyActions(),
                )
            }
        }

        composeRule.onNodeWithText("Body & recovery").assertIsDisplayed()
        composeRule.onNodeWithText("Weight").assertIsDisplayed()
        composeRule.onNodeWithText("Sleep").assertIsDisplayed()
        composeRule.onNodeWithText("Save daily check-in").assertIsDisplayed()
    }

    @Test
    fun homeScreenShowsDashboardSnapshotAndQuickActions() {
        composeRule.setContent {
            RecompTrackerTheme {
                HomeDashboardContent(
                    state = DashboardUiState(),
                    onLogFood = {},
                    onUpdateBody = {},
                )
            }
        }

        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
        composeRule.onNodeWithText("Weekly direction").assertIsDisplayed()
        composeRule.onNodeWithText("Log food").assertIsDisplayed()
        composeRule.onNodeWithText("Update body check-in").assertIsDisplayed()
    }

    @Test
    fun moreScreenShowsSecondaryDestinations() {
        composeRule.setContent {
            RecompTrackerTheme {
                MoreScreen(
                    onStatsClick = {},
                    onChartsClick = {},
                    onPlanClick = {},
                    onSettingsClick = {},
                )
            }
        }

        composeRule.onNodeWithText("More").assertIsDisplayed()
        composeRule.onNodeWithText("Stats").assertIsDisplayed()
        composeRule.onNodeWithText("Charts").assertIsDisplayed()
        composeRule.onNodeWithText("Plan").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    private fun noOpFoodActions() = FoodActions(
        onToggleEditMode = {},
        onAddSlot = {},
        onRenameSlot = { _, _ -> },
        onDeleteSlot = {},
        onReorderSlots = {},
        onDeleteMeal = {},
    )

    private fun noOpBodyActions() = BodyRecoveryActions(
        onBodyWeightChanged = {},
        onWaistChanged = {},
        onStepsChanged = {},
        onSleepChanged = {},
        onEnergyChanged = {},
        onHungerChanged = {},
        onSorenessChanged = {},
        onTrainedChanged = {},
        onNotesChanged = {},
        onSaveMetrics = {},
    )
}
