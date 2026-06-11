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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
            signature = "sig-1",
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
        val vm = WeeklyReviewViewModel(deps.toVm(saveTarget = { savedTarget = it }))
        vm.open(); advanceUntilIdle()
        vm.requestApply(2450)
        assertEquals(2450, vm.pendingApply.value)
        vm.confirmApply(); advanceUntilIdle()
        assertEquals(2450, savedTarget)
        assertEquals(null, vm.pendingApply.value)
    }
}

private class WeeklyReviewDeps(
    val cloudActive: kotlinx.coroutines.flow.MutableStateFlow<Boolean>,
    val reviewData: kotlinx.coroutines.flow.MutableStateFlow<WeeklyReviewData?>,
    val signature: String,
    val generate: suspend (WeeklyReviewData) -> WeeklyBriefing,
) {
    fun toVm(saveTarget: suspend (Int) -> Unit = {}) = WeeklyReviewConfig(
        cloudActiveFlow = cloudActive,
        reviewDataFlow = reviewData,
        signatureOf = { signature },
        briefingFor = { _, _, gen -> gen() },
        generate = generate,
        saveCalorieTarget = saveTarget,
        markSeen = {},
        lastSeenSignatureFlow = kotlinx.coroutines.flow.MutableStateFlow(""),
        startCoachHandoff = { _, _ -> },
    )
}
