package com.zack.recomptracker.domain

import com.zack.recomptracker.domain.streak.StreakCalculator
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {
    private val calc = StreakCalculator()

    // 2026-06-22 is a Monday.
    private val mon = LocalDate(2026, 6, 22)
    private val tue = mon.plus(1, DateTimeUnit.DAY)
    private val wed = mon.plus(2, DateTimeUnit.DAY)
    private val thu = mon.plus(3, DateTimeUnit.DAY)
    private val fri = mon.plus(4, DateTimeUnit.DAY)

    @Test fun emptyHistoryIsZero() {
        val r = calc.compute(emptySet(), today = thu, restDays = 2)
        assertEquals(0, r.current)
        assertEquals(0, r.longest)
    }

    @Test fun workoutContinuesAcrossTwoRestDays() {
        // Mon workout, Tue/Wed rest, Thu workout -> spans 4 calendar days
        val r = calc.compute(setOf(mon, thu), today = thu, restDays = 2)
        assertEquals(4, r.current)
        assertEquals(4, r.longest)
    }

    @Test fun workoutAliveWhileResting() {
        // Mon workout only, today Thu (3 days later, within tolerance): alive, span to last workout = 1
        val r = calc.compute(setOf(mon), today = thu, restDays = 2)
        assertEquals(1, r.current)
        assertEquals(1, r.longest)
    }

    @Test fun workoutBreaksAfterThreeRestDays() {
        // Mon workout only, today Fri (4 days later): broken
        val r = calc.compute(setOf(mon), today = fri, restDays = 2)
        assertEquals(0, r.current)
        assertEquals(1, r.longest)
    }

    @Test fun workoutGapTooLargeStartsNewStreak() {
        // Mon then Fri (gap 4 > 3) -> separate chains; Friday is a fresh streak of 1
        val r = calc.compute(setOf(mon, fri), today = fri, restDays = 2)
        assertEquals(1, r.current)
        assertEquals(1, r.longest)
    }

    @Test fun calorieConsecutiveDaysCount() {
        val r = calc.compute(setOf(mon, tue, wed), today = wed, restDays = 0)
        assertEquals(3, r.current)
        assertEquals(3, r.longest)
    }

    @Test fun calorieGraceForUnloggedToday() {
        // In zone Mon/Tue/Wed, today Thu not yet logged -> still alive (grace), current 3
        val r = calc.compute(setOf(mon, tue, wed), today = thu, restDays = 0)
        assertEquals(3, r.current)
        assertEquals(3, r.longest)
    }

    @Test fun calorieBreaksWhenAFullDayMissed() {
        // In zone Mon/Tue/Wed, today Fri (Thu missed, today not logged) -> broken
        val r = calc.compute(setOf(mon, tue, wed), today = fri, restDays = 0)
        assertEquals(0, r.current)
        assertEquals(3, r.longest)
    }

    @Test fun longestCanExceedCurrent() {
        // Old 3-day chain (Mon-Wed), gap, then a single qualifying Fri
        val r = calc.compute(setOf(mon, tue, wed, fri), today = fri, restDays = 0)
        assertEquals(1, r.current)
        assertEquals(3, r.longest)
    }
}
