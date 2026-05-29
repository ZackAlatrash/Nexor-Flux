package com.zack.recomptracker.data.health

sealed class HealthConnectAvailability {
    object Available : HealthConnectAvailability()
    object NotInstalled : HealthConnectAvailability()
    object NotSupported : HealthConnectAvailability()
}

data class HealthConnectReadResult(
    val steps: Int? = null,
    val weightKg: Double? = null,
    val sleepHours: Double? = null,
)
