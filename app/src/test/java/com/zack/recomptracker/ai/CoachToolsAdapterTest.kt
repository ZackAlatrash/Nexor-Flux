package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.coach.CoachJourney
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.domain.coach.CoachSignal
import com.zack.recomptracker.domain.rebalance.RebalanceMode
import com.zack.recomptracker.domain.rebalance.RebalancePlan
import com.zack.recomptracker.domain.rebalance.RebalanceState
import com.zack.recomptracker.domain.rebalance.RebalanceStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CoachToolsAdapterTest {

    private val fixedDate: LocalDate = LocalDate.of(2026, 7, 1)
    private val dateProvider = object : DateProvider {
        override fun today(): LocalDate = fixedDate
    }

    private class FakeJourney(private val narrative: String) : CoachJourney {
        override suspend fun recordFiredSignal(signal: CoachSignal, weekSignature: String) = Unit
        override suspend fun recordWeeklyVerdict(weekSignature: String, weekEndDateIso: String, verdict: String) = Unit
        override suspend fun journeyNarrative(): String = narrative
    }

    private suspend fun promptWith(narrative: String): String {
        val executor = mock<CoachToolExecutor>()
        whenever(executor.execute(any(), any())).thenReturn("{\"meals\":[]}")
        val planRepo = mock<PlanRepository>()
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences()))
        val profileStore = mock<UserProfilePreferencesStore>()
        whenever(profileStore.preferences).thenReturn(flowOf(UserProfilePreferences()))
        val adapter = CoachToolsAdapter(
            toolExecutor = executor,
            planRepository = planRepo,
            userProfileStore = profileStore,
            dateProvider = dateProvider,
            handoffStore = CoachHandoffStore(),
            journey = FakeJourney(narrative),
        )
        return adapter.systemPromptSnapshot()
    }

    private suspend fun promptWithRebalance(state: RebalanceState): String {
        val executor = mock<CoachToolExecutor>()
        whenever(executor.execute(any(), any())).thenReturn("{\"meals\":[]}")
        val planRepo = mock<PlanRepository>()
        whenever(planRepo.preferences).thenReturn(flowOf(PlanPreferences())) // 2550 kcal, P165
        val profileStore = mock<UserProfilePreferencesStore>()
        whenever(profileStore.preferences).thenReturn(flowOf(UserProfilePreferences()))
        val adapter = CoachToolsAdapter(
            toolExecutor = executor,
            planRepository = planRepo,
            userProfileStore = profileStore,
            dateProvider = dateProvider,
            handoffStore = CoachHandoffStore(),
            rebalanceState = { state },
        )
        return adapter.systemPromptSnapshot()
    }

    @Test
    fun `plan line shows effective kcal and a rebalance suffix on a plan day`() = runTest {
        // 3-day rebalance covering today (fixedDate 2026-07-01), day 2 of 3, −250 kcal from 2550.
        val plan = RebalancePlan(
            id = "p",
            triggerDateIso = fixedDate.minusDays(2).toString(),
            startDateIso = fixedDate.minusDays(1).toString(),
            endDateIso = fixedDate.plusDays(1).toString(),
            lengthDays = 3,
            mode = RebalanceMode.EAT_LESS,
            baseCalories = 2550,
            dailyCalorieReduction = 250,
            extraDailySteps = 0,
            baseStepGoal = null,
            recentAvgSteps = null,
            surplusKcal = 600,
            recoveredKcal = 600,
            status = RebalanceStatus.ACTIVE,
            createdAtIso = "2026-06-29T09:00:00Z",
        )
        val prompt = promptWithRebalance(RebalanceState(active = plan))
        // Effective calories (2550 − 250) on the SAME plan line, plus the day-X suffix.
        assertTrue("shows effective kcal", prompt.contains("Plan: 2300 kcal"))
        assertTrue("shows rebalance suffix", prompt.contains("| Rebalance: day 2 of 3"))
        assertFalse("does not show the base target", prompt.contains("Plan: 2550 kcal"))
    }

    @Test
    fun `plan line shows base kcal and no suffix when no rebalance is active`() = runTest {
        val prompt = promptWithRebalance(RebalanceState())
        assertTrue("shows base kcal", prompt.contains("Plan: 2550 kcal"))
        assertFalse("no rebalance suffix", prompt.contains("Rebalance: day"))
    }

    @Test
    fun `includes the journey section when the store returns non-blank`() = runTest {
        val prompt = promptWith("3 weeks ago your bench stalled; it's moving again.")
        assertTrue("journey header present", prompt.contains("=== YOUR JOURNEY SO FAR ==="))
        assertTrue("journey narrative present", prompt.contains("3 weeks ago your bench stalled"))
    }

    @Test
    fun `omits the journey section when the store narrative is blank`() = runTest {
        val prompt = promptWith("")
        assertFalse("no journey header when blank", prompt.contains("YOUR JOURNEY SO FAR"))
    }
}
