package com.zack.recomptracker.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.zack.recomptracker.core.model.MealType
import com.zack.recomptracker.ui.today.TodayActions
import com.zack.recomptracker.ui.today.TodayContent
import com.zack.recomptracker.ui.today.TodayUiState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class TodayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun todayScreenShowsEmptyMealStateAndCoreForms() {
        composeRule.setContent {
            TodayContent(
                state = TodayUiState(date = LocalDate.of(2026, 5, 29)),
                actions = noOpActions(),
            )
        }

        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Macro progress").assertIsDisplayed()
        composeRule.onNodeWithText("Body and recovery").assertIsDisplayed()
        composeRule.onNodeWithTag("todayList").performScrollToNode(hasText("No meals logged today."))
        composeRule.onNodeWithText("No meals logged today.").assertIsDisplayed()
    }

    private fun noOpActions() = TodayActions(
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
        onMealNameChanged = {},
        onMealTypeChanged = { _: MealType -> },
        onCaloriesChanged = {},
        onProteinChanged = {},
        onCarbsChanged = {},
        onFatChanged = {},
        onAddMeal = {},
        onDeleteMeal = {},
        onCopyYesterday = {},
        onAddSavedMeal = {},
    )
}
