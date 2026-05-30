package com.zack.recomptracker.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.CatalogFoodEntity
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RecompDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: RecompDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, RecompDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertsLogsMealsFoodsMealsAndReviews() = runBlocking {
        database.dailyLogDao().upsert(DailyLogEntity(date = "2026-05-29", bodyWeightKg = 80.2))
        database.mealEntryDao().insert(
            MealEntryEntity(
                date = "2026-05-29",
                mealType = "LUNCH",
                name = "Rice chicken",
                calories = 720,
                proteinG = 55.0,
                carbsG = 82.0,
                fatG = 16.0,
            ),
        )
        database.savedFoodDao().insert(
            SavedFoodEntity(
                name = "Greek yogurt",
                servingName = "250g",
                calories = 160,
                proteinG = 25.0,
                carbsG = 10.0,
                fatG = 2.0,
            ),
        )
        database.savedMealDao().insert(
            SavedMealEntity(
                name = "Post workout oats",
                mealType = "BREAKFAST",
                calories = 640,
                proteinG = 45.0,
                carbsG = 88.0,
                fatG = 12.0,
            ),
        )
        database.weeklyReviewDao().upsert(
            WeeklyReviewEntity(
                weekStart = "2026-05-25",
                verdict = "HOLD",
                recommendedCalorieChange = 0,
                reasonCodes = "MAINTENANCE_TREND",
                generatedAt = Instant.now().toString(),
            ),
        )
        database.catalogFoodDao().insertAll(
            listOf(
                CatalogFoodEntity(
                    source = "NEVO",
                    sourceVersion = "2025/9.0",
                    externalId = "123",
                    name = "Halfvolle melk",
                    servingName = "100g",
                    calories = 47,
                    proteinG = 3.5,
                    carbsG = 4.8,
                    fatG = 1.5,
                ),
            ),
        )

        assertEquals(1, database.dailyLogDao().getAll().size)
        assertEquals(1, database.mealEntryDao().getAll().size)
        assertEquals(1, database.savedFoodDao().getAll().size)
        assertEquals(1, database.savedMealDao().getAll().size)
        assertEquals(1, database.weeklyReviewDao().getAll().size)
        assertEquals(1, database.catalogFoodDao().getAll().size)
    }

    @Test
    fun migratesVersion2To3WithoutDroppingPersonalFoods() = runBlocking {
        val databaseName = "migration-2-3-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createVersion2Schema(db)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO saved_foods " +
                "(name, servingName, calories, proteinG, carbsG, fatG) " +
                "VALUES ('Greek yogurt', '100g', 60, 10.0, 4.0, 0.0)",
        )
        helper.close()

        val migrated = Room.databaseBuilder(context, RecompDatabase::class.java, databaseName)
            .addMigrations(RecompDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(1, migrated.savedFoodDao().getAll().size)
            assertEquals(0, migrated.catalogFoodDao().getAll().size)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migratesVersion3To4PreservingDataAndAddingColumns() = runBlocking {
        val databaseName = "migration-3-4-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(3) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createVersion2Schema(db)
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS catalog_foods (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, source TEXT NOT NULL, " +
                                    "sourceVersion TEXT NOT NULL, externalId TEXT NOT NULL, name TEXT NOT NULL, " +
                                    "servingName TEXT NOT NULL, calories INTEGER NOT NULL, proteinG REAL NOT NULL, " +
                                    "carbsG REAL NOT NULL, fatG REAL NOT NULL)",
                            )
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO saved_foods (name, servingName, calories, proteinG, carbsG, fatG) " +
                "VALUES ('Whey', '100g', 120, 24.0, 3.0, 2.0)",
        )
        helper.writableDatabase.execSQL(
            "INSERT INTO meal_entries (date, mealType, name, calories, proteinG, carbsG, fatG, slotId) " +
                "VALUES ('2026-05-30', 'FOOD_LIBRARY', 'Whey', 72, 14, 2, 1, NULL)",
        )
        helper.close()

        val migrated = Room.databaseBuilder(context, RecompDatabase::class.java, databaseName)
            .addMigrations(RecompDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val foods = migrated.savedFoodDao().getAll()
            val meals = migrated.mealEntryDao().getAll()
            assertEquals(1, foods.size)
            assertEquals(null, foods.first().householdServingName)
            assertEquals(1, meals.size)
            assertEquals(null, meals.first().amountGrams)
            assertEquals(null, meals.first().basePer100Calories)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createVersion2Schema(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS daily_logs (date TEXT NOT NULL, bodyWeightKg REAL, waistCm REAL, steps INTEGER, sleepHours REAL, energyScore INTEGER, hungerScore INTEGER, sorenessScore INTEGER, trained INTEGER NOT NULL, notes TEXT NOT NULL, PRIMARY KEY(date))")
        db.execSQL("CREATE TABLE IF NOT EXISTS meal_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, mealType TEXT NOT NULL, name TEXT NOT NULL, calories INTEGER NOT NULL, proteinG REAL NOT NULL, carbsG REAL NOT NULL, fatG REAL NOT NULL, slotId INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meal_entries_date ON meal_entries (date)")
        db.execSQL("CREATE TABLE IF NOT EXISTS saved_foods (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, servingName TEXT NOT NULL, calories INTEGER NOT NULL, proteinG REAL NOT NULL, carbsG REAL NOT NULL, fatG REAL NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS saved_meals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, mealType TEXT NOT NULL, calories INTEGER NOT NULL, proteinG REAL NOT NULL, carbsG REAL NOT NULL, fatG REAL NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS lift_performance (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, liftName TEXT NOT NULL, weight REAL NOT NULL, reps INTEGER NOT NULL, sets INTEGER NOT NULL, rir INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lift_performance_date ON lift_performance (date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lift_performance_liftName ON lift_performance (liftName)")
        db.execSQL("CREATE TABLE IF NOT EXISTS weekly_reviews (weekStart TEXT NOT NULL, verdict TEXT NOT NULL, recommendedCalorieChange INTEGER NOT NULL, reasonCodes TEXT NOT NULL, generatedAt TEXT NOT NULL, PRIMARY KEY(weekStart))")
        db.execSQL("CREATE TABLE IF NOT EXISTS meal_slots (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, sort_order INTEGER NOT NULL)")
    }
}
