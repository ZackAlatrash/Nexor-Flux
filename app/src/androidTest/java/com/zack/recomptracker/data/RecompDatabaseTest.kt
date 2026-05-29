package com.zack.recomptracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.data.local.RecompDatabase
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
    private lateinit var database: RecompDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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

        assertEquals(1, database.dailyLogDao().getAll().size)
        assertEquals(1, database.mealEntryDao().getAll().size)
        assertEquals(1, database.savedFoodDao().getAll().size)
        assertEquals(1, database.savedMealDao().getAll().size)
        assertEquals(1, database.weeklyReviewDao().getAll().size)
    }
}
