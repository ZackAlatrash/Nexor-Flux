package com.zack.recomptracker.domain.body

/** Upper sanity bound for a single day's step count — rejects fat-fingered entries. */
const val MAX_DAILY_STEPS = 200_000

/** Result of validating the raw text of a steps input field. */
sealed interface StepsValidation {
    /** Parsed value; `null` means the field was left blank (allowed — no steps logged). */
    data class Valid(val steps: Int?) : StepsValidation
    data class Invalid(val message: String) : StepsValidation
}

/**
 * Validates a raw steps input string. A blank field is valid (no steps logged); otherwise the
 * value must be a whole number within `0..`[MAX_DAILY_STEPS]. Pure — no Android dependencies.
 */
fun validateStepsInput(raw: String): StepsValidation {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return StepsValidation.Valid(null)
    val parsed = trimmed.toIntOrNull()
        ?: return StepsValidation.Invalid("Steps must be a whole number.")
    if (parsed < 0 || parsed > MAX_DAILY_STEPS) {
        return StepsValidation.Invalid("Steps must be between 0 and 200,000.")
    }
    return StepsValidation.Valid(parsed)
}
