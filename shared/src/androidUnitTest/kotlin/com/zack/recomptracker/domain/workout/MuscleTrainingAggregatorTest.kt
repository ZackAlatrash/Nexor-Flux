package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

class MuscleTrainingAggregatorTest {

    private val today: LocalDate = LocalDate(2026, 7, 4)

    // --- Fixture helpers -----------------------------------------------------

    /** A library exercise mapped to a single muscle group via its first primary muscle. */
    private fun exercise(id: Long, name: String, primary: String) = Exercise(
        id = id, externalId = "ext-$id", name = name, category = null, force = null,
        level = null, mechanic = null, equipment = null,
        primaryMuscles = listOf(primary), secondaryMuscles = emptyList(),
        instructions = emptyList(), images = emptyList(), userCreated = false,
    )

    private fun set(reps: Int, weightKg: Double?, completed: Boolean = true) =
        SessionSet(id = 0, setNumber = 1, reps = reps, weightKg = weightKg, rir = null, completed = completed)

    /** A session on [daysAgo] before [today] with one exercise and the given sets. */
    private fun session(daysAgo: Int, exerciseId: Long, sets: List<SessionSet>): WorkoutSession {
        val date = today.minus(daysAgo, DateTimeUnit.DAY).toString()
        return WorkoutSession(
            id = daysAgo.toLong() * 100 + exerciseId,
            workoutId = null, workoutName = "W", date = date,
            startedAt = date, completedAt = date, status = SessionStatus.COMPLETED,
            note = null, durationSeconds = null,
            exercises = listOf(
                SessionExercise(
                    id = exerciseId, exerciseId = exerciseId, exerciseName = "ex$exerciseId",
                    sortOrder = 0, note = null, sets = sets,
                ),
            ),
        )
    }

    private fun summaryFor(list: List<MuscleTrainingAggregator.MuscleSummary>, c: MuscleCategory) =
        list.first { it.category == c }

    // --- Tests ---------------------------------------------------------------

    @Test fun singleMuscleWeeklyVolume() {
        // Bench (chest) 3 days ago: 10 reps × 100kg = 1000 volume, in the trailing week.
        val library = listOf(exercise(1, "Bench", "chest"))
        val sessions = listOf(session(daysAgo = 3, exerciseId = 1, sets = listOf(set(10, 100.0))))

        val result = MuscleTrainingAggregator.aggregate(sessions, library, today)
        val chest = summaryFor(result, MuscleCategory.CHEST)

        assertEquals(1000.0, chest.weeklyVolume, 0.0001)
        assertEquals(0.0, chest.priorWeeklyVolume, 0.0001)
        // Every category is always present.
        assertEquals(MuscleCategory.entries.size, result.size)
        // A different, untrained group reads zero and fresh.
        val legs = summaryFor(result, MuscleCategory.LEGS)
        assertEquals(0.0, legs.weeklyVolume, 0.0001)
        assertEquals(1f, legs.recoveryScore, 0.0001f)
    }

    @Test fun onlyCompletedSetsCount() {
        val library = listOf(exercise(1, "Bench", "chest"))
        val sessions = listOf(
            session(
                daysAgo = 1, exerciseId = 1,
                sets = listOf(set(10, 100.0, completed = true), set(10, 100.0, completed = false)),
            ),
        )
        val chest = summaryFor(MuscleTrainingAggregator.aggregate(sessions, library, today), MuscleCategory.CHEST)
        // Only the completed 10×100 counts.
        assertEquals(1000.0, chest.weeklyVolume, 0.0001)
    }

    @Test fun exerciseWithNoCategoryIsSkipped() {
        // "neck" does not map to any MuscleCategory → its volume is dropped, no crash.
        val library = listOf(exercise(9, "Neck curl", "neck"))
        val sessions = listOf(session(daysAgo = 1, exerciseId = 9, sets = listOf(set(10, 50.0))))

        val result = MuscleTrainingAggregator.aggregate(sessions, library, today)
        // No category picked up any volume.
        assertTrue(result.all { it.weeklyVolume == 0.0 })
        assertTrue(result.all { it.recoveryScore == 1f })
    }

    @Test fun exerciseMissingFromLibraryIsSkipped() {
        // Session references exerciseId 1 but library is empty → unmapped, skipped safely.
        val sessions = listOf(session(daysAgo = 0, exerciseId = 1, sets = listOf(set(10, 100.0))))
        val result = MuscleTrainingAggregator.aggregate(sessions, emptyList(), today)
        assertTrue(result.all { it.weeklyVolume == 0.0 })
    }

    @Test fun recencyDecayTodayVsThreeDaysVsFresh() {
        val library = listOf(
            exercise(1, "Bench", "chest"),      // trained TODAY
            exercise(2, "Row", "lats"),         // BACK, trained 3 days ago (outside recovery window)
            exercise(3, "Squat", "quadriceps"), // LEGS, never trained
        )
        // Identical heavy volume so only recency differs: 20 × 100 = 2000.
        val sessions = listOf(
            session(daysAgo = 0, exerciseId = 1, sets = listOf(set(20, 100.0))),
            session(daysAgo = 3, exerciseId = 2, sets = listOf(set(20, 100.0))),
        )
        val result = MuscleTrainingAggregator.aggregate(sessions, library, today)
        val chest = summaryFor(result, MuscleCategory.CHEST)   // today
        val back = summaryFor(result, MuscleCategory.BACK)     // 3 days ago
        val legs = summaryFor(result, MuscleCategory.LEGS)     // untrained

        // Trained today reads lower (more fatigued) than 3 days ago, which reads fully fresh,
        // same as the never-trained group.
        assertTrue(chest.recoveryScore < back.recoveryScore)
        assertEquals(1f, back.recoveryScore, 0.0001f)
        assertEquals(1f, legs.recoveryScore, 0.0001f)
        // Today's chest is in a recovering/moderate band, not fresh.
        assertTrue(chest.recoveryScore < 1f)
    }

    @Test fun recencyDecayWithinWindowTodayVsTwoDaysAgo() {
        val library = listOf(
            exercise(1, "Bench", "chest"),      // today
            exercise(2, "OHP", "shoulders"),    // 2 days ago
        )
        // Same volume; only daysAgo differs (0 vs 2), both inside the 3-day recovery window.
        val sessions = listOf(
            session(daysAgo = 0, exerciseId = 1, sets = listOf(set(20, 100.0))),
            session(daysAgo = 2, exerciseId = 2, sets = listOf(set(20, 100.0))),
        )
        val result = MuscleTrainingAggregator.aggregate(sessions, library, today)
        val chest = summaryFor(result, MuscleCategory.CHEST)
        val shoulders = summaryFor(result, MuscleCategory.SHOULDERS)
        // Trained today is more fatigued (lower score) than the same load two days ago.
        assertTrue(chest.recoveryScore < shoulders.recoveryScore)
    }

    @Test fun recoveryScoreExactFormulaAndBand() {
        // Today only, volume = 20 × 200 = 4000 = RECOVERY_SATURATION_VOLUME → load 1.0 → score 0f.
        val library = listOf(exercise(1, "Bench", "chest"))
        val sessions = listOf(session(daysAgo = 0, exerciseId = 1, sets = listOf(set(20, 200.0))))
        val chest = summaryFor(MuscleTrainingAggregator.aggregate(sessions, library, today), MuscleCategory.CHEST)
        assertEquals(0f, chest.recoveryScore, 0.0001f)
        assertEquals(MuscleTrainingAggregator.RecoveryBand.RECOVERING, chest.recoveryBand)
    }

    @Test fun emptyHistoryAllFresh() {
        val result = MuscleTrainingAggregator.aggregate(emptyList(), emptyList(), today)
        assertEquals(MuscleCategory.entries.size, result.size)
        assertTrue(result.all { it.weeklyVolume == 0.0 })
        assertTrue(result.all { it.priorWeeklyVolume == 0.0 })
        assertTrue(result.all { it.recoveryScore == 1f })
        assertTrue(result.all { it.recoveryBand == MuscleTrainingAggregator.RecoveryBand.FRESH })
        assertTrue(result.all { it.volumeTrend == MuscleTrainingAggregator.VolumeTrend.FLAT })
    }

    @Test fun weeklyVsPriorTrendDeltaAcrossBoundary() {
        // daysAgo 6 → trailing week; daysAgo 7 → prior week. Volumes chosen to force UP.
        val library = listOf(exercise(1, "Bench", "chest"))
        val sessions = listOf(
            session(daysAgo = 6, exerciseId = 1, sets = listOf(set(10, 300.0))),  // 3000, weekly
            session(daysAgo = 7, exerciseId = 1, sets = listOf(set(10, 100.0))),  // 1000, prior
        )
        val chest = summaryFor(MuscleTrainingAggregator.aggregate(sessions, library, today), MuscleCategory.CHEST)

        assertEquals(3000.0, chest.weeklyVolume, 0.0001)
        assertEquals(1000.0, chest.priorWeeklyVolume, 0.0001)
        assertEquals(2000.0, chest.volumeDelta, 0.0001)
        assertEquals(MuscleTrainingAggregator.VolumeTrend.UP, chest.volumeTrend)
    }

    @Test fun trendDownAndFlatBands() {
        val library = listOf(exercise(1, "Bench", "chest"))
        // Weekly 1000, prior 3000 → down.
        val down = listOf(
            session(daysAgo = 3, exerciseId = 1, sets = listOf(set(10, 100.0))),
            session(daysAgo = 10, exerciseId = 1, sets = listOf(set(10, 300.0))),
        )
        val downChest = summaryFor(MuscleTrainingAggregator.aggregate(down, library, today), MuscleCategory.CHEST)
        assertEquals(MuscleTrainingAggregator.VolumeTrend.DOWN, downChest.volumeTrend)

        // Weekly 1000, prior 1000 → flat (within 10% band).
        val flat = listOf(
            session(daysAgo = 3, exerciseId = 1, sets = listOf(set(10, 100.0))),
            session(daysAgo = 10, exerciseId = 1, sets = listOf(set(10, 100.0))),
        )
        val flatChest = summaryFor(MuscleTrainingAggregator.aggregate(flat, library, today), MuscleCategory.CHEST)
        assertEquals(MuscleTrainingAggregator.VolumeTrend.FLAT, flatChest.volumeTrend)
    }

    @Test fun volumeOlderThanTwoWeeksIsIgnored() {
        val library = listOf(exercise(1, "Bench", "chest"))
        // 14 days ago is outside both windows (0..13).
        val sessions = listOf(session(daysAgo = 14, exerciseId = 1, sets = listOf(set(10, 100.0))))
        val chest = summaryFor(MuscleTrainingAggregator.aggregate(sessions, library, today), MuscleCategory.CHEST)
        assertEquals(0.0, chest.weeklyVolume, 0.0001)
        assertEquals(0.0, chest.priorWeeklyVolume, 0.0001)
    }
}
