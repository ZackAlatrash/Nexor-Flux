package com.zack.recomptracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.ui.body.BodyCheckInFormActions
import com.zack.recomptracker.ui.dashboard.DashboardUiState
import com.zack.recomptracker.ui.dashboard.HomeDashboardContent
import com.zack.recomptracker.ui.theme.RecompTrackerTheme
import com.zack.recomptracker.ui.today.FoodActions
import com.zack.recomptracker.ui.today.FoodContent
import com.zack.recomptracker.ui.today.FoodLogUiState
import com.zack.recomptracker.ui.today.BodyRecoveryContent
import com.zack.recomptracker.ui.today.TodayUiState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class TodayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun foodScreenShowsMealsHeaderAndFoodLogTitle() {
        val today = LocalDate.of(2026, 5, 30)
        composeRule.setContent {
            RecompTrackerTheme {
                FoodContent(
                    state = FoodLogUiState(
                        selectedDate = today,
                        today = today,
                        target = PlanPreferences(),
                    ),
                    actions = noOpFoodActions(),
                    onAddToSlot = { _, _ -> },
                    onBrowseLibrary = {},
                    onEditEntryAmount = { _, _, _ -> },
                    onSelectDate = {},
                )
            }
        }

        composeRule.onNodeWithText("Food Log").assertIsDisplayed()
        composeRule.onNodeWithText("MEALS").assertIsDisplayed()
    }

    @Test
    fun bodyScreenShowsBodyTitleWeightAndSleepTiles() {
        composeRule.setContent {
            RecompTrackerTheme {
                BodyRecoveryContent(
                    state = TodayUiState(date = LocalDate.of(2026, 5, 30)),
                    actions = noOpBodyActions(),
                    onViewHistory = {},
                )
            }
        }

        composeRule.onNodeWithText("Body").assertIsDisplayed()
        composeRule.onNodeWithText("Weight").assertIsDisplayed()
        composeRule.onNodeWithText("Sleep").assertIsDisplayed()
    }

    @Test
    fun homeScreenShowsDashboardTitle() {
        composeRule.setContent {
            RecompTrackerTheme {
                HomeDashboardContent(
                    state = DashboardUiState(),
                )
            }
        }

        composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
    }

    private fun noOpFoodActions() = FoodActions(
        onToggleEditMode = {},
        onAddSlot = {},
        onRenameSlot = { _, _ -> },
        onDeleteSlot = {},
        onReorderSlots = {},
        onDeleteMeal = {},
        onEditMacros = { _, _, _, _, _ -> },
    )

    private fun noOpBodyActions() = BodyCheckInFormActions(
        onBodyWeightChanged = {},
        onWaistChanged = {},
        onWaistSkinfoldChanged = {},
        onStepsChanged = {},
        onSleepChanged = {},
        onEnergyChanged = {},
        onHungerChanged = {},
        onSorenessChanged = {},
        onTrainedChanged = {},
        onNotesChanged = {},
        onSave = {},
    )
}
