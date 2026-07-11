package com.zack.recomptracker.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1-21: the default meal slots used to be seeded only in MIGRATION_1_2, so a fresh install (which
 * creates the schema at the current version and never runs migrations) had an empty meal_slots table
 * and no slot cards. The seed now also runs in RoomDatabase.Callback.onCreate.
 *
 * Real Room so onCreate actually fires.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class FreshInstallMealSlotsTest {

    private fun freshDatabase(withSeed: Boolean): RecompDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, RecompDatabase::class.java)
            .allowMainThreadQueries()
            .apply { if (withSeed) addCallback(RecompDatabase.SEED_DEFAULT_SLOTS) }
            .build()
    }

    @Test
    fun `a fresh install is seeded with the three default meal slots`() = runTest {
        val db = freshDatabase(withSeed = true)
        try {
            val slots = db.mealSlotDao().getAll()
            assertEquals(listOf("Meal 1", "Lunch", "Dinner"), slots.map { it.name })
            assertEquals(listOf(0, 1, 2), slots.map { it.sortOrder })
        } finally {
            db.close()
        }
    }

    @Test
    fun `without the seed callback a fresh install has no slots (the bug)`() = runTest {
        val db = freshDatabase(withSeed = false)
        try {
            assertEquals(emptyList<String>(), db.mealSlotDao().getAll().map { it.name })
        } finally {
            db.close()
        }
    }
}
