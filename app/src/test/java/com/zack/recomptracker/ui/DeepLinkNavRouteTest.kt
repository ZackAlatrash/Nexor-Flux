package com.zack.recomptracker.ui

import com.zack.recomptracker.domain.coach.CoachActionType
import com.zack.recomptracker.ui.navigation.Routes
import com.zack.recomptracker.ui.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure gate + mapping for notification deep-links (review P0-3). A pending action may navigate
 * only once onboarding is known-complete: while the flag is still loading (`null`) the NavHost is
 * not composed and `navigate()` would throw ("You must call setGraph() before…" — the cold-start
 * push-tap crash), and while onboarding is incomplete a deep-link must never route over the flow.
 */
class DeepLinkNavRouteTest {

    @Test
    fun `no navigation while the onboarding flag is still loading`() {
        assertNull(deepLinkNavRoute(CoachActionType.LOG_WEIGHT, onboardingComplete = null))
    }

    @Test
    fun `no navigation while onboarding is incomplete`() {
        assertNull(deepLinkNavRoute(CoachActionType.LOG_WEIGHT, onboardingComplete = false))
    }

    @Test
    fun `weight and steps actions open the Body tab once onboarded`() {
        assertEquals(TopLevelDestination.Body.route, deepLinkNavRoute(CoachActionType.LOG_WEIGHT, true))
        assertEquals(TopLevelDestination.Body.route, deepLinkNavRoute(CoachActionType.LOG_STEPS, true))
    }

    @Test
    fun `meal actions open the Food tab once onboarded`() {
        assertEquals(Routes.Food, deepLinkNavRoute(CoachActionType.CONFIRM_PLANNED_MEALS, true))
        assertEquals(Routes.Food, deepLinkNavRoute(CoachActionType.OPEN_FOOD_LOG, true))
    }

    @Test
    fun `training action opens the Train tab once onboarded`() {
        assertEquals(Routes.Train, deepLinkNavRoute(CoachActionType.OPEN_TRAINING, true))
    }

    @Test
    fun `weekly review and apply target land on Home once onboarded`() {
        assertEquals(TopLevelDestination.Home.route, deepLinkNavRoute(CoachActionType.OPEN_WEEKLY_REVIEW, true))
        assertEquals(TopLevelDestination.Home.route, deepLinkNavRoute(CoachActionType.APPLY_TARGET, true))
    }

    @Test
    fun `non-navigation actions never navigate`() {
        assertNull(deepLinkNavRoute(CoachActionType.TRACK_EXPERIMENT, true))
        assertNull(deepLinkNavRoute(CoachActionType.NONE, true))
        assertNull(deepLinkNavRoute(null, true))
    }
}
