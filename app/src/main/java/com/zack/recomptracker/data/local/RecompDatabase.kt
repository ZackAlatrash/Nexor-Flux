package com.zack.recomptracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zack.recomptracker.data.local.dao.DailyLogDao
import com.zack.recomptracker.data.local.dao.CatalogFoodDao
import com.zack.recomptracker.data.local.dao.MealEntryDao
import com.zack.recomptracker.data.local.dao.MealSlotDao
import com.zack.recomptracker.data.local.dao.PerformanceDao
import com.zack.recomptracker.data.local.dao.RecipeDao
import com.zack.recomptracker.data.local.dao.SavedFoodDao
import com.zack.recomptracker.data.local.dao.SavedMealDao
import com.zack.recomptracker.data.local.dao.WeeklyReviewDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.CatalogFoodEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity

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
    ],
    version = 7,
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

        fun create(context: Context): RecompDatabase = Room.databaseBuilder(
            context.applicationContext,
            RecompDatabase::class.java,
            "recomp_tracker.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()
    }
}
