package com.zack.recomptracker.ui.progress

import com.zack.recomptracker.domain.workout.MuscleCategory
import com.zack.recomptracker.domain.workout.MuscleTrainingAggregator
import com.zack.recomptracker.domain.workout.MuscleTrainingAggregator.RecoveryBand
import com.zack.recomptracker.domain.workout.MuscleTrainingAggregator.VolumeTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure per-muscle read mapping that [ProgressViewModel] surfaces into its
 * UiState. Exercises the filter (drop never-trained groups) and the |delta| ranking without
 * standing up the full ViewModel (which needs Android-backed prefs).
 */
class ProgressViewModelMuscleReadTest {

    private fun summary(
        category: MuscleCategory,
        weekly: Double,
        prior: Double,
        trend: VolumeTrend,
    ) = MuscleTrainingAggregator.MuscleSummary(
        category = category,
        weeklyVolume = weekly,
        priorWeeklyVolume = prior,
        volumeDelta = weekly - prior,
        volumeTrend = trend,
        recoveryScore = 1f,
        recoveryBand = RecoveryBand.FRESH,
    )

    @Test
    fun `drops muscles with zero volume in both weeks`() {
        val reads = ProgressViewModel.muscleVolumeReadsFrom(
            listOf(
                summary(MuscleCategory.BACK, weekly = 2000.0, prior = 1000.0, trend = VolumeTrend.UP),
                summary(MuscleCategory.CORE, weekly = 0.0, prior = 0.0, trend = VolumeTrend.FLAT),
            ),
        )

        assertEquals(1, reads.size)
        assertEquals("Back", reads.single().muscle)
    }

    @Test
    fun `keeps a muscle that was trained only in the prior week`() {
        val reads = ProgressViewModel.muscleVolumeReadsFrom(
            listOf(summary(MuscleCategory.LEGS, weekly = 0.0, prior = 1500.0, trend = VolumeTrend.DOWN)),
        )

        assertEquals(listOf("Legs"), reads.map { it.muscle })
        assertEquals(VolumeTrend.DOWN, reads.single().trend)
    }

    @Test
    fun `ranks the biggest movers first by absolute delta`() {
        val reads = ProgressViewModel.muscleVolumeReadsFrom(
            listOf(
                summary(MuscleCategory.CHEST, weekly = 1100.0, prior = 1000.0, trend = VolumeTrend.FLAT),
                summary(MuscleCategory.BACK, weekly = 3000.0, prior = 500.0, trend = VolumeTrend.UP),
                summary(MuscleCategory.LEGS, weekly = 200.0, prior = 1400.0, trend = VolumeTrend.DOWN),
            ),
        )

        assertEquals(listOf("Back", "Legs", "Chest"), reads.map { it.muscle })
        assertTrue(reads.first().volumeDelta > 0.0)
    }

    @Test
    fun `empty aggregate yields no reads`() {
        assertTrue(ProgressViewModel.muscleVolumeReadsFrom(emptyList()).isEmpty())
    }
}
