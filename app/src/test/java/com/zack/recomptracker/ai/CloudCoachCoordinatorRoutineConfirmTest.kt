package com.zack.recomptracker.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCoachCoordinatorRoutineConfirmTest {
    @Test
    fun `create_routine summary lists the routine name`() {
        val s = routineActionSummary("create_routine", mapOf("name" to "Push Day",
            "exercises" to """[{"name":"Bench Press","sets":4,"reps":8}]"""))
        assertTrue(s.contains("Push Day"))
        assertTrue(s.contains("Bench Press"))
    }

    @Test
    fun `edit_routine summary describes the changes`() {
        val s = routineActionSummary("edit_routine", mapOf("name" to "Push Day",
            "remove" to """["Dips"]""", "new_name" to "Upper A"))
        assertTrue(s.contains("Push Day"))
        assertTrue(s.contains("Dips") || s.contains("Upper A"))
    }

    @Test
    fun `create_exercise summary names the exercise`() {
        val s = routineActionSummary("create_exercise", mapOf("name" to "Cable Y-Raise",
            "primary_muscles" to """["Shoulders"]"""))
        assertTrue(s.contains("Cable Y-Raise"))
    }
}
