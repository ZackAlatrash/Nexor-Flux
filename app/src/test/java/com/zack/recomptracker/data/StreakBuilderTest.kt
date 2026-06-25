package com.zack.recomptracker.data

import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.buildStreaks
import com.zack.recomptracker.domain.streak.StreakCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakBuilderTest {
    private val calc = StreakCalculator()
    private val prefs = PlanPreferences() // zone defaults 2400..2600
    private val mon = LocalDate.of(2026, 6, 22)
    private val tue = mon.plusDays(1)

    private fun log(date: LocalDate, steps: Int? = null, trained: Boolean = false) =
        DailyLogEntity(date = date.toString(), steps = steps, trained = trained)

    @Test fun calorieUsesZoneRule() {
        val s = buildStreaks(
            dailyLogs = emptyList(),
            eatenCaloriesByDate = mapOf(mon to 2500, tue to 3000), // mon in zone, tue over
            completedSessionDates = emptyList(),
            prefs = prefs,
            dailyStepGoal = null,
            today = tue,
            calculator = calc,
        )
        // Mon in zone; today Tue not qualified but Mon is yesterday -> grace alive, current 1
        assertEquals(1, s.calorie.current)
    }

    @Test fun stepsCountAtOrAboveGoal() {
        val s = buildStreaks(
            dailyLogs = listOf(log(mon, steps = 12000), log(tue, steps = 8000)),
            eatenCaloriesByDate = emptyMap(),
            completedSessionDates = emptyList(),
            prefs = prefs,
            dailyStepGoal = 10000,
            today = tue,
            calculator = calc,
        )
        // Mon 12000 >= goal qualifies; Tue 8000 does not; Mon is yesterday -> grace, current 1
        assertEquals(1, s.steps.current)
    }

    @Test fun stepsZeroWhenNoGoal() {
        val s = buildStreaks(
            dailyLogs = listOf(log(mon, steps = 12000)),
            eatenCaloriesByDate = emptyMap(),
            completedSessionDates = emptyList(),
            prefs = prefs,
            dailyStepGoal = null,
            today = mon,
            calculator = calc,
        )
        assertEquals(0, s.steps.current)
        assertEquals(0, s.steps.longest)
    }

    @Test fun workoutUnionsSessionsAndTrainedFlag() {
        val s = buildStreaks(
            dailyLogs = listOf(log(tue, trained = true)), // Tue via trained flag
            eatenCaloriesByDate = emptyMap(),
            completedSessionDates = listOf(mon),          // Mon via completed session
            prefs = prefs,
            dailyStepGoal = null,
            today = tue,
            calculator = calc,
        )
        // Mon + Tue workouts -> spans 2 calendar days
        assertEquals(2, s.workout.current)
    }

    @Test fun last7HasSevenFlags() {
        val s = buildStreaks(
            dailyLogs = emptyList(),
            eatenCaloriesByDate = mapOf(mon to 2500),
            completedSessionDates = emptyList(),
            prefs = prefs,
            dailyStepGoal = null,
            today = mon,
            calculator = calc,
        )
        assertEquals(7, s.calorie.last7.size)
        assertEquals(true, s.calorie.last7.last()) // today (mon) is in zone
    }
}
