package com.zack.recomptracker.data.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectRepository(context: Context) {

    private val appContext: Context = context.applicationContext

    // Created lazily — HealthConnectClient warns against multiple instances.
    // Only accessed after availability() confirms SDK_AVAILABLE.
    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(appContext)
    }

    val requiredPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    fun availability(): HealthConnectAvailability = when (
        HealthConnectClient.getSdkStatus(appContext)
    ) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NotInstalled
        else -> HealthConnectAvailability.NotSupported
    }

    fun permissionsContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    suspend fun hasPermissions(): Boolean = runCatching {
        client.permissionController.getGrantedPermissions().containsAll(requiredPermissions)
    }.getOrDefault(false)

    suspend fun readToday(date: LocalDate): HealthConnectReadResult = runCatching {
        val zone = ZoneId.systemDefault()
        val startOfDay: Instant = date.atStartOfDay(zone).toInstant()
        val now: Instant = Instant.now()
        val thirtyDaysAgo: Instant = date.minusDays(30).atStartOfDay(zone).toInstant()
        val yesterdayNoon: Instant = date.minusDays(1).atTime(12, 0).atZone(zone).toInstant()

        val steps = readSteps(startOfDay, now)
        val weightKg = readLatestWeight(thirtyDaysAgo, now)
        val sleepHours = readLatestSleep(yesterdayNoon, now)

        HealthConnectReadResult(steps = steps, weightKg = weightKg, sleepHours = sleepHours)
    }.getOrDefault(HealthConnectReadResult())

    private suspend fun readSteps(start: Instant, end: Instant): Int? {
        val response = client.readRecords(
            ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(start, end))
        )
        return if (response.records.isEmpty()) null
        else response.records.sumOf { it.count }.toInt()
    }

    private suspend fun readLatestWeight(start: Instant, end: Instant): Double? {
        val response = client.readRecords(
            ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(start, end))
        )
        return response.records.maxByOrNull { it.time }?.weight?.inKilograms
    }

    private suspend fun readLatestSleep(start: Instant, end: Instant): Double? {
        val response = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end))
        )
        val latest = response.records.maxByOrNull { it.endTime } ?: return null
        return Duration.between(latest.startTime, latest.endTime).toMinutes() / 60.0
    }
}
