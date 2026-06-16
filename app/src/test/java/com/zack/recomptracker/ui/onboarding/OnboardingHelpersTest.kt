package com.zack.recomptracker.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OnboardingHelpersTest {

    @Test
    fun heightMetricParsesDirectly() {
        assertEquals(178, parseHeightCm("178", metric = true))
    }

    @Test
    fun heightImperialConvertsInchesToCm() {
        // 70 in * 2.54 = 177.8 cm -> rounds to 178
        assertEquals(178, parseHeightCm("70", metric = false))
    }

    @Test
    fun heightBlankOrInvalidIsNull() {
        assertNull(parseHeightCm("", metric = true))
        assertNull(parseHeightCm("abc", metric = true))
    }

    @Test
    fun weightMetricParsesDecimal() {
        assertEquals(82.5, parseWeightKg("82.5", metric = true)!!, 0.001)
    }

    @Test
    fun weightImperialConvertsPoundsToKg() {
        // 180 lb * 0.45359237 = 81.6466... kg
        assertEquals(81.6466, parseWeightKg("180", metric = false)!!, 0.001)
    }

    @Test
    fun waistImperialConvertsInchesToCm() {
        assertEquals(86.36, parseWaistCm("34", metric = false)!!, 0.001)
        assertNull(parseWaistCm("", metric = true))
    }

    @Test
    fun ageYearsFromBirthDate() {
        val today = LocalDate.of(2026, 6, 16)
        assertEquals(30, ageYearsFrom("1996-03-14", today))
        assertNull(ageYearsFrom(null, today))
        assertNull(ageYearsFrom("not-a-date", today))
    }

    @Test
    fun birthDateValidWhenPlausibleAge() {
        val today = LocalDate.of(2026, 6, 16)
        assertTrue(isValidBirthDate("1996-03-14", today))
    }

    @Test
    fun birthDateInvalidWhenFutureOrImplausible() {
        val today = LocalDate.of(2026, 6, 16)
        assertFalse(isValidBirthDate("2030-01-01", today)) // future
        assertFalse(isValidBirthDate("2020-01-01", today)) // age 6 < 13
        assertFalse(isValidBirthDate(null, today))
    }
}
