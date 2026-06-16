package com.zack.recomptracker.ui

import com.zack.recomptracker.ui.today.CalorieDayStatus
import com.zack.recomptracker.ui.today.calorieStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieDayStatusTest {

    private val zoneLow  = 1800
    private val zoneHigh = 2000

    @Test
    fun belowZone_today_returnsBelowZone() {
        val result = calorieStatus(1500, zoneLow, zoneHigh, isToday = true, isPast = false)
        assertEquals("below zone today", CalorieDayStatus.BelowZone, result)
    }

    @Test
    fun belowZone_future_returnsBelowZone() {
        val result = calorieStatus(0, zoneLow, zoneHigh, isToday = false, isPast = false)
        assertEquals("zero cals future", CalorieDayStatus.BelowZone, result)
    }

    @Test
    fun inZone_returnsGoalHit() {
        val result = calorieStatus(1900, zoneLow, zoneHigh, isToday = true, isPast = false)
        assertEquals("in zone", CalorieDayStatus.GoalHit, result)
    }

    @Test
    fun exactlyAtLowerBound_returnsGoalHit() {
        val result = calorieStatus(1800, zoneLow, zoneHigh, isToday = false, isPast = true)
        assertEquals("at lower bound", CalorieDayStatus.GoalHit, result)
    }

    @Test
    fun exactlyAtUpperBound_returnsGoalHit() {
        val result = calorieStatus(2000, zoneLow, zoneHigh, isToday = true, isPast = false)
        assertEquals("at upper bound", CalorieDayStatus.GoalHit, result)
    }

    @Test
    fun overZone_returnsOver() {
        val result = calorieStatus(2200, zoneLow, zoneHigh, isToday = true, isPast = false)
        assertEquals("over zone today", CalorieDayStatus.Over, result)
    }

    @Test
    fun overZone_pastDay_returnsOver() {
        val result = calorieStatus(2500, zoneLow, zoneHigh, isToday = false, isPast = true)
        assertEquals("over zone past", CalorieDayStatus.Over, result)
    }

    @Test
    fun belowZone_pastDay_withLoggedCals_returnsMissed() {
        val result = calorieStatus(900, zoneLow, zoneHigh, isToday = false, isPast = true)
        assertEquals("past with cals missed", CalorieDayStatus.Missed, result)
    }

    @Test
    fun zeroCals_pastDay_returnsBelowZone_notMissed() {
        val result = calorieStatus(0, zoneLow, zoneHigh, isToday = false, isPast = true)
        assertEquals("zero cals past = not logged, not missed",
            CalorieDayStatus.BelowZone, result)
    }
}
