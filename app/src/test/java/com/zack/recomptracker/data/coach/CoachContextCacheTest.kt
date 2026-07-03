package com.zack.recomptracker.data.coach

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.domain.coach.BodyContext
import com.zack.recomptracker.domain.coach.CoachContext
import com.zack.recomptracker.domain.coach.HistoryContext
import com.zack.recomptracker.domain.coach.NutritionContext
import com.zack.recomptracker.domain.coach.PlanContext
import com.zack.recomptracker.domain.coach.ProfileContext
import com.zack.recomptracker.domain.coach.StreakSnapshot
import com.zack.recomptracker.domain.coach.StreaksContext
import com.zack.recomptracker.domain.coach.TrainingContext
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachContextCacheTest {

    private class MutableDateProvider(var day: LocalDate) : DateProvider {
        override fun today(): LocalDate = day
    }

    private fun context(day: LocalDate) = CoachContext(
        asOf = day,
        plan = PlanContext(0, 0, 0, 0, 0, 0),
        profile = ProfileContext(),
        nutrition = NutritionContext(
            todayEaten = MacroTotals(),
            todayPlanned = MacroTotals(),
            todayMealsLogged = 0,
            eatenByDate = emptyMap(),
            loggedDaysInWindow = 0,
        ),
        body = BodyContext(
            weightSeries = emptyList(), waistSeries = emptyList(), skinfoldSeries = emptyList(),
            sleepSeries = emptyList(), energySeries = emptyList(), hungerSeries = emptyList(),
            sorenessSeries = emptyList(), stepsByDate = emptyMap(), trainedDates = emptySet(),
        ),
        training = TrainingContext(completedSessionDates = emptyList(), weeklyTrainingFrequency = 0.0),
        streaks = StreaksContext(StreakSnapshot(0, 0), StreakSnapshot(0, 0), StreakSnapshot(0, 0)),
        history = HistoryContext(),
    )

    @Test
    fun `builds once then serves cache on same day`() = runTest {
        val dp = MutableDateProvider(LocalDate.of(2026, 7, 1))
        val builds = AtomicInteger(0)
        val cache = CoachContextCache(dp) { builds.incrementAndGet(); context(dp.day) }

        val first = cache.get()
        val second = cache.get()

        assertEquals(1, builds.get())
        assertEquals(first, second)
    }

    @Test
    fun `rebuilds when the day changes`() = runTest {
        val dp = MutableDateProvider(LocalDate.of(2026, 7, 1))
        val builds = AtomicInteger(0)
        val cache = CoachContextCache(dp) { builds.incrementAndGet(); context(dp.day) }

        cache.get()
        dp.day = LocalDate.of(2026, 7, 2)
        val second = cache.get()

        assertEquals(2, builds.get())
        assertEquals(LocalDate.of(2026, 7, 2), second.asOf)
    }

    @Test
    fun `refresh forces a rebuild on the same day`() = runTest {
        val dp = MutableDateProvider(LocalDate.of(2026, 7, 1))
        val builds = AtomicInteger(0)
        val cache = CoachContextCache(dp) { builds.incrementAndGet(); context(dp.day) }

        cache.get()
        cache.refresh()

        assertEquals(2, builds.get())
    }

    @Test
    fun `invalidate forces the next get to rebuild`() = runTest {
        val dp = MutableDateProvider(LocalDate.of(2026, 7, 1))
        val builds = AtomicInteger(0)
        val cache = CoachContextCache(dp) { builds.incrementAndGet(); context(dp.day) }

        cache.get()
        cache.invalidate()
        cache.get()

        assertEquals(2, builds.get())
    }
}
