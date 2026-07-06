package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.preferences.PlanPreferences
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanRepositoryHistoryTest {
    @Test fun `toTargets pulls the day-judging fields from preferences`() {
        val prefs = PlanPreferences(
            targetCalories = 2200, targetProteinG = 180, targetCarbsG = 200, targetFatG = 60,
            calorieZoneLowerBound = 2100, calorieZoneUpperBound = 2300,
        )
        val t = prefs.toPlanTargets()
        assertEquals(2200, t.calories)
        assertEquals(180, t.proteinG)
        assertEquals(2100, t.zoneLowerBound)
        assertEquals(2300, t.zoneUpperBound)
    }

    @Test fun `versionEntity stamps effectiveFrom and createdAt`() {
        val prefs = PlanPreferences(targetCalories = 2000)
        val entity = prefs.toPlanVersionEntity(
            effectiveFrom = LocalDate.parse("2026-06-30"),
            createdAt = "2026-06-30T10:00:00Z",
        )
        assertEquals("2026-06-30", entity.effectiveFrom)
        assertEquals(2000, entity.targetCalories)
        assertEquals("2026-06-30T10:00:00Z", entity.createdAt)
    }
}
