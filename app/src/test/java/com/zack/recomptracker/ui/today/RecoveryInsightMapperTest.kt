package com.zack.recomptracker.ui.today

import com.zack.recomptracker.data.local.entity.DailyLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryInsightMapperTest {

    @Test
    fun `null log yields null context`() {
        assertNull(buildRecoveryInsightContext(null))
    }

    @Test
    fun `maps logged recovery fields`() {
        val log = DailyLogEntity(
            date = "2026-06-10",
            sleepHours = 6.5,
            energyScore = 4,
            hungerScore = 5,
            sorenessScore = 8,
            trained = true,
        )
        val ctx = buildRecoveryInsightContext(log)!!
        assertEquals(6.5, ctx.sleepHours!!, 0.001)
        assertEquals(8, ctx.sorenessScore)
        assertTrue(ctx.trained)
        assertTrue(ctx.hasSufficientData)
    }

    @Test
    fun `log with only body metrics is insufficient`() {
        val log = DailyLogEntity(date = "2026-06-10", bodyWeightKg = 80.0, waistCm = 85.0)
        val ctx = buildRecoveryInsightContext(log)!!
        assertNull(ctx.sleepHours)
        assertNull(ctx.energyScore)
        assertFalse(ctx.hasSufficientData)
    }
}
