package com.zack.recomptracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zack.recomptracker.data.local.dao.DailyLogDao
import com.zack.recomptracker.data.local.dao.CatalogFoodDao
import com.zack.recomptracker.data.local.dao.ExerciseDao
import com.zack.recomptracker.data.local.dao.MealEntryDao
import com.zack.recomptracker.data.local.dao.MealSlotDao
import com.zack.recomptracker.data.local.dao.PerformanceDao
import com.zack.recomptracker.data.local.dao.PlanVersionDao
import com.zack.recomptracker.data.local.dao.RecipeDao
import com.zack.recomptracker.data.local.dao.SavedFoodDao
import com.zack.recomptracker.data.local.dao.SavedMealDao
import com.zack.recomptracker.data.local.dao.WeeklyReviewDao
import com.zack.recomptracker.data.local.dao.UsageEventDao
import com.zack.recomptracker.data.local.dao.WorkoutDao
import com.zack.recomptracker.data.local.dao.WorkoutSessionDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.CatalogFoodEntity
import com.zack.recomptracker.data.local.entity.ExerciseEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.PlanVersionEntity
import com.zack.recomptracker.data.local.entity.PlannedSetEntity
import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.local.entity.SessionExerciseEntity
import com.zack.recomptracker.data.local.entity.SessionSetEntity
import com.zack.recomptracker.data.local.entity.UsageEventEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import com.zack.recomptracker.data.local.entity.WorkoutEntity
import com.zack.recomptracker.data.local.entity.WorkoutExerciseEntity
import com.zack.recomptracker.data.local.entity.WorkoutSessionEntity

@Database(
    entities = [
        DailyLogEntity::class,
        CatalogFoodEntity::class,
        MealEntryEntity::class,
        SavedFoodEntity::class,
        SavedMealEntity::class,
        LiftPerformanceEntity::class,
        WeeklyReviewEntity::class,
        MealSlotEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        PlannedSetEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SessionSetEntity::class,
        PlanVersionEntity::class,
        UsageEventEntity::class,
    ],
    version = 15,
    exportSchema = false,
)
abstract class RecompDatabase : RoomDatabase() {
    abstract fun catalogFoodDao(): CatalogFoodDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun savedFoodDao(): SavedFoodDao
    abstract fun savedMealDao(): SavedMealDao
    abstract fun performanceDao(): PerformanceDao
    abstract fun weeklyReviewDao(): WeeklyReviewDao
    abstract fun mealSlotDao(): MealSlotDao
    abstract fun recipeDao(): RecipeDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun planVersionDao(): PlanVersionDao
    abstract fun usageEventDao(): UsageEventDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS meal_slots (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "sort_order INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN slotId INTEGER")
                // Seed three default slots
                db.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Meal 1', 0)")
                db.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Lunch', 1)")
                db.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Dinner', 2)")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_foods ADD COLUMN householdServingName TEXT")
                db.execSQL("ALTER TABLE saved_foods ADD COLUMN householdServingGrams REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN amountGrams REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN basePer100Calories INTEGER")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN basePer100ProteinG REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN basePer100CarbsG REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN basePer100FatG REAL")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN entryServingName TEXT")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN entryServingGrams REAL")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_logs ADD COLUMN waistSkinfoldMm REAL")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN loggedByServings INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recipes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recipe_ingredients (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "recipeId INTEGER NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "sortOrder INTEGER NOT NULL, " +
                    "calories INTEGER NOT NULL, " +
                    "proteinG REAL NOT NULL, " +
                    "carbsG REAL NOT NULL, " +
                    "fatG REAL NOT NULL, " +
                    "amountGrams REAL, " +
                    "basePer100Calories INTEGER, " +
                    "basePer100ProteinG REAL, " +
                    "basePer100CarbsG REAL, " +
                    "basePer100FatG REAL, " +
                    "entryServingName TEXT, " +
                    "entryServingGrams REAL, " +
                    "loggedByServings INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(recipeId) REFERENCES recipes(id) ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recipe_ingredients_recipeId " +
                    "ON recipe_ingredients (recipeId)"
                )
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE meal_entries ADD COLUMN planned INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE weekly_reviews ADD COLUMN briefingJson TEXT")
                db.execSQL("ALTER TABLE weekly_reviews ADD COLUMN briefingSignature TEXT")
                db.execSQL("ALTER TABLE weekly_reviews ADD COLUMN briefingGeneratedAt TEXT")
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS exercises (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "source TEXT NOT NULL, sourceVersion TEXT NOT NULL, externalId TEXT NOT NULL, " +
                        "name TEXT NOT NULL, category TEXT, force TEXT, level TEXT, mechanic TEXT, equipment TEXT, " +
                        "primaryMuscles TEXT NOT NULL, secondaryMuscles TEXT NOT NULL, instructions TEXT NOT NULL, " +
                        "images TEXT NOT NULL, userCreated INTEGER NOT NULL DEFAULT 0)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_exercises_source_externalId ON exercises (source, externalId)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercises_name ON exercises (name)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS workouts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, note TEXT, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS workout_exercises (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "workoutId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, " +
                        "plannedSets INTEGER NOT NULL, targetReps INTEGER, sortOrder INTEGER NOT NULL, note TEXT, " +
                        "FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE NO ACTION)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercises_workoutId ON workout_exercises (workoutId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercises_exerciseId ON workout_exercises (exerciseId)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS workout_sessions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "workoutId INTEGER, workoutName TEXT NOT NULL, date TEXT NOT NULL, " +
                        "startedAt TEXT NOT NULL, completedAt TEXT, status TEXT NOT NULL, note TEXT, " +
                        "FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE SET NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_workoutId ON workout_sessions (workoutId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_date ON workout_sessions (date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_status ON workout_sessions (status)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS session_exercises (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sessionId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, exerciseName TEXT NOT NULL, " +
                        "sortOrder INTEGER NOT NULL, note TEXT, " +
                        "FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE NO ACTION)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_exercises_sessionId ON session_exercises (sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_exercises_exerciseId ON session_exercises (exerciseId)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS session_sets (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sessionExerciseId INTEGER NOT NULL, setNumber INTEGER NOT NULL, reps INTEGER NOT NULL, " +
                        "weightKg REAL, rir INTEGER, completed INTEGER NOT NULL DEFAULT 1, " +
                        "FOREIGN KEY(sessionExerciseId) REFERENCES session_exercises(id) ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_sets_sessionExerciseId ON session_sets (sessionExerciseId)")
            }
        }

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS planned_sets (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "workoutExerciseId INTEGER NOT NULL, setNumber INTEGER NOT NULL, " +
                        "targetReps INTEGER, targetWeightKg REAL, " +
                        "FOREIGN KEY(workoutExerciseId) REFERENCES workout_exercises(id) ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_sets_workoutExerciseId ON planned_sets (workoutExerciseId)")
                db.execSQL(
                    "CREATE TABLE workout_exercises_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workoutId INTEGER NOT NULL, " +
                        "exerciseId INTEGER NOT NULL, sortOrder INTEGER NOT NULL, note TEXT, " +
                        "FOREIGN KEY(workoutId) REFERENCES workouts(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE NO ACTION)",
                )
                db.execSQL(
                    "INSERT INTO workout_exercises_new (id, workoutId, exerciseId, sortOrder, note) " +
                        "SELECT id, workoutId, exerciseId, sortOrder, note FROM workout_exercises",
                )
                db.execSQL("DROP TABLE workout_exercises")
                db.execSQL("ALTER TABLE workout_exercises_new RENAME TO workout_exercises")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercises_workoutId ON workout_exercises (workoutId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercises_exerciseId ON workout_exercises (exerciseId)")
            }
        }

        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN durationSeconds INTEGER")
            }
        }

        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS plan_versions (" +
                        "effectiveFrom TEXT PRIMARY KEY NOT NULL, " +
                        "targetCalories INTEGER NOT NULL, " +
                        "targetProteinG INTEGER NOT NULL, " +
                        "targetCarbsG INTEGER NOT NULL, " +
                        "targetFatG INTEGER NOT NULL, " +
                        "calorieZoneLowerBound INTEGER NOT NULL, " +
                        "calorieZoneUpperBound INTEGER NOT NULL, " +
                        "createdAt TEXT NOT NULL)",
                )
            }
        }

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Steps provenance: lets a sync know whether a populated value was the user's
                // manual entry (keep it) or a prior Health Connect read (safe to refresh).
                db.execSQL("ALTER TABLE daily_logs ADD COLUMN stepsSource TEXT")
            }
        }

        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Additive: new local usage-tracking table only. No existing table is touched.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS usage_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestampEpochMs INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "label TEXT)",
                )
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS catalog_foods (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "source TEXT NOT NULL, " +
                    "sourceVersion TEXT NOT NULL, " +
                    "externalId TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "servingName TEXT NOT NULL, " +
                    "calories INTEGER NOT NULL, " +
                    "proteinG REAL NOT NULL, " +
                    "carbsG REAL NOT NULL, " +
                    "fatG REAL NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_catalog_foods_source_externalId " +
                    "ON catalog_foods (source, externalId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_catalog_foods_name ON catalog_foods (name)"
                )
            }
        }

        /**
         * Seeds the default meal slots on a FRESH install. The seed used to live only in
         * [MIGRATION_1_2], so a new device — which creates the schema at the current version and never
         * runs migrations — started with an empty Food Log and no slot cards (P1-21). onCreate fires
         * only on first creation, so upgraders (which already have the slots from MIGRATION_1_2) are
         * never double-seeded. Exposed so the same seed is exercised by tests.
         */
        internal val SEED_DEFAULT_SLOTS: RoomDatabase.Callback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Meal 1', 0)")
                db.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Lunch', 1)")
                db.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Dinner', 2)")
            }
        }

        fun create(context: Context): RecompDatabase = Room.databaseBuilder(
            context.applicationContext,
            RecompDatabase::class.java,
            "recomp_tracker.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
            .addCallback(SEED_DEFAULT_SLOTS)
            .build()
    }
}
