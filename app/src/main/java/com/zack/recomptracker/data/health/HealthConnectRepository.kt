package com.zack.recomptracker.data.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt
import com.zack.recomptracker.domain.foodimport.FoodImportCandidate

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
    val nutritionPermission: String = HealthPermission.getReadPermission(NutritionRecord::class)
    val historicalNutritionPermissions: Set<String> = setOf(
        nutritionPermission,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
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

    suspend fun hasNutritionPermission(): Boolean = runCatching {
        nutritionPermission in client.permissionController.getGrantedPermissions()
    }.getOrDefault(false)

    fun supportsHistoricalNutritionImport(): Boolean =
        availability() == HealthConnectAvailability.Available &&
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    suspend fun hasHistoricalNutritionPermissions(): Boolean = runCatching {
        client.permissionController.getGrantedPermissions().containsAll(historicalNutritionPermissions)
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

    suspend fun readHistoricalNutrition(days: Long = 365): Result<List<FoodImportCandidate>> = runCatching {
        check(supportsHistoricalNutritionImport()) {
            "Health Connect on this device cannot provide 365-day history access."
        }
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days))
        val foods = mutableListOf<FoodImportCandidate>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = NutritionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken,
                ),
            )
            foods += response.records.mapNotNull(NutritionRecord::toFoodImportCandidate)
            pageToken = response.pageToken
        } while (pageToken != null)
        foods
    }

    /**
     * Total steps per calendar day over the trailing [days] days (inclusive of today), grouped by
     * the device's local date. Used to backfill streaks/trends on first connect so they reflect
     * existing Health Connect history instead of starting from a single data point, and for the
     * steps-only foreground refresh (days = 1). Aggregated by Health Connect (see [readSteps] for
     * why raw-record summing is wrong). Days with no data are simply absent from the map. Returns
     * an empty map on any failure.
     */
    suspend fun readStepsHistory(days: Long = 30): Map<LocalDate, Int> = runCatching {
        val zone = ZoneId.systemDefault()
        val start: LocalDateTime = LocalDate.now(zone).minusDays(days - 1).atStartOfDay()
        val end: LocalDateTime = LocalDateTime.now(zone)
        val response = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        response.mapNotNull { bucket ->
            val steps = bucket.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
            bucket.startTime.toLocalDate() to steps.toInt()
        }.toMap()
    }.getOrDefault(emptyMap())

    /**
     * Steps via Health Connect's aggregate API, NOT a raw-record sum. Multiple sources (Samsung
     * Health / watch, Google's step tracking, the phone's own counter) each write their own
     * StepsRecords for the same walk; summing raw records counts every duplicate, which showed
     * 17k steps on a 4k day. The aggregate deduplicates across data origins. Null when Health
     * Connect has no step data in the window.
     */
    private suspend fun readSteps(start: Instant, end: Instant): Int? {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return response[StepsRecord.COUNT_TOTAL]?.toInt()
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

// Samsung Health syncs nutrition to Health Connect as one aggregate record per
// meal type (HealthConstants.Nutrition MEAL_TYPE_BREAKFAST/LUNCH/DINNER and the
// MORNING/AFTERNOON/EVENING_SNACK variants), labelling NutritionRecord.name with
// the localized meal-type rather than the foods inside it. Those aggregates are
// not reusable "foods", so we drop any record whose name is just a meal-type tag.
private val MEAL_TYPE_NAMES: Set<String> = setOf(
    // English (Samsung's six meal types + common synonyms)
    "breakfast", "lunch", "dinner", "supper", "brunch", "meal", "snack",
    "morning snack", "afternoon snack", "evening snack", "late night snack",
    "morning meal", "afternoon meal", "evening meal", "night meal",
    // Dutch (RIVM/NEVO users typically run Samsung Health in Dutch)
    "ontbijt", "middageten", "diner", "avondeten", "tussendoortje",
    "ochtendsnack", "middagsnack", "avondsnack", "snack ochtend", "snack middag", "snack avond",
    // Korean (Samsung Health's origin locale)
    "아침", "점심", "저녁", "간식", "야식", "아침식사", "점심식사", "저녁식사",
)

private fun NutritionRecord.toFoodImportCandidate(): FoodImportCandidate? {
    val foodName = name?.trim().orEmpty()
    if (foodName.isBlank()) return null
    if (foodName.lowercase(Locale.ROOT) in MEAL_TYPE_NAMES) return null
    val food = FoodImportCandidate(
        name = foodName,
        servingName = "100g",
        calories = energy?.inKilocalories?.roundToInt() ?: 0,
        proteinG = protein?.inGrams ?: 0.0,
        carbsG = totalCarbohydrate?.inGrams ?: 0.0,
        fatG = totalFat?.inGrams ?: 0.0,
    )
    return food.takeIf {
        it.calories > 0 || it.proteinG > 0 || it.carbsG > 0 || it.fatG > 0
    }
}
