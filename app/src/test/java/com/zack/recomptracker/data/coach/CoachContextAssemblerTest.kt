package com.zack.recomptracker.data.coach

import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.preferences.ActivityLevel
import com.zack.recomptracker.data.preferences.BiologicalSex
import com.zack.recomptracker.data.preferences.FitnessGoal
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.preferences.UserProfilePreferences
import com.zack.recomptracker.domain.plan.PlanTargets
import com.zack.recomptracker.domain.streak.StreakResult
import com.zack.recomptracker.domain.streak.Streaks
import com.zack.recomptracker.domain.workout.SessionExercise
import com.zack.recomptracker.domain.workout.SessionSet
import com.zack.recomptracker.domain.workout.SessionStatus
import com.zack.recomptracker.domain.workout.WorkoutSession
import java.time.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachContextAssemblerTest {

    private val today = LocalDate.of(2026, 7, 1)

    // ── Fixture builders ─────────────────────────────────────────────────────────

    private fun log(
        date: LocalDate,
        weight: Double? = null,
        waist: Double? = null,
        skinfold: Double? = null,
        steps: Int? = null,
        sleep: Double? = null,
        energy: Int? = null,
        hunger: Int? = null,
        soreness: Int? = null,
        trained: Boolean = false,
    ) = DailyLogEntity(
        date = date.toString(),
        bodyWeightKg = weight,
        waistCm = waist,
        waistSkinfoldMm = skinfold,
        steps = steps,
        sleepHours = sleep,
        energyScore = energy,
        hungerScore = hunger,
        sorenessScore = soreness,
        trained = trained,
    )

    private fun meal(
        date: LocalDate,
        calories: Int,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fat: Double = 0.0,
        planned: Boolean = false,
    ) = MealEntryEntity(
        date = date.toString(),
        mealType = "meal",
        name = "food",
        calories = calories,
        proteinG = protein,
        carbsG = carbs,
        fatG = fat,
        planned = planned,
    )

    private fun session(
        date: LocalDate,
        exerciseName: String,
        sets: List<SessionSet>,
    ) = WorkoutSession(
        id = 0,
        workoutId = null,
        workoutName = "w",
        date = date.toString(),
        startedAt = "",
        completedAt = "",
        status = SessionStatus.COMPLETED,
        note = null,
        durationSeconds = null,
        exercises = listOf(
            SessionExercise(
                id = 0,
                exerciseId = 1,
                exerciseName = exerciseName,
                sortOrder = 0,
                note = null,
                sets = sets,
            ),
        ),
    )

    private fun set(reps: Int, weightKg: Double?, rir: Int? = null, completed: Boolean = true) =
        SessionSet(id = 0, setNumber = 1, reps = reps, weightKg = weightKg, rir = rir, completed = completed)

    private val defaultTargets = PlanTargets(
        calories = 2000, proteinG = 150, carbsG = 200, fatG = 60,
        zoneLowerBound = 1900, zoneUpperBound = 2100,
    )

    private fun inputs(
        plan: PlanPreferences = PlanPreferences(),
        profile: UserProfilePreferences = UserProfilePreferences(),
        dailyLogs: List<DailyLogEntity> = emptyList(),
        eatenMeals: List<MealEntryEntity> = emptyList(),
        plannedMeals: List<MealEntryEntity> = emptyList(),
        sessions: List<WorkoutSession> = emptyList(),
        streaks: Streaks = Streaks.EMPTY,
        reviews: List<WeeklyReviewInput> = emptyList(),
        targetsByDate: Map<LocalDate, PlanTargets>? = null,
    ) = CoachContextInputs(
        today = today,
        plan = plan,
        profile = profile,
        dailyLogs = dailyLogs,
        eatenMeals = eatenMeals,
        plannedMeals = plannedMeals,
        completedSessions = sessions,
        streaks = streaks,
        weeklyReviews = reviews,
        targetsByDate = targetsByDate
            ?: (0 until 60).associate { today.minusDays(it.toLong()) to defaultTargets },
    )

    // ── asOf / plan / profile ─────────────────────────────────────────────────────

    @Test
    fun `asOf mirrors today`() {
        assertEquals(today.toKotlinLocalDate(), CoachContextAssembler.assemble(inputs()).asOf)
    }

    @Test
    fun `plan fields and calorie zone map straight through`() {
        val plan = PlanPreferences(
            targetCalories = 2550, targetProteinG = 165, targetCarbsG = 320, targetFatG = 68,
            calorieZoneLowerBound = 2400, calorieZoneUpperBound = 2600,
        )
        val ctx = CoachContextAssembler.assemble(inputs(plan = plan)).plan
        assertEquals(2550, ctx.targetCalories)
        assertEquals(165, ctx.targetProteinG)
        assertEquals(320, ctx.targetCarbsG)
        assertEquals(68, ctx.targetFatG)
        assertEquals(2400, ctx.zoneLowerBound)
        assertEquals(2600, ctx.zoneUpperBound)
    }

    @Test
    fun `weeksSincePhaseStart is whole weeks between phase start and today`() {
        val plan = PlanPreferences(maintenancePhaseStartDate = today.minusDays(20).toString())
        val ctx = CoachContextAssembler.assemble(inputs(plan = plan)).plan
        assertEquals(today.minusDays(20).toKotlinLocalDate(), ctx.maintenancePhaseStartDate)
        assertEquals(2, ctx.weeksSincePhaseStart) // 20 days -> 2 whole weeks
    }

    @Test
    fun `no phase start yields null weeks`() {
        val ctx = CoachContextAssembler.assemble(inputs()).plan
        assertNull(ctx.maintenancePhaseStartDate)
        assertNull(ctx.weeksSincePhaseStart)
    }

    @Test
    fun `profile derives age from birthDate and passes enums as names`() {
        val profile = UserProfilePreferences(
            goal = FitnessGoal.RECOMP,
            biologicalSex = BiologicalSex.MALE,
            birthDate = LocalDate.of(2000, 7, 1).toString(),
            heightCm = 180,
            activityLevel = ActivityLevel.MODERATELY_ACTIVE,
            weeklyGymSessions = 4,
            dailyStepGoal = 9000,
        )
        val ctx = CoachContextAssembler.assemble(inputs(profile = profile)).profile
        assertEquals("RECOMP", ctx.goal)
        assertEquals("MALE", ctx.sex)
        assertEquals(26, ctx.ageYears) // born 2000-07-01, today 2026-07-01
        assertEquals(180, ctx.heightCm)
        assertEquals("MODERATELY_ACTIVE", ctx.activityLevel)
        assertEquals(4, ctx.weeklyGymSessions)
        assertEquals(9000, ctx.dailyStepGoal)
    }

    // ── Nutrition ─────────────────────────────────────────────────────────────────

    @Test
    fun `today eaten and planned totals separate, planned excluded from eaten`() {
        val eaten = listOf(
            meal(today, 500, protein = 40.0),
            meal(today, 300, protein = 20.0),
        )
        val planned = listOf(meal(today, 700, protein = 50.0, planned = true))
        val ctx = CoachContextAssembler.assemble(
            inputs(eatenMeals = eaten, plannedMeals = planned),
        ).nutrition
        assertEquals(800, ctx.todayEaten.calories)
        assertEquals(60.0, ctx.todayEaten.proteinG, 0.001)
        assertEquals(700, ctx.todayPlanned.calories)
        assertEquals(2, ctx.todayMealsLogged)
    }

    @Test
    fun `eatenByDate groups per day and counts logged days`() {
        val eaten = listOf(
            meal(today, 1000),
            meal(today.minusDays(1), 1500),
            meal(today.minusDays(1), 200),
            meal(today.minusDays(2), 1800),
        )
        val ctx = CoachContextAssembler.assemble(inputs(eatenMeals = eaten)).nutrition
        assertEquals(3, ctx.loggedDaysInWindow)
        assertEquals(1000, ctx.eatenByDate[today.toKotlinLocalDate()]!!.calories)
        assertEquals(1700, ctx.eatenByDate[today.minusDays(1).toKotlinLocalDate()]!!.calories)
    }

    @Test
    fun `entries outside the 28-day window are excluded from nutrition`() {
        val eaten = listOf(
            meal(today, 1000),
            meal(today.minusDays(40), 2000), // outside window
        )
        val ctx = CoachContextAssembler.assemble(inputs(eatenMeals = eaten)).nutrition
        assertEquals(1, ctx.loggedDaysInWindow)
        assertNull(ctx.eatenByDate[today.minusDays(40).toKotlinLocalDate()])
    }

    @Test
    fun `adherence graded per own-day target, consistency over window`() {
        // Two logged days, both exactly on the 2000 target -> 100% adherence.
        val eaten = listOf(meal(today, 2000), meal(today.minusDays(1), 2000))
        val ctx = CoachContextAssembler.assemble(inputs(eatenMeals = eaten)).nutrition
        assertEquals(100.0, ctx.adherencePercent!!, 0.001)
        // 2 logged / 28 expected.
        assertEquals(2.0 / 28.0 * 100.0, ctx.loggingConsistencyPercent!!, 0.001)
    }

    @Test
    fun `adherence null when nothing logged`() {
        val ctx = CoachContextAssembler.assemble(inputs()).nutrition
        assertNull(ctx.adherencePercent)
        assertEquals(0, ctx.loggedDaysInWindow)
    }

    @Test
    fun `unconfirmed planned counts today-or-past plans, not future`() {
        val planned = listOf(
            meal(today, 400, planned = true),
            meal(today.minusDays(2), 400, planned = true),
            meal(today.plusDays(1), 400, planned = true), // future -> not unconfirmed
        )
        val ctx = CoachContextAssembler.assemble(inputs(plannedMeals = planned)).nutrition
        assertEquals(2, ctx.unconfirmedPlannedCount)
    }

    // ── Body / trends / steps ─────────────────────────────────────────────────────

    @Test
    fun `metric series built oldest to newest, nulls skipped`() {
        val logs = listOf(
            log(today, weight = 80.0),
            log(today.minusDays(2), weight = 81.0),
            log(today.minusDays(1)), // no weight
        )
        val ctx = CoachContextAssembler.assemble(inputs(dailyLogs = logs)).body
        assertEquals(2, ctx.weightSeries.size)
        assertEquals(today.minusDays(2).toKotlinLocalDate(), ctx.weightSeries.first().date)
        assertEquals(today.toKotlinLocalDate(), ctx.weightSeries.last().date)
    }

    @Test
    fun `weight trend is negative when weight falls over the window`() {
        // Oldest day (7 days ago) = 87kg, newest (today) = 80kg: -1kg/day -> -7.0 kg/week.
        val logs = (0..7).map { log(today.minusDays(it.toLong()), weight = 80.0 + it) }
        val ctx = CoachContextAssembler.assemble(inputs(dailyLogs = logs)).body
        assertTrue("expected negative trend, got ${ctx.weightTrendKgPerWeek}", ctx.weightTrendKgPerWeek!! < 0.0)
        assertEquals(-7.0, ctx.weightTrendKgPerWeek!!, 0.01)
    }

    @Test
    fun `trend null with fewer than two points`() {
        val ctx = CoachContextAssembler.assemble(
            inputs(dailyLogs = listOf(log(today, weight = 80.0))),
        ).body
        assertNull(ctx.weightTrendKgPerWeek)
    }

    @Test
    fun `energy hunger soreness scores become double series`() {
        val logs = listOf(
            log(today, energy = 7, hunger = 3, soreness = 5),
            log(today.minusDays(1), energy = 6, hunger = 4, soreness = 6),
        )
        val ctx = CoachContextAssembler.assemble(inputs(dailyLogs = logs)).body
        assertEquals(2, ctx.energySeries.size)
        assertEquals(7.0, ctx.energySeries.last().value, 0.001)
        assertEquals(2, ctx.hungerSeries.size)
        assertEquals(2, ctx.sorenessSeries.size)
    }

    @Test
    fun `steps averages current vs previous 7 day windows`() {
        // Current 7d (today .. today-6): 10000 each. Previous 7d (today-7 .. today-13): 5000 each.
        val logs = (0..6).map { log(today.minusDays(it.toLong()), steps = 10000) } +
            (7..13).map { log(today.minusDays(it.toLong()), steps = 5000) }
        val ctx = CoachContextAssembler.assemble(inputs(dailyLogs = logs)).body
        assertEquals(10000, ctx.avgSteps7)
        assertEquals(5000, ctx.avgStepsPrev7)
        assertEquals(10000, ctx.stepsByDate[today.toKotlinLocalDate()])
    }

    @Test
    fun `trained dates collected from trained logs`() {
        val logs = listOf(
            log(today, trained = true),
            log(today.minusDays(1), trained = false),
            log(today.minusDays(2), trained = true),
        )
        val ctx = CoachContextAssembler.assemble(inputs(dailyLogs = logs)).body
        assertEquals(setOf(today.toKotlinLocalDate(), today.minusDays(2).toKotlinLocalDate()), ctx.trainedDates)
    }

    @Test
    fun `daysSinceLastWeighIn measured from most recent weight`() {
        val logs = listOf(
            log(today.minusDays(3), weight = 80.0),
            log(today.minusDays(10), weight = 81.0),
        )
        val ctx = CoachContextAssembler.assemble(inputs(dailyLogs = logs)).body
        assertEquals(3, ctx.daysSinceLastWeighIn)
    }

    @Test
    fun `daysSinceLastWeighIn null when no weigh-ins`() {
        val ctx = CoachContextAssembler.assemble(inputs(dailyLogs = listOf(log(today, steps = 100)))).body
        assertNull(ctx.daysSinceLastWeighIn)
    }

    // ── Training ──────────────────────────────────────────────────────────────────

    @Test
    fun `e1rm series best per day per exercise, oldest to newest`() {
        val sessions = listOf(
            // Older: bench, best set 100x5 -> e1RM 100*(1+5/30)=116.67
            session(today.minusDays(7), "Bench", listOf(set(5, 100.0), set(8, 80.0))),
            // Newer: bench, 100x8 -> 100*(1+8/30)=126.67
            session(today, "Bench", listOf(set(8, 100.0))),
        )
        val ctx = CoachContextAssembler.assemble(inputs(sessions = sessions)).training
        val bench = ctx.e1rmByExercise["Bench"]!!
        assertEquals(2, bench.size)
        assertEquals(today.minusDays(7).toKotlinLocalDate(), bench.first().date)
        assertEquals(116.667, bench.first().estimatedOneRepMax, 0.01)
        assertEquals(today.toKotlinLocalDate(), bench.last().date)
        assertEquals(126.667, bench.last().estimatedOneRepMax, 0.01)
    }

    @Test
    fun `e1rm ignores incomplete and bodyweight sets`() {
        val sessions = listOf(
            session(today, "Pullup", listOf(set(10, null), set(5, 40.0, completed = false))),
        )
        val ctx = CoachContextAssembler.assemble(inputs(sessions = sessions)).training
        // Only completed weighted sets qualify; none here -> exercise absent.
        assertNull(ctx.e1rmByExercise["Pullup"])
    }

    @Test
    fun `completed session dates within window, sorted`() {
        val sessions = listOf(
            session(today, "Bench", listOf(set(5, 100.0))),
            session(today.minusDays(3), "Squat", listOf(set(5, 140.0))),
            session(today.minusDays(40), "Old", listOf(set(5, 60.0))), // out of window
        )
        val ctx = CoachContextAssembler.assemble(inputs(sessions = sessions)).training
        assertEquals(listOf(today.minusDays(3).toKotlinLocalDate(), today.toKotlinLocalDate()), ctx.completedSessionDates)
    }

    @Test
    fun `recentRir gathers RIR from most recent sessions`() {
        val sessions = listOf(
            session(today, "Bench", listOf(set(5, 100.0, rir = 1), set(5, 100.0, rir = 2))),
            session(today.minusDays(2), "Squat", listOf(set(5, 140.0, rir = 3))),
        )
        val ctx = CoachContextAssembler.assemble(inputs(sessions = sessions)).training
        assertEquals(listOf(1, 2, 3), ctx.recentRir.sorted())
    }

    @Test
    fun `weekly training frequency and target populated`() {
        // 4 sessions in the trailing 4 weeks -> 1.0 / week.
        val sessions = (0 until 4).map { session(today.minusDays(it * 7L), "Bench", listOf(set(5, 100.0))) }
        val profile = UserProfilePreferences(weeklyGymSessions = 5)
        val ctx = CoachContextAssembler.assemble(inputs(sessions = sessions, profile = profile)).training
        assertEquals(1.0, ctx.weeklyTrainingFrequency, 0.001)
        assertEquals(5, ctx.weeklyGymSessionsTarget)
    }

    // ── Streaks / history ─────────────────────────────────────────────────────────

    @Test
    fun `streak snapshots carry current and longest`() {
        val streaks = Streaks(
            workout = StreakResult(current = 3, longest = 10),
            calorie = StreakResult(current = 5, longest = 5),
            steps = StreakResult(current = 0, longest = 7),
        )
        val ctx = CoachContextAssembler.assemble(inputs(streaks = streaks)).streaks
        assertEquals(3, ctx.workout.current)
        assertEquals(10, ctx.workout.longest)
        assertEquals(5, ctx.calorie.current)
        assertEquals(0, ctx.steps.current)
        assertEquals(7, ctx.steps.longest)
    }

    @Test
    fun `weekly reviews sorted oldest first with verdict and signature`() {
        val reviews = listOf(
            WeeklyReviewInput(today.minusDays(7), "Gaining", "sigB"),
            WeeklyReviewInput(today.minusDays(14), "Holding", "sigA"),
        )
        val ctx = CoachContextAssembler.assemble(inputs(reviews = reviews)).history
        assertEquals(2, ctx.weeklyReviews.size)
        assertEquals(today.minusDays(14).toKotlinLocalDate(), ctx.weeklyReviews.first().weekStart)
        assertEquals("Holding", ctx.weeklyReviews.first().verdict)
        assertEquals("sigB", ctx.weeklyReviews.last().signature)
    }
}
