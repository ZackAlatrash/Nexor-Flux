package com.zack.recomptracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zack.recomptracker.data.local.dao.DailyLogDao
import com.zack.recomptracker.data.local.dao.MealEntryDao
import com.zack.recomptracker.data.local.dao.MealSlotDao
import com.zack.recomptracker.data.local.dao.PerformanceDao
import com.zack.recomptracker.data.local.dao.SavedFoodDao
import com.zack.recomptracker.data.local.dao.SavedMealDao
import com.zack.recomptracker.data.local.dao.WeeklyReviewDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import com.zack.recomptracker.data.local.entity.LiftPerformanceEntity
import com.zack.recomptracker.data.local.entity.MealEntryEntity
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.data.local.entity.SavedMealEntity
import com.zack.recomptracker.data.local.entity.WeeklyReviewEntity

@Database(
    entities = [
        DailyLogEntity::class,
        MealEntryEntity::class,
        SavedFoodEntity::class,
        SavedMealEntity::class,
        LiftPerformanceEntity::class,
        WeeklyReviewEntity::class,
        MealSlotEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class RecompDatabase : RoomDatabase() {
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun savedFoodDao(): SavedFoodDao
    abstract fun savedMealDao(): SavedMealDao
    abstract fun performanceDao(): PerformanceDao
    abstract fun weeklyReviewDao(): WeeklyReviewDao
    abstract fun mealSlotDao(): MealSlotDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS meal_slots (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "sort_order INTEGER NOT NULL)"
                )
                database.execSQL("ALTER TABLE meal_entries ADD COLUMN slotId INTEGER")
                // Seed three default slots
                database.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Meal 1', 0)")
                database.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Lunch', 1)")
                database.execSQL("INSERT INTO meal_slots (name, sort_order) VALUES ('Dinner', 2)")
            }
        }

        fun create(context: Context): RecompDatabase = Room.databaseBuilder(
            context.applicationContext,
            RecompDatabase::class.java,
            "recomp_tracker.db",
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
