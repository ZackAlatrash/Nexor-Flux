package com.zack.recomptracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutValidationTest {

    @Test
    fun `valid template draft passes`() {
        val result = WorkoutValidation.validateTemplate(name = "Push Day", exerciseCount = 3, plannedSets = listOf(3, 4, 3))
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `blank name fails`() {
        val result = WorkoutValidation.validateTemplate(name = "  ", exerciseCount = 1, plannedSets = listOf(3))
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reasons.any { it.contains("name", ignoreCase = true) })
    }

    @Test
    fun `no exercises fails`() {
        val result = WorkoutValidation.validateTemplate(name = "Empty", exerciseCount = 0, plannedSets = emptyList())
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reasons.any { it.contains("exercise", ignoreCase = true) })
    }

    @Test
    fun `planned sets below one fails`() {
        val result = WorkoutValidation.validateTemplate(name = "Bad", exerciseCount = 2, plannedSets = listOf(3, 0))
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).reasons.any { it.contains("set", ignoreCase = true) })
    }

    @Test
    fun `valid set passes`() {
        assertEquals(ValidationResult.Valid, WorkoutValidation.validateSet(reps = 10, weightKg = 60.0, rir = 2))
    }

    @Test
    fun `bodyweight set with null weight passes`() {
        assertEquals(ValidationResult.Valid, WorkoutValidation.validateSet(reps = 12, weightKg = null, rir = null))
    }

    @Test
    fun `negative reps fails`() {
        val result = WorkoutValidation.validateSet(reps = -1, weightKg = 60.0, rir = null)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `negative weight fails`() {
        val result = WorkoutValidation.validateSet(reps = 5, weightKg = -10.0, rir = null)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun `rir out of range fails`() {
        val result = WorkoutValidation.validateSet(reps = 5, weightKg = 60.0, rir = 11)
        assertTrue(result is ValidationResult.Invalid)
    }
}
