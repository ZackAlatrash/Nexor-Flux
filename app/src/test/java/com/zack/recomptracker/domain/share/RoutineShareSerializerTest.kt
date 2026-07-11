package com.zack.recomptracker.domain.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineShareSerializerTest {

    private val sample = RoutineSharePayload(
        name = "Push Day",
        note = "Chest focus",
        exercises = listOf(
            SharedExercise(
                source = "free-exercise-db",
                externalId = "Barbell_Bench_Press",
                name = "Barbell Bench Press",
                note = "Slow eccentric",
                sets = listOf(
                    SharedSet(setNumber = 1, targetReps = 8, targetWeightKg = 60.0),
                    SharedSet(setNumber = 2, targetReps = 8, targetWeightKg = null),
                ),
            ),
            SharedExercise(
                source = "user",
                externalId = "user_my_cable_thing",
                name = "My Cable Thing",
                note = null,
                sets = listOf(SharedSet(setNumber = 1, targetReps = null, targetWeightKg = null)),
            ),
        ),
    )

    @Test
    fun `round-trips structure, reps, weights, notes and order`() {
        val decoded = RoutineShareSerializer.decode(RoutineShareSerializer.encode(sample))
        assertTrue(decoded is RoutineShareResult.Success)
        assertEquals(sample, (decoded as RoutineShareResult.Success).payload)
    }

    @Test
    fun `rejects a foreign app marker`() {
        val foreign = """{"version":1,"app":"someOtherApp","name":"X","exercises":[{"source":"s","externalId":"e","name":"E","sets":[]}]}"""
        assertEquals(RoutineShareResult.NotARoutineFile, RoutineShareSerializer.decode(foreign))
    }

    @Test
    fun `rejects a future version`() {
        val future = RoutineShareSerializer.encode(sample.copy(version = CURRENT_SHARE_VERSION + 1))
        assertEquals(RoutineShareResult.UnsupportedVersion, RoutineShareSerializer.decode(future))
    }

    @Test
    fun `rejects corrupt json`() {
        assertEquals(RoutineShareResult.Damaged, RoutineShareSerializer.decode("{not json"))
    }

    @Test
    fun `rejects an empty routine`() {
        val empty = RoutineShareSerializer.encode(sample.copy(exercises = emptyList()))
        assertEquals(RoutineShareResult.Damaged, RoutineShareSerializer.decode(empty))
    }

    @Test
    fun `tolerates unknown fields for forward compat`() {
        val withExtra = """{"version":1,"app":"recomptracker","name":"X","futureField":true,"exercises":[{"source":"s","externalId":"e","name":"E","sets":[]}]}"""
        assertTrue(RoutineShareSerializer.decode(withExtra) is RoutineShareResult.Success)
    }
}
