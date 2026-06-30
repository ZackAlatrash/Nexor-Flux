package com.zack.recomptracker.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityInsightContextTest {

    @Test
    fun `sufficient data needs a positive goal and today's steps`() {
        assertTrue(ActivityInsightContext(steps = 4000, stepGoal = 10000, averageDailySteps7 = 8000).hasSufficientData)
        assertFalse(ActivityInsightContext(steps = null, stepGoal = 10000, averageDailySteps7 = 8000).hasSufficientData)
        assertFalse(ActivityInsightContext(steps = 4000, stepGoal = null, averageDailySteps7 = 8000).hasSufficientData)
        assertFalse(ActivityInsightContext(steps = 4000, stepGoal = 0, averageDailySteps7 = 8000).hasSufficientData)
    }

    @Test
    fun `key buckets steps to 500 so minor updates do not regenerate`() {
        val a = ActivityInsightContext(steps = 4000, stepGoal = 10000, averageDailySteps7 = 8000)
        val b = ActivityInsightContext(steps = 4200, stepGoal = 10000, averageDailySteps7 = 8000)
        assertEquals(a.key(), b.key())
    }

    @Test
    fun `key changes when crossing a 500-step bucket`() {
        val a = ActivityInsightContext(steps = 4000, stepGoal = 10000, averageDailySteps7 = 8000)
        val c = ActivityInsightContext(steps = 4600, stepGoal = 10000, averageDailySteps7 = 8000)
        assertTrue(a.key() != c.key())
    }
}
