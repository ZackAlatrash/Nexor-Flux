package com.zack.recomptracker.ui.review

import com.zack.recomptracker.ai.ActionBlock
import com.zack.recomptracker.domain.review.BriefingPhase
import com.zack.recomptracker.ai.WeeklyBriefing
import com.zack.recomptracker.domain.review.WeeklyReviewData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun briefing() = WeeklyBriefing(
        "2026-06-08", BriefingPhase.FULL, "H", "N", emptyList(),
        ActionBlock("Reduce calories", "r", 2450), "w",
    )

    private fun fixture(
        cloudActive: Boolean = true,
        daysLogged: Int = 14,
    ): WeeklyReviewDeps {
        val data = WeeklyReviewData(
            weekStart = "2026-06-08", phase = BriefingPhase.FULL, daysLogged = daysLogged,
            input = com.zack.recomptracker.domain.adjustment.AdjustmentInput(
                daysLogged, 90.0, 4, 0.0, -0.2,
                com.zack.recomptracker.domain.adjustment.PerformanceTrend.UP,
                com.zack.recomptracker.domain.adjustment.RecoveryTrend.GOOD,
            ),
            result = com.zack.recomptracker.domain.adjustment.AdjustmentResult(
                com.zack.recomptracker.domain.adjustment.AdjustmentVerdict.REDUCE_CALORIES, -100,
                listOf("X"), "s",
            ),
            verdictLabel = "Reduce calories", signals = emptyList(),
            currentTargetCalories = 2550, applyTargetCalories = 2450,
        )
        return WeeklyReviewDeps(
            cloudActive = MutableStateFlow(cloudActive),
            reviewData = MutableStateFlow(data),
            lastSeen = MutableStateFlow(""),
            generate = { briefing() },
        )
    }

    @Test
    fun `open shows upsell when cloud inactive`() = runTest {
        val vm = WeeklyReviewViewModel(fixture(cloudActive = false).toVm())
        vm.open(); advanceUntilIdle()
        assertTrue(vm.uiState.value is WeeklyReviewUiState.Upsell)
    }

    @Test
    fun `open shows insufficient data under 7 days`() = runTest {
        val vm = WeeklyReviewViewModel(fixture(daysLogged = 4).toVm())
        vm.open(); advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s is WeeklyReviewUiState.InsufficientData)
        assertEquals(3, (s as WeeklyReviewUiState.InsufficientData).daysRemaining)
    }

    @Test
    fun `open generates and becomes ready`() = runTest {
        val vm = WeeklyReviewViewModel(fixture().toVm())
        vm.open(); advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s is WeeklyReviewUiState.Ready)
        assertEquals("H", (s as WeeklyReviewUiState.Ready).briefing.headline)
    }

    @Test
    fun `apply with confirm saves the target`() = runTest {
        val deps = fixture()
        var savedTarget: Int? = null
        val vm = WeeklyReviewViewModel(deps.toVm(saveTarget = { target ->
            savedTarget = target
            // Mirror production: the saved target propagates back through weeklyReviewDataFlow.
            deps.reviewData.value = deps.reviewData.value!!.copy(currentTargetCalories = target)
        }))
        vm.open(); advanceUntilIdle()
        vm.requestApply(2450)
        assertEquals(2450, vm.pendingApply.value)
        vm.confirmApply(); advanceUntilIdle()
        assertEquals(2450, savedTarget)
        assertEquals(null, vm.pendingApply.value)
    }

    @Test
    fun `badge stays false after apply-confirm with changed target`() = runTest {
        val deps = fixture()
        val vm = WeeklyReviewViewModel(deps.toVm(saveTarget = { target ->
            // Mirror production: the saved target propagates back through weeklyReviewDataFlow,
            // producing a new signature.
            deps.reviewData.value = deps.reviewData.value!!.copy(currentTargetCalories = target)
        }))
        val badgeJob = launch { vm.badge.collect {} }

        // Opening the review marks the current signature seen, clearing the badge.
        vm.open(); advanceUntilIdle()
        assertFalse("badge should clear after opening the review", vm.badge.value)

        // Applying the recommendation changes the target (and the signature) — the badge must
        // not re-appear solely because the user acted on it.
        vm.requestApply(2450)
        vm.confirmApply(); advanceUntilIdle()
        assertFalse("badge should stay clear after applying the recommendation", vm.badge.value)

        badgeJob.cancel()
    }
}

private class WeeklyReviewDeps(
    val cloudActive: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    val reviewData: kotlinx.coroutines.flow.MutableStateFlow<WeeklyReviewData?>,
    val lastSeen: kotlinx.coroutines.flow.MutableStateFlow<String>,
    val generate: suspend (WeeklyReviewData) -> WeeklyBriefing,
) {
    fun toVm(saveTarget: suspend (Int) -> Unit = {}) = WeeklyReviewConfig(
        cloudActiveFlow = cloudActive,
        reviewDataFlow = reviewData,
        // Signature tracks the calorie target the way the real computer does, so applying a
        // recommendation produces a genuinely different signature.
        signatureOf = { "sig-${it.currentTargetCalories}" },
        briefingFor = { _, _, gen -> gen() },
        generate = generate,
        saveCalorieTarget = saveTarget,
        markSeen = { lastSeen.value = it },
        lastSeenSignatureFlow = lastSeen,
        startCoachHandoff = { _, _ -> },
    )
}
