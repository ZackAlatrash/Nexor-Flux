package com.zack.recomptracker.ui.today

import com.zack.recomptracker.data.local.entity.DailyLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LatestCheckInTest {

    private val today = LocalDate.of(2026, 6, 16)

    @Test
    fun `includes today's entry as the latest check-in`() {
        // Regression: the Body/Recovery hero used to exclude today (d < today),
        // so logging today never updated the displayed weight/waist.
        val logs = listOf(
            DailyLogEntity(date = "2026-06-15", bodyWeightKg = 80.0),
            DailyLogEntity(date = "2026-06-16", bodyWeightKg = 80.5),
        )
        val latest = latestCheckIn(logs, today)
        assertEquals(LocalDate.of(2026, 6, 16), latest?.second)
        assertEquals(80.5, latest?.first?.bodyWeightKg)
    }

    @Test
    fun `falls back to the most recent prior day when today has no weight or waist`() {
        val logs = listOf(
            DailyLogEntity(date = "2026-06-15", bodyWeightKg = 80.0),
            DailyLogEntity(date = "2026-06-16", sleepHours = 7.0), // recovery-only today, no weight/waist
        )
        val latest = latestCheckIn(logs, today)
        assertEquals(LocalDate.of(2026, 6, 15), latest?.second)
        assertEquals(80.0, latest?.first?.bodyWeightKg)
    }

    @Test
    fun `ignores future-dated entries`() {
        val logs = listOf(
            DailyLogEntity(date = "2026-06-16", bodyWeightKg = 80.5),
            DailyLogEntity(date = "2026-06-20", bodyWeightKg = 79.0), // future
        )
        val latest = latestCheckIn(logs, today)
        assertEquals(LocalDate.of(2026, 6, 16), latest?.second)
    }

    @Test
    fun `picks waist when only waist is logged`() {
        val logs = listOf(DailyLogEntity(date = "2026-06-16", waistCm = 84.0))
        assertEquals(84.0, latestCheckIn(logs, today)?.first?.waistCm)
    }

    @Test
    fun `returns null when no entry has weight or waist`() {
        val logs = listOf(DailyLogEntity(date = "2026-06-16", sleepHours = 7.0))
        assertNull(latestCheckIn(logs, today))
    }
}
