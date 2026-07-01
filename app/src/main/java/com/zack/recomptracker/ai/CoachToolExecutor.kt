package com.zack.recomptracker.ai

import android.util.Log
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.coach.CoachMemory
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.remote.WebSearchProvider
import com.zack.recomptracker.data.remote.toToolJson
import com.zack.recomptracker.data.repository.DailyMetricsInput
import com.zack.recomptracker.data.repository.ExerciseLibraryRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.MealEntryInput
import com.zack.recomptracker.data.repository.NewWorkoutLine
import com.zack.recomptracker.data.repository.PlanRepository
import com.zack.recomptracker.data.repository.PlannedSetDraft
import com.zack.recomptracker.data.repository.WorkoutRepository
import com.zack.recomptracker.data.repository.WorkoutSessionRepository
import com.zack.recomptracker.domain.activity.ActivitySummary
import com.zack.recomptracker.domain.adherence.AdherenceCalculator
import com.zack.recomptracker.domain.adherence.NutritionDay
import com.zack.recomptracker.domain.coach.TrainingDerivations
import com.zack.recomptracker.domain.coach.TrendDirection
import com.zack.recomptracker.domain.food.MealEntryTypes
import com.zack.recomptracker.domain.food.MealSuggester
import com.zack.recomptracker.domain.food.SuggestMacros
import com.zack.recomptracker.domain.food.SuggestionFocus
import com.zack.recomptracker.domain.food.SuggestionFood
import com.zack.recomptracker.domain.food.SuggestionResult
import com.zack.recomptracker.domain.trend.MeasurementPoint
import com.zack.recomptracker.domain.trend.TrendCalculator
import com.zack.recomptracker.domain.workout.Exercise
import com.zack.recomptracker.domain.workout.WorkoutSession
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.LocalDate

class CoachToolExecutor(
    private val logRepository: LogRepository,
    private val planRepository: PlanRepository,
    private val dateProvider: DateProvider,
    private val webSearchProvider: WebSearchProvider? = null,
    private val workoutSessionRepository: WorkoutSessionRepository? = null,
    private val workoutRepository: WorkoutRepository? = null,
    private val exerciseLibraryRepository: ExerciseLibraryRepository? = null,
    private val coachMemory: CoachMemory? = null,
) {
    private val adherenceCalculator = AdherenceCalculator()
    private val trendCalculator = TrendCalculator()
    private val toolJson = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun execute(name: String, args: Map<String, String>): String = when (name) {
        "get_today_summary" -> getTodaySummary(args)
        "get_weekly_trends" -> getWeeklyTrends()
        "get_training_summary" -> getTrainingSummary()
        "get_body_trends" -> getBodyTrends()
        "search_food_library" -> searchFoodLibrary(args)
        "suggest_meals" -> suggestMeals(args)
        "log_meal" -> logMeal(args)
        "log_metric" -> logMetric(args)
        "update_calorie_target" -> updateCalorieTarget(args)
        "search_web" -> searchWeb(args)
        "get_routines" -> getRoutines()
        "search_exercises" -> searchExercises(args)
        "create_routine" -> createRoutine(args)
        "edit_routine" -> editRoutine(args)
        "create_exercise" -> createExercise(args)
        "delete_meal" -> deleteMeal(args)
        "edit_meal" -> editMeal(args)
        "remember" -> remember(args)
        "forget" -> forget(args)
        else -> """{"error":"unknown tool $name"}"""
    }

    private suspend fun getTodaySummary(args: Map<String, String> = emptyMap()): String {
        val today = args["date"]?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        } ?: dateProvider.today()
        // Use getDay (direct suspend DAO query) instead of observeDay(...).first() — the
        // combine-backed Flow requires both inner flows to emit before producing its first
        // value, which can silently return 0 meals when no daily_log row exists for today.
        val dayLog = logRepository.getDay(today)
        Log.d("RecompCoach", "get_today_summary: date=$today meals=${dayLog.meals.size} cals=${dayLog.totals.calories}")
        // Tag each meal with its planned flag. `totals` below counts eaten entries only, so
        // without this the model would see meals it can't reconcile with the totals (and might
        // report a plan as already eaten). planned=true entries are intentions, not reality.
        val mealsJson = dayLog.meals.joinToString(separator = ",") { meal ->
            """{"name":"${meal.name.esc()}","calories":${meal.calories},"protein_g":${meal.proteinG},"carbs_g":${meal.carbsG},"fat_g":${meal.fatG},"meal_type":"${meal.mealType.esc()}","planned":${meal.planned}}"""
        }
        val log = dayLog.dailyLog
        val dailyLogJson = if (log != null) {
            """{"weight_kg":${log.bodyWeightKg},"waist_cm":${log.waistCm},"steps":${log.steps},"sleep_hours":${log.sleepHours},"energy_score":${log.energyScore},"hunger_score":${log.hungerScore},"soreness_score":${log.sorenessScore},"trained":${log.trained},"notes":"${log.notes.esc()}"}"""
        } else {
            "null"
        }
        val plannedJson = dayLog.plannedTotals.let { p ->
            """{"calories":${p.calories},"protein_g":${p.proteinG},"carbs_g":${p.carbsG},"fat_g":${p.fatG}}"""
        }
        return """{"date":"$today","meals":[$mealsJson],"totals":{"calories":${dayLog.totals.calories},"protein_g":${dayLog.totals.proteinG},"carbs_g":${dayLog.totals.carbsG},"fat_g":${dayLog.totals.fatG}},"planned_totals":$plannedJson,"daily_log":$dailyLogJson}"""
    }

    private suspend fun getWeeklyTrends(): String {
        val today = dateProvider.today()
        val start = today.minusDays(6)
        val macroMap = logRepository.getWeekMacros(start, today)
        val dailyEntries = (0..6).joinToString(separator = ",") { offset ->
            val date = start.plusDays(offset.toLong())
            val m = macroMap[date]
            val cals = m?.calories ?: 0
            val prot = m?.proteinG ?: 0.0
            val carbs = m?.carbsG ?: 0.0
            val fat = m?.fatG ?: 0.0
            """{"date":"$date","calories":$cals,"protein_g":$prot,"carbs_g":$carbs,"fat_g":$fat}"""
        }
        val weekDates = (0..6).map { start.plusDays(it.toLong()) }
        val targetsByDate = planRepository.targetsByDate(weekDates)
        val nutritionDays = weekDates.map { date ->
            NutritionDay(
                date = date,
                calories = macroMap[date]?.calories ?: 0,
                targetCalories = targetsByDate[date]?.calories ?: 0,
            )
        }
        // adherence_percent = graded closeness on logged days; days_logged = the separate
        // logging-consistency signal. A logged-but-over-target day no longer reads as 100%.
        val adherencePercent = adherenceCalculator.calculate(nutritionDays).toInt()
        val daysLogged = macroMap.values.count { it.calories > 0 }
        return """{"week_start":"$start","week_end":"$today","daily_macros":[$dailyEntries],"adherence_percent":$adherencePercent,"days_logged":$daysLogged}"""
    }

    /**
     * Strength / lifting / recovery-load snapshot over the trailing [TRAINING_WINDOW_DAYS] days.
     * Every number is computed by the pure [TrainingDerivations] and [ActivitySummary] calculators —
     * the model never invents figures. Per-lift latest e1RM + trend, total volume, sessions/week
     * (over the trained-day union of session dates and `trained` daily-logs), recent RIR, and recent
     * soreness. When no completed sessions land in the window, returns a compact `note` object.
     */
    private suspend fun getTrainingSummary(): String {
        val repo = workoutSessionRepository
            ?: return """{"window_days":$TRAINING_WINDOW_DAYS,"sessions_per_week":0,"lifts":[],"note":"no completed training sessions logged in this window"}"""
        val today = dateProvider.today()
        val windowStart = today.minusDays((TRAINING_WINDOW_DAYS - 1).toLong())

        // Dated (date, session) pairs, oldest → newest — the shape every TrainingDerivations fn takes.
        val datedSessions: List<Pair<LocalDate, WorkoutSession>> =
            repo.getCompletedSessionsSince(windowStart)
                .mapNotNull { session -> parseDate(session.date)?.let { it to session } }
                .filter { (date, _) -> !date.isBefore(windowStart) && !date.isAfter(today) }
                .sortedBy { it.first }

        // One read of the daily logs, reused for trained-day frequency and recent soreness below.
        val dailyLogs = logRepository.observeDailyLogs().first()

        // Trained-day frequency uses the full union (session dates + `trained` daily-logs) over the
        // trailing 4 weeks — the single ActivitySummary definition used across the app.
        val trainedLogDates = dailyLogs
            .filter { it.trained }
            .mapNotNull { parseDate(it.date) }
        val workoutDays = ActivitySummary.workoutDays(
            completedSessionDates = datedSessions.map { it.first },
            trainedLogDates = trainedLogDates,
        )
        val sessionsPerWeek = ActivitySummary.weeklyTrainingFrequency(workoutDays, today)

        if (datedSessions.isEmpty()) {
            return """{"window_days":$TRAINING_WINDOW_DAYS,"sessions_per_week":${sessionsPerWeek.round1()},"lifts":[],"note":"no completed training sessions logged in this window"}"""
        }

        val latestE1rm = TrainingDerivations.latestE1rmByExercise(datedSessions)
        val liftsJson = latestE1rm.entries
            .sortedByDescending { it.value }
            .joinToString(",") { (name, e1rm) ->
                val trend = TrainingDerivations.trendDirection(datedSessions, name)
                """{"name":"${name.esc()}","latest_e1rm_kg":${e1rm.round1()},"trend":"${trend.toJson()}"}"""
            }
        val totalVolume = TrainingDerivations.totalTrainingVolume(datedSessions)
        val recentRir = TrainingDerivations.recentRir(datedSessions)

        // Recent soreness: the last few non-null soreness scores from daily logs in the window,
        // newest first — a recovery-load signal alongside RIR.
        val recentSoreness = dailyLogs
            .mapNotNull { log -> parseDate(log.date)?.let { it to log } }
            .filter { (date, _) -> !date.isBefore(windowStart) && !date.isAfter(today) }
            .sortedByDescending { it.first }
            .mapNotNull { it.second.sorenessScore }
            .take(RECENT_SORENESS_COUNT)

        return """{"window_days":$TRAINING_WINDOW_DAYS,"sessions_per_week":${sessionsPerWeek.round1()},"total_volume_kg":${totalVolume.toInt()},"lifts":[$liftsJson],"recent_rir":${recentRir.toJsonArray()},"recent_soreness":${recentSoreness.toJsonArray()}}"""
    }

    /**
     * Body-measurement history over the trailing [TRAINING_WINDOW_DAYS] days: weight / waist /
     * skinfold linear trends (kg or cm or mm per week via [TrendCalculator.trendPerWeek]), plus the
     * latest weight & waist and a 7-day average weight. Every field is null-safe — a metric that was
     * never logged yields `null` for its trend/latest so the model can say "no data" rather than guess.
     */
    private suspend fun getBodyTrends(): String {
        val today = dateProvider.today()
        val windowStart = today.minusDays((TRAINING_WINDOW_DAYS - 1).toLong())

        val logsInWindow = logRepository.observeDailyLogs().first()
            .mapNotNull { log -> parseDate(log.date)?.let { it to log } }
            .filter { (date, _) -> !date.isBefore(windowStart) && !date.isAfter(today) }
            .sortedBy { it.first }

        val weightSeries = doubleSeries(logsInWindow) { it.bodyWeightKg }
        val waistSeries = doubleSeries(logsInWindow) { it.waistCm }
        val skinfoldSeries = doubleSeries(logsInWindow) { it.waistSkinfoldMm }

        val weightTrend = trendOrNull(weightSeries)
        val waistTrend = trendOrNull(waistSeries)
        val skinfoldTrend = trendOrNull(skinfoldSeries)

        val latestWeight = weightSeries.maxByOrNull { it.date }?.value
        val latestWaist = waistSeries.maxByOrNull { it.date }?.value

        // 7-day average weight ending today, over logged weigh-ins only.
        val weekStart = today.minusDays(6)
        val last7Weights = weightSeries
            .filter { !it.date.isBefore(weekStart) && !it.date.isAfter(today) }
            .mapNotNull { it.value }
        val avgWeight7 = if (last7Weights.isEmpty()) null else last7Weights.average()

        return """{"window_days":$TRAINING_WINDOW_DAYS,"weight_trend_kg_per_week":${weightTrend.round2Json()},"waist_trend_cm_per_week":${waistTrend.round2Json()},"skinfold_trend_mm_per_week":${skinfoldTrend.round2Json()},"latest_weight_kg":${latestWeight.round1Json()},"latest_waist_cm":${latestWaist.round1Json()},"avg_weight_7d":${avgWeight7.round1Json()}}"""
    }

    private suspend fun searchFoodLibrary(args: Map<String, String>): String {
        val query = args["query"]?.trim()
            ?: return """{"error":"search_food_library requires 'query'"}"""
        if (query.isEmpty()) return """{"results":[],"count":0}"""

        val requestedGrams = args["grams"]?.toDoubleOrNull()
        val matches = scoredFoodMatches(query).take(5)

        if (matches.isEmpty()) return """{"results":[],"count":0}"""

        val resultsJson = matches.joinToString(",") { (food, _) ->
            val servingGrams = food.householdServingGrams
            val scale = if (requestedGrams != null && servingGrams != null && servingGrams > 0) {
                requestedGrams / servingGrams
            } else null
            val calories = if (scale != null) (food.calories * scale).toInt() else food.calories
            val proteinG = if (scale != null) food.proteinG * scale else food.proteinG
            val carbsG   = if (scale != null) food.carbsG   * scale else food.carbsG
            val fatG     = if (scale != null) food.fatG     * scale else food.fatG
            val servingLabel = if (requestedGrams != null) "${requestedGrams.toInt()}g" else food.servingName.esc()
            """{"name":"${food.name.esc()}","calories":$calories,"protein_g":$proteinG,"carbs_g":$carbsG,"fat_g":$fatG,"serving":"$servingLabel"}"""
        }
        return """{"results":[$resultsJson],"count":${matches.size}}"""
    }

    private suspend fun logMeal(args: Map<String, String>): String {
        val name = args["name"] ?: return """{"error":"log_meal requires 'name'"}"""
        val requestedGrams = args["grams"]?.toDoubleOrNull()
        val mealType = args["meal_type"]
            ?.takeIf { it in setOf("Breakfast", "Lunch", "Dinner", "Snack") }
            ?: "Snack"
        // Optional date: a future date plans the meal instead of logging it as eaten.
        val logDate = args["date"]?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: dateProvider.today()
        val planned = logDate.isAfter(dateProvider.today())

        // Always check the food library first — use its macros instead of the model's
        // estimates. This ensures the entry matches the saved food exactly, regardless of
        // whether the model called search_food_library beforehand.
        val allFoods = logRepository.getSavedFoods()
        val libraryFood = scoredFoodMatches(name, allFoods).firstOrNull()?.first
        Log.d("RecompCoach", "logMeal: query='$name' grams=$requestedGrams library_hit=${libraryFood?.name} total_foods=${allFoods.size}")
        val finalName: String
        val calories: Int
        val proteinG: Double
        val carbsG: Double
        val fatG: Double

        if (libraryFood != null) {
            val servingGrams = libraryFood.householdServingGrams
            val scale = if (requestedGrams != null && servingGrams != null && servingGrams > 0)
                requestedGrams / servingGrams else 1.0
            finalName = libraryFood.name
            calories = (libraryFood.calories * scale).toInt()
            proteinG = libraryFood.proteinG * scale
            carbsG = libraryFood.carbsG * scale
            fatG = libraryFood.fatG * scale
            Log.d("RecompCoach", "logMeal: using library '$finalName' scale=$scale calories=$calories")
        } else {
            // Food not in library — fall back to model-provided macros.
            finalName = name
            // LiteRT-LM may surface integer JSON values as Double (e.g. "500.0"), so try
            // toIntOrNull first and fall back to toDoubleOrNull().toInt() before giving up.
            calories = args["calories"]?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
                ?: return """{"error":"'${name.esc()}' not found in food library. Please call log_meal again with calories and macros."}"""
            // Guard: model passes 0 as placeholder when it expects library lookup to handle it.
            // Logging 0 calories is always wrong for real food — return an error so the model retries.
            if (calories <= 0) return """{"error":"'${name.esc()}' not found in food library. Please call log_meal again with the actual calories."}"""
            proteinG = args["protein_g"]?.toDoubleOrNull() ?: 0.0
            carbsG = args["carbs_g"]?.toDoubleOrNull() ?: 0.0
            fatG = args["fat_g"]?.toDoubleOrNull() ?: 0.0
        }

        // For library foods: populate all the metadata fields so the entry renders in the
        // food log exactly like a manually-logged library food (shows "Xg ·" prefix and
        // allows amount editing). mealType is set to FOOD_LIBRARY so the entry is tagged
        // correctly; slot assignment still uses the user's mealType arg via matchedSlotId.
        val logGrams = if (libraryFood != null) requestedGrams ?: libraryFood.householdServingGrams else null
        val input = MealEntryInput(
            date = logDate,
            mealType = if (libraryFood != null) MealEntryTypes.FOOD_LIBRARY else mealType,
            name = finalName,
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            amountGrams = logGrams,
            basePer100Calories = libraryFood?.calories,
            basePer100ProteinG = libraryFood?.proteinG,
            basePer100CarbsG = libraryFood?.carbsG,
            basePer100FatG = libraryFood?.fatG,
            entryServingName = libraryFood?.householdServingName,
            entryServingGrams = libraryFood?.householdServingGrams,
            loggedByServings = false,
            planned = planned,
        )
        // Match the meal_type to a named slot (case-insensitive) so the entry appears
        // inside the correct slot card in the food log screen, not just in the totals.
        val matchedSlotId = logRepository.getSlots()
            .firstOrNull { it.name.trim().equals(mealType, ignoreCase = true) }
            ?.id
        logRepository.addMealToSlot(input, matchedSlotId)
        return if (planned) {
            """{"success":true,"planned":"${finalName.esc()}","date":"$logDate","calories":$calories}"""
        } else {
            """{"success":true,"logged":"${finalName.esc()}","calories":$calories}"""
        }
    }

    // Shared food scoring used by both searchFoodLibrary and logMeal.
    //   3 = exact name match (case-insensitive)
    //   2 = name starts with query
    //   1 = name contains query
    //   0 = every query word appears somewhere in name
    //  -1 = no match → excluded
    private suspend fun scoredFoodMatches(query: String): List<Pair<SavedFoodEntity, Int>> =
        scoredFoodMatches(query, logRepository.getSavedFoods())

    private fun scoredFoodMatches(query: String, foods: List<SavedFoodEntity>): List<Pair<SavedFoodEntity, Int>> {
        val queryLower = query.lowercase().trim()
        val queryWords = queryLower.split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun score(name: String): Int {
            val n = name.lowercase()
            return when {
                n == queryLower -> 3
                n.startsWith(queryLower) -> 2
                n.contains(queryLower) -> 1
                queryWords.all { n.contains(it) } -> 0
                else -> -1
            }
        }
        return foods.map { it to score(it.name) }.filter { (_, s) -> s >= 0 }.sortedByDescending { (_, s) -> s }
    }

    private val validMetrics = setOf(
        "weight_kg", "waist_cm", "sleep_hours",
        "energy_score", "hunger_score", "soreness_score",
    )

    private val scoreMetrics = setOf("energy_score", "hunger_score", "soreness_score")

    private suspend fun logMetric(args: Map<String, String>): String {
        val metric = args["metric"] ?: return """{"error":"log_metric requires 'metric'"}"""
        if (metric !in validMetrics) return """{"error":"unknown metric '${metric.esc()}'"}"""
        val value = args["value"]?.toDoubleOrNull()
            ?: return """{"error":"log_metric requires a numeric 'value'"}"""
        if (metric in scoreMetrics && value != kotlin.math.floor(value)) {
            return """{"error":"$metric must be a whole number"}"""
        }
        val rangeError = when (metric) {
            "energy_score", "hunger_score", "soreness_score" ->
                if (value.toInt() !in 1..10) "$metric must be between 1 and 10" else null
            "weight_kg" -> if (value !in 20.0..300.0) "weight_kg must be between 20 and 300" else null
            "waist_cm" -> if (value !in 40.0..200.0) "waist_cm must be between 40 and 200" else null
            "sleep_hours" -> if (value !in 0.0..24.0) "sleep_hours must be between 0 and 24" else null
            else -> null
        }
        if (rangeError != null) return """{"error":"$rangeError"}"""
        val today = dateProvider.today()
        val existing = logRepository.getDay(today).dailyLog
        logRepository.saveDailyMetrics(
            DailyMetricsInput(
                date = today,
                bodyWeightKg = if (metric == "weight_kg") value else existing?.bodyWeightKg,
                waistCm = if (metric == "waist_cm") value else existing?.waistCm,
                waistSkinfoldMm = existing?.waistSkinfoldMm,
                steps = existing?.steps,
                sleepHours = if (metric == "sleep_hours") value else existing?.sleepHours,
                energyScore = if (metric == "energy_score") value.toInt() else existing?.energyScore,
                hungerScore = if (metric == "hunger_score") value.toInt() else existing?.hungerScore,
                sorenessScore = if (metric == "soreness_score") value.toInt() else existing?.sorenessScore,
                trained = existing?.trained ?: false,
                notes = existing?.notes ?: "",
            ),
        )
        return """{"success":true,"metric":"${metric.esc()}","value":$value}"""
    }

    private suspend fun updateCalorieTarget(args: Map<String, String>): String {
        // Same Double-representation guard as logMeal — model may emit e.g. 2200.0.
        val newTarget = args["target_calories"]?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
            ?: return """{"error":"update_calorie_target requires 'target_calories'"}"""
        if (newTarget < 500 || newTarget > 6_000) {
            return """{"error":"target_calories must be between 500 and 6000"}"""
        }
        val prefs = planRepository.preferences.first()
        planRepository.save(prefs.withCalorieTarget(newTarget))
        return """{"success":true,"new_target_calories":$newTarget}"""
    }

    private suspend fun searchWeb(args: Map<String, String>): String {
        val query = args["query"]?.takeIf { it.isNotBlank() }?.trim()
            ?: return """{"error":"search_web requires 'query'"}"""
        val provider = webSearchProvider ?: return """{"error":"web search unavailable"}"""
        val result = provider.search(query) ?: return """{"error":"web search unavailable"}"""
        return result.toToolJson()
    }

    private suspend fun getRoutines(): String {
        val repo = workoutRepository ?: return """{"error":"routines unavailable"}"""
        val routines = repo.observeAll().first()
        val items = routines.joinToString(",") { r ->
            val ex = r.exercises.joinToString(",") { line ->
                val first = line.plannedSets.firstOrNull()
                """{"name":"${line.exercise.name.esc()}","sets":${line.plannedSets.size}""" +
                    (first?.targetReps?.let { ""","reps":$it""" } ?: "") +
                    (first?.targetWeightKg?.let { ""","weight_kg":$it""" } ?: "") + "}"
            }
            """{"name":"${r.name.esc()}","exercises":[$ex]}"""
        }
        return """{"routines":[$items]}"""
    }

    // Word-based exercise scoring, mirroring scoredFoodMatches. The user's library has
    // non-standard names ("Dumbbell Incline Bench Press"), so a contiguous-substring match
    // (ExerciseDao.search) misses "incline dumbbell press". Word-overlap reuses existing
    // exercises instead of the coach creating duplicates.
    //   4 = exact name match (case-insensitive)
    //   3 = name starts with query
    //   2 = name contains query
    //   1 = every query word appears somewhere in name
    //  -1 = no match → excluded
    private fun scoredExerciseMatches(query: String, all: List<Exercise>): List<Pair<Exercise, Int>> {
        val queryLower = query.lowercase().trim()
        val queryWords = queryLower.split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun score(name: String): Int {
            val n = name.lowercase()
            return when {
                n == queryLower -> 4
                n.startsWith(queryLower) -> 3
                n.contains(queryLower) -> 2
                queryWords.isNotEmpty() && queryWords.all { n.contains(it) } -> 1
                else -> -1
            }
        }
        return all.map { it to score(it.name) }.filter { (_, s) -> s >= 1 }.sortedByDescending { (_, s) -> s }
    }

    private suspend fun searchExercises(args: Map<String, String>): String {
        val query = args["query"]?.trim().orEmpty()
        if (query.isBlank()) return """{"error":"search_exercises requires 'query'"}"""
        val lib = exerciseLibraryRepository ?: return """{"error":"exercise library unavailable"}"""
        val matches = scoredExerciseMatches(query, lib.all()).take(8).joinToString(",") { (e, _) ->
            """{"name":"${e.name.esc()}","primary_muscles":[${e.primaryMuscles.joinToString(",") { "\"${it.esc()}\"" }}]""" +
                (e.equipment?.let { ""","equipment":"${it.esc()}"""" } ?: "") + "}"
        }
        return """{"matches":[$matches]}"""
    }

    /**
     * Resolve an exercise name to a library id from the preloaded [all] list, using the word-based
     * scorer with a confident bar: the top match must score >= 1 (all-words-or-better). Returns null
     * when nothing clears the bar, so the caller can report it unresolved rather than mis-mapping
     * (e.g. "leg press" must NOT silently resolve to "Leg Extension").
     */
    private fun resolveExerciseId(name: String, all: List<Exercise>): Long? =
        scoredExerciseMatches(name, all).firstOrNull()?.first?.id

    /**
     * Up to [limit] closest existing names for an unresolved query, for the error suggestion.
     * Uses the word scorer first (score >= 1); if nothing clears that bar, falls back to any
     * exercise sharing at least one query word, so the user still gets a hint.
     */
    private fun closestExerciseNames(name: String, all: List<Exercise>, limit: Int = 3): List<String> {
        val scored = scoredExerciseMatches(name, all)
        if (scored.isNotEmpty()) return scored.take(limit).map { it.first.name }
        val queryWords = name.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return all.filter { ex ->
            val n = ex.name.lowercase()
            queryWords.any { n.contains(it) }
        }.take(limit).map { it.name }
    }

    private fun unresolvedError(unresolved: List<Pair<String, List<String>>>): String {
        val parts = unresolved.joinToString("; ") { (name, suggestions) ->
            val hint = if (suggestions.isEmpty()) "" else " (closest: ${suggestions.joinToString(", ") { it.esc() }})"
            "'${name.esc()}'$hint"
        }
        return """{"error":"not found: $parts. Use create_exercise only if none fit."}"""
    }

    /** Build `sets` uniform planned-set drafts carrying the same optional reps/weight target. */
    private fun uniformSets(sets: Int, reps: Int?, weightKg: Double?): List<PlannedSetDraft> =
        (1..sets.coerceIn(1, 20)).map { PlannedSetDraft(targetReps = reps, targetWeightKg = weightKg) }

    private suspend fun createRoutine(args: Map<String, String>): String {
        val repo = workoutRepository ?: return """{"error":"routines unavailable"}"""
        val name = args["name"]?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"create_routine requires 'name'"}"""
        val parsed = runCatching {
            toolJson.decodeFromString<List<RoutineExerciseArg>>(args["exercises"] ?: "[]")
        }.getOrElse { return """{"error":"could not parse 'exercises' list"}""" }
        if (parsed.isEmpty()) return """{"error":"a routine needs at least one exercise"}"""

        val allExercises = exerciseLibraryRepository?.all() ?: emptyList()
        val lines = mutableListOf<NewWorkoutLine>()
        val unresolved = mutableListOf<Pair<String, List<String>>>()
        for (e in parsed) {
            val id = resolveExerciseId(e.name, allExercises)
            if (id == null) { unresolved += e.name to closestExerciseNames(e.name, allExercises); continue }
            lines += NewWorkoutLine(exerciseId = id, plannedSets = uniformSets(e.sets, e.reps, e.weight_kg))
        }
        if (unresolved.isNotEmpty()) return unresolvedError(unresolved)
        return runCatching {
            repo.saveWorkout(name, null, lines)
            """{"success":true,"routine":"${name.esc()}","exercises":${lines.size}}"""
        }.getOrElse { """{"error":"${(it.message ?: "could not save routine").esc()}"}""" }
    }

    private suspend fun editRoutine(args: Map<String, String>): String {
        val repo = workoutRepository ?: return """{"error":"routines unavailable"}"""
        val name = args["name"]?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"edit_routine requires 'name'"}"""
        val current = repo.observeAll().first().firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: return """{"error":"routine '${name.esc()}' not found"}"""

        val add = runCatching { toolJson.decodeFromString<List<RoutineExerciseArg>>(args["add"] ?: "[]") }.getOrDefault(emptyList())
        val remove = runCatching { toolJson.decodeFromString<List<String>>(args["remove"] ?: "[]") }.getOrDefault(emptyList())
        val retarget = runCatching { toolJson.decodeFromString<List<RetargetArg>>(args["retarget"] ?: "[]") }.getOrDefault(emptyList())
        val newName = args["new_name"]?.trim()?.takeIf { it.isNotBlank() }

        // Start from the current lines (preserve everything not explicitly changed).
        val lines = current.exercises.sortedBy { it.sortOrder }.map { line ->
            NewWorkoutLine(
                exerciseId = line.exercise.id,
                plannedSets = line.plannedSets.map { PlannedSetDraft(it.targetReps, it.targetWeightKg) },
                note = line.note,
            )
        }.toMutableList()

        // remove by exercise name
        if (remove.isNotEmpty()) {
            val removeIds = current.exercises
                .filter { ex -> remove.any { it.equals(ex.exercise.name, ignoreCase = true) } }
                .map { it.exercise.id }.toSet()
            lines.removeAll { it.exerciseId in removeIds }
        }

        // retarget existing lines (by matching exercise id resolved from current template)
        for (rt in retarget) {
            val ex = current.exercises.firstOrNull { it.exercise.name.equals(rt.name, ignoreCase = true) } ?: continue
            val idx = lines.indexOfFirst { it.exerciseId == ex.exercise.id }
            if (idx < 0) continue
            val existingFirst = lines[idx].plannedSets.firstOrNull()
            val setCount = rt.sets ?: lines[idx].plannedSets.size
            val reps = rt.reps ?: existingFirst?.targetReps
            val weight = rt.weight_kg ?: existingFirst?.targetWeightKg
            lines[idx] = lines[idx].copy(plannedSets = uniformSets(setCount, reps, weight))
        }

        // add new exercises (resolved from the library)
        val allExercises = exerciseLibraryRepository?.all() ?: emptyList()
        val unresolved = mutableListOf<Pair<String, List<String>>>()
        for (a in add) {
            val id = resolveExerciseId(a.name, allExercises)
            if (id == null) { unresolved += a.name to closestExerciseNames(a.name, allExercises); continue }
            lines += NewWorkoutLine(exerciseId = id, plannedSets = uniformSets(a.sets, a.reps, a.weight_kg))
        }
        if (unresolved.isNotEmpty()) return unresolvedError(unresolved)

        return runCatching {
            repo.updateWorkout(current.id, newName ?: current.name, current.note, lines)
            """{"success":true,"routine":"${(newName ?: current.name).esc()}","exercises":${lines.size}}"""
        }.getOrElse { """{"error":"${(it.message ?: "could not update routine").esc()}"}""" }
    }

    private suspend fun createExercise(args: Map<String, String>): String {
        val lib = exerciseLibraryRepository ?: return """{"error":"exercise library unavailable"}"""
        val name = args["name"]?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"create_exercise requires 'name'"}"""
        fun muscles(key: String): List<String> =
            runCatching { toolJson.decodeFromString<List<String>>(args[key] ?: "[]") }
                .getOrDefault(emptyList())
                .map { it.trim() }.filter { it.isNotBlank() }
        val primary = muscles("primary_muscles")
        val secondary = muscles("secondary_muscles")
        return runCatching {
            val id = lib.addCustomExercise(name, primaryMuscles = primary, secondaryMuscles = secondary)
            """{"success":true,"exercise":"${name.esc()}","id":$id}"""
        }.getOrElse { """{"error":"${(it.message ?: "could not create exercise").esc()}"}""" }
    }

    // ── Training / body-trend helpers ────────────────────────────────────────────

    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()

    /** Dated series of the non-null values produced by [selector], oldest → newest. */
    private inline fun doubleSeries(
        logsInWindow: List<Pair<LocalDate, DailyLogEntity>>,
        selector: (DailyLogEntity) -> Double?,
    ): List<MeasurementPoint> = logsInWindow
        .mapNotNull { (date, log) -> selector(log)?.let { MeasurementPoint(date, it) } }
        .sortedBy { it.date }

    /** Linear per-week trend via [TrendCalculator]; null when fewer than 2 logged points. */
    private fun trendOrNull(series: List<MeasurementPoint>): Double? {
        if (series.size < 2) return null
        return trendCalculator.trendPerWeek(series)
    }

    private fun TrendDirection?.toJson(): String = when (this) {
        TrendDirection.UP -> "up"
        TrendDirection.DOWN -> "down"
        TrendDirection.FLAT, null -> "flat"
    }

    /** Rounds to 1 decimal for a bare JSON number (no quotes). */
    private fun Double.round1(): String = String.format(java.util.Locale.US, "%.1f", this)

    /** Nullable → JSON: the metric rounded to 1 decimal, or the literal `null`. */
    private fun Double?.round1Json(): String =
        if (this == null) "null" else String.format(java.util.Locale.US, "%.1f", this)

    /** Nullable → JSON: the trend rounded to 2 decimals, or the literal `null`. */
    private fun Double?.round2Json(): String =
        if (this == null) "null" else String.format(java.util.Locale.US, "%.2f", this)

    private fun List<Int>.toJsonArray(): String = "[" + joinToString(",") + "]"

    /** Confident name matches among a day's logged meal entries (exact › startsWith › contains › all-words). */
    private fun scoredMealMatches(query: String, meals: List<MealEntryEntity>): List<MealEntryEntity> {
        val q = query.lowercase().trim()
        val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun score(name: String): Int {
            val n = name.lowercase()
            return when {
                n == q -> 3
                n.startsWith(q) -> 2
                n.contains(q) -> 1
                words.isNotEmpty() && words.all { n.contains(it) } -> 0
                else -> -1
            }
        }
        return meals.map { it to score(it.name) }
            .filter { (_, s) -> s >= 0 }
            .sortedByDescending { (_, s) -> s }
            .map { it.first }
    }

    private fun mealMatchJson(m: MealEntryEntity): String =
        """{"name":"${m.name.esc()}","meal_type":"${m.mealType.esc()}","calories":${m.calories}""" +
            (m.amountGrams?.let { ""","grams":${it.toInt()}""" } ?: "") + "}"

    private suspend fun deleteMeal(args: Map<String, String>): String {
        val name = args["name"]?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"delete_meal requires 'name'"}"""
        val date = args["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: dateProvider.today()
        val matches = scoredMealMatches(name, logRepository.getDay(date).meals)
        return when {
            matches.isEmpty() -> """{"error":"no logged meal matching '${name.esc()}' on $date"}"""
            matches.size > 1 -> """{"needs_disambiguation":true,"matches":[${matches.take(5).joinToString(",") { mealMatchJson(it) }}]}"""
            else -> {
                val entry = matches.first()
                logRepository.deleteMeal(entry.id)
                """{"success":true,"deleted":"${entry.name.esc()}","calories":${entry.calories}}"""
            }
        }
    }

    private suspend fun editMeal(args: Map<String, String>): String {
        val name = args["name"]?.trim().orEmpty()
        if (name.isBlank()) return """{"error":"edit_meal requires 'name'"}"""
        val date = args["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: dateProvider.today()
        val matches = scoredMealMatches(name, logRepository.getDay(date).meals)
        if (matches.isEmpty()) return """{"error":"no logged meal matching '${name.esc()}' on $date"}"""
        if (matches.size > 1) return """{"needs_disambiguation":true,"matches":[${matches.take(5).joinToString(",") { mealMatchJson(it) }}]}"""
        val entry = matches.first()

        var cal = entry.calories
        var p = entry.proteinG
        var c = entry.carbsG
        var f = entry.fatG
        var amt = entry.amountGrams

        val grams = args["grams"]?.toDoubleOrNull()
        if (grams != null && grams > 0) {
            val base = entry.basePer100Calories
            if (base != null) {
                cal = (base * grams / 100.0).toInt()
                p = (entry.basePer100ProteinG ?: 0.0) * grams / 100.0
                c = (entry.basePer100CarbsG ?: 0.0) * grams / 100.0
                f = (entry.basePer100FatG ?: 0.0) * grams / 100.0
                amt = grams
            } else if (entry.amountGrams != null && entry.amountGrams > 0) {
                val scale = grams / entry.amountGrams
                cal = (entry.calories * scale).toInt()
                p = entry.proteinG * scale
                c = entry.carbsG * scale
                f = entry.fatG * scale
                amt = grams
            }
            // else: no gram basis — fall through; require explicit macros below.
        }

        // Explicit macro args override any rescale (integer-as-double tolerated, matching log_meal).
        args["calories"]?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }?.let { cal = it }
        args["protein_g"]?.toDoubleOrNull()?.let { p = it }
        args["carbs_g"]?.toDoubleOrNull()?.let { c = it }
        args["fat_g"]?.toDoubleOrNull()?.let { f = it }

        val nothingChanged = cal == entry.calories && p == entry.proteinG && c == entry.carbsG &&
            f == entry.fatG && amt == entry.amountGrams
        if (nothingChanged) {
            return """{"error":"nothing to change — provide grams or calories/macros (this entry has no gram basis to rescale)"}"""
        }

        logRepository.updateMealEntry(
            entry.copy(calories = cal, proteinG = p, carbsG = c, fatG = f, amountGrams = amt),
        )
        return """{"success":true,"updated":"${entry.name.esc()}","calories":$cal}"""
    }

    private suspend fun remember(args: Map<String, String>): String {
        val text = args["text"]?.trim().orEmpty()
        if (text.isBlank()) return """{"error":"remember requires 'text'"}"""
        val mem = coachMemory ?: return """{"error":"memory unavailable"}"""
        val entry = mem.add(text) ?: return """{"error":"could not remember that"}"""
        return """{"success":true,"remembered":"${entry.text.esc()}"}"""
    }

    private suspend fun forget(args: Map<String, String>): String {
        val text = args["text"]?.trim().orEmpty()
        if (text.isBlank()) return """{"error":"forget requires 'text'"}"""
        val mem = coachMemory ?: return """{"error":"memory unavailable"}"""
        val removed = mem.removeMatching(text) ?: return """{"error":"nothing in memory matching '${text.esc()}'"}"""
        return """{"success":true,"forgot":"${removed.text.esc()}"}"""
    }

    private suspend fun suggestMeals(args: Map<String, String>): String {
        val date = args["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: dateProvider.today()
        val prefs = planRepository.preferences.first()
        val eaten = logRepository.getDay(date).totals
        val remaining = SuggestMacros(
            calories = (prefs.targetCalories - eaten.calories).coerceAtLeast(0),
            proteinG = (prefs.targetProteinG - eaten.proteinG).coerceAtLeast(0.0),
            carbsG = (prefs.targetCarbsG - eaten.carbsG).coerceAtLeast(0.0),
            fatG = (prefs.targetFatG - eaten.fatG).coerceAtLeast(0.0),
        )
        val proteinMetRatio = if (prefs.targetProteinG > 0) eaten.proteinG / prefs.targetProteinG else 1.0
        val library = logRepository.getSavedFoods().map {
            SuggestionFood(
                name = it.name, servingLabel = it.servingName, gramsPerServing = it.householdServingGrams,
                calories = it.calories, proteinG = it.proteinG, carbsG = it.carbsG, fatG = it.fatG,
            )
        }
        return serializeSuggestions(MealSuggester.suggest(remaining, proteinMetRatio, library))
    }

    private fun serializeSuggestions(r: SuggestionResult): String {
        val focus = when (r.focus) {
            SuggestionFocus.PROTEIN -> "protein"; SuggestionFocus.CARBS -> "carbs"
            SuggestionFocus.CALORIES -> "calories"; SuggestionFocus.NONE -> "none"
        }
        val rem = """{"calories":${r.remaining.calories},"protein_g":${r.remaining.proteinG},"carbs_g":${r.remaining.carbsG},"fat_g":${r.remaining.fatG}}"""
        if (r.focus == SuggestionFocus.NONE) {
            return """{"remaining":$rem,"focus":"none","message":"on target"}"""
        }
        val sugg = r.suggestions.joinToString(",") { s ->
            """{"name":"${s.name.esc()}","amount":"${s.amountLabel.esc()}","calories":${s.calories},"protein_g":${s.proteinG},"carbs_g":${s.carbsG},"fat_g":${s.fatG},"exact":true}"""
        }
        val combos = r.combos.joinToString(",") { c ->
            val items = c.items.joinToString(",") { """{"name":"${it.name.esc()}","amount":"${it.amountLabel.esc()}"}""" }
            """{"items":[$items],"calories":${c.calories},"protein_g":${c.proteinG},"carbs_g":${c.carbsG},"fat_g":${c.fatG}}"""
        }
        return """{"remaining":$rem,"focus":"$focus","suggestions":[$sugg],"combos":[$combos],"library_thin":${r.libraryThin}}"""
    }

    private companion object {
        /** Trailing window for the training + body-trend tools (matches CoachContextAssembler). */
        const val TRAINING_WINDOW_DAYS = 28

        /** How many of the most-recent non-null soreness scores get_training_summary surfaces. */
        const val RECENT_SORENESS_COUNT = 3
    }

    private fun String.esc() = replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

@kotlinx.serialization.Serializable
private data class RoutineExerciseArg(
    val name: String,
    val sets: Int,
    val reps: Int? = null,
    val weight_kg: Double? = null,
)

@kotlinx.serialization.Serializable
private data class RetargetArg(
    val name: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val weight_kg: Double? = null,
)
