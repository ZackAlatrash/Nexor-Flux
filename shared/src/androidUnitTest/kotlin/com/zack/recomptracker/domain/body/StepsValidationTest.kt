package com.zack.recomptracker.domain.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsValidationTest {

    @Test
    fun `blank input is valid and parses to null`() {
        assertEquals(StepsValidation.Valid(null), validateStepsInput(""))
        assertEquals(StepsValidation.Valid(null), validateStepsInput("   "))
    }

    @Test
    fun `whole number within range is valid`() {
        assertEquals(StepsValidation.Valid(0), validateStepsInput("0"))
        assertEquals(StepsValidation.Valid(8210), validateStepsInput("8210"))
        assertEquals(StepsValidation.Valid(MAX_DAILY_STEPS), validateStepsInput("200000"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(StepsValidation.Valid(8210), validateStepsInput("  8210 "))
    }

    @Test
    fun `non-integer is rejected`() {
        val result = validateStepsInput("8.5")
        assertTrue(result is StepsValidation.Invalid)
        assertEquals("Steps must be a whole number.", (result as StepsValidation.Invalid).message)
    }

    @Test
    fun `negative value is rejected by range`() {
        assertTrue(validateStepsInput("-5") is StepsValidation.Invalid)
    }

    @Test
    fun `absurdly large value is rejected by upper bound`() {
        val result = validateStepsInput("999999999")
        assertTrue(result is StepsValidation.Invalid)
        assertEquals(
            "Steps must be between 0 and 200,000.",
            (result as StepsValidation.Invalid).message,
        )
    }
}
