package com.zack.recomptracker.ai

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.coach.CoachJourney
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferencesStore
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.domain.coach.CoachSignal
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
