package com.zack.recomptracker.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.MealSlotEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P1-21 remediation: onCreate only seeds NEW installs, so devices whose schema was first created at
 * v>=2 (already at v15 with an empty meal_slots) need to self-heal at app start. MealSlotInitializer
 * seeds the defaults when the table is empty, idempotently.
 *
 * The in-memory DB is built WITHOUT the onCreate callback, so meal_slots starts empty — exactly the
 * broken-install shape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class MealSlotInitializerTest {

    private lateinit var database: RecompDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RecompDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `seedIfEmpty seeds the default slots on an empty table`() = runTest {
        MealSlotInitializer(database.mealSlotDao()).seedIfEmpty()

        val slots = database.mealSlotDao().getAll()
        assertEquals(listOf("Meal 1", "Lunch", "Dinner"), slots.map { it.name })
        assertEquals(listOf(0, 1, 2), slots.map { it.sortOrder })
    }

    @Test
    fun `seedIfEmpty leaves an already-populated table untouched`() = runTest {
        database.mealSlotDao().insert(MealSlotEntity(name = "Custom", sortOrder = 0))

        MealSlotInitializer(database.mealSlotDao()).seedIfEmpty()

        assertEquals(listOf("Custom"), database.mealSlotDao().getAll().map { it.name })
    }

    @Test
    fun `a fresh install seeded by onCreate is not double-seeded by the startup initializer`() = runTest {
        // The real production combination: onCreate seeds 3, then the startup seedIfEmpty runs.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seeded = Room.inMemoryDatabaseBuilder(context, RecompDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(RecompDatabase.SEED_DEFAULT_SLOTS)
            .build()
        try {
            MealSlotInitializer(seeded.mealSlotDao()).seedIfEmpty()
            assertEquals(3, seeded.mealSlotDao().getAll().size) // still 3, not 6
        } finally {
            seeded.close()
        }
    }
}
