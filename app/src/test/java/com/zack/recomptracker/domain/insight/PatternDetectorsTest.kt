package com.zack.recomptracker.domain.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PatternDetectorsTest {

    private val targets = NutritionTargets(
        calories = 2200, proteinG = 165, carbsG = 320, fatG = 68,
        calorieZoneLower = 2100, calorieZoneUpper = 2300,
    )

    /** 14 consecutive days ending [end]; [calsFor] maps each date to its calories. proteins/etc default to target. */
    private fun days(
        end: LocalDate = LocalDate.of(2026, 6, 14),
        logged: (LocalDate) -> Boolean = { true },
        calsFor: (LocalDate) -> Int,
        proteinFor: (LocalDate) -> Double = { targets.proteinG.toDouble() },
    ): List<DayNutrition> = (0..13).map { offset ->
        val d = end.minusDays((13 - offset).toLong())
        DayNutrition(d, calsFor(d), proteinFor(d), targets.carbsG.toDouble(), targets.fatG.toDouble(), logged(d))
    }

    private fun isWeekend(d: LocalDate) =
        d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    @Test
    fun `weekday-weekend fires when weekend calories are much higher`() {
        val fact = detectWeekdayWeekend(
            days(calsFor = { if (isWeekend(it)) 2800 else 2200 }),
            targets,
        )
        assertEquals(InsightFactType.WEEKDAY_WEEKEND, fact?.type)
        assertTrue(fact!!.statement.contains("2800"))
        assertTrue(fact.statement.contains("2200"))
        assertTrue(fact.statement.contains("+600"))
    }

    @Test
    fun `weekday-weekend does not fire when gap is small`() {
        val fact = detectWeekdayWeekend(
            days(calsFor = { if (isWeekend(it)) 2250 else 2200 }),
            targets,
        )
        assertNull(fact)
    }

    @Test
    fun `weekday-weekend needs enough weekend days`() {
        // Only weekdays logged → fewer than 2 weekend samples.
        val fact = detectWeekdayWeekend(
            days(logged = { !isWeekend(it) }, calsFor = { 2800 }),
            targets,
        )
        assertNull(fact)
    }

    @Test
    fun `weekday-weekend fires at a 200-plus gap (real-world calibration)`() {
        // Weekends ~2080 vs weekdays ~2300 → ~220 gap, just over the 200 threshold.
        // Mirrors real user data where a ~215 gap was previously dropped at the old 250 threshold.
        val fact = detectWeekdayWeekend(
            days(calsFor = { if (isWeekend(it)) 2080 else 2300 }),
            targets,
        )
        assertEquals(InsightFactType.WEEKDAY_WEEKEND, fact?.type)
    }

    @Test
    fun `derailment fires when one day drives most of the surplus`() {
        // 7 logged days: six at target (no surplus), one at +1500.
        val end = LocalDate.of(2026, 6, 14)
        val spike = end // most recent day
        val list = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val cals = if (d == spike) 3700 else 2200
            DayNutrition(d, cals, 165.0, 320.0, 68.0, logged = offset >= 7) // only last 7 logged
        }
        val fact = detectDerailmentDay(list, targets)
        assertEquals(InsightFactType.DERAILMENT_DAY, fact?.type)
        assertTrue(fact!!.statement.contains("%"))
        assertTrue(fact.statement.contains("surplus"))
    }

    @Test
    fun `derailment does not fire when surplus is spread evenly`() {
        // Seven logged days each slightly over target → no single dominant day.
        val list = days(calsFor = { 2350 }).mapIndexed { i, d -> d.copy(logged = i >= 7) }
        val fact = detectDerailmentDay(list, targets)
        assertNull(fact)
    }

    @Test
    fun `derailment does not fire below minimum weekly surplus`() {
        val list = days(calsFor = { 2200 }).mapIndexed { i, d -> d.copy(logged = i >= 7) }
        assertNull(detectDerailmentDay(list, targets))
    }

    @Test
    fun `weakest macro flags protein with on-point wording when calories are in zone`() {
        // Calories ~2200 (in 2100-2300 zone); protein ~60% of target, carbs & fat on target.
        val fact = detectWeakestMacro(
            days(calsFor = { 2200 }, proteinFor = { 100.0 }),
            targets,
        )
        assertEquals(InsightFactType.WEAKEST_MACRO, fact?.type)
        assertTrue(fact!!.statement.contains("protein"))
        assertTrue(fact.statement.contains("%"))
        assertTrue("in-zone calories should use the 'on point' wording", fact.statement.contains("on point"))
    }

    @Test
    fun `weakest macro fires out of zone with neutral wording`() {
        // Calories 2800 (above zone) but protein only ~61% — still the weakest lever worth surfacing.
        // Previously the zone guard suppressed this; now it fires without claiming "calories on point".
        val fact = detectWeakestMacro(
            days(calsFor = { 2800 }, proteinFor = { 100.0 }),
            targets,
        )
        assertEquals(InsightFactType.WEAKEST_MACRO, fact?.type)
        assertTrue(fact!!.statement.contains("protein", ignoreCase = true))
        assertTrue(fact.statement.contains("%"))
        assertFalse("must not falsely claim calories are on point", fact.statement.contains("on point"))
    }

    @Test
    fun `weakest macro does not fire when all macros near target`() {
        val fact = detectWeakestMacro(
            days(calsFor = { 2200 }, proteinFor = { 165.0 }),
            targets,
        )
        assertNull(fact)
    }

    @Test
    fun `streak fires for consecutive protein-target days`() {
        val end = LocalDate.of(2026, 6, 14)
        // Last 5 days at/above target, earlier day below.
        val list = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val protein = if (offset >= 9) 180.0 else 120.0
            DayNutrition(d, 2200, protein, 320.0, 68.0, logged = true)
        }
        val fact = detectStreak(list, targets)
        assertEquals(InsightFactType.STREAK, fact?.type)
        assertTrue(fact!!.statement.contains("5 days"))
    }

    @Test
    fun `streak ignores a not-yet-logged most-recent day`() {
        val end = LocalDate.of(2026, 6, 14)
        // Today (most recent) unlogged; previous 4 at target.
        val list = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val logged = offset != 13
            val protein = if (offset in 9..12) 180.0 else 120.0
            DayNutrition(d, if (logged) 2200 else 0, if (logged) protein else 0.0, 320.0, 68.0, logged)
        }
        val fact = detectStreak(list, targets)
        assertEquals(InsightFactType.STREAK, fact?.type)
        assertTrue(fact!!.statement.contains("4 days"))
    }

    @Test
    fun `streak does not fire below minimum`() {
        val end = LocalDate.of(2026, 6, 14)
        val list = (0..13).map { offset ->
            val d = end.minusDays((13 - offset).toLong())
            val protein = if (offset >= 12) 180.0 else 120.0 // only last 2 hit
            DayNutrition(d, 2200, protein, 320.0, 68.0, logged = true)
        }
        assertNull(detectStreak(list, targets))
    }
}
