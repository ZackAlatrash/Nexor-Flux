package com.zack.recomptracker.data.local

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [RecompDatabase.MIGRATION_12_13] — the plan_versions table that backs plan history.
 *
 * The DB uses `exportSchema = false`, so no historical schema JSON exists and a canonical
 * MigrationTestHelper test isn't possible. Instead we run the migration's SQL directly against a
 * fresh in-memory SQLite database (via Robolectric's Android SQLite, so it stays a pure-JVM
 * `testDebugUnitTest`) and assert the table and its columns match what [PlanVersionEntity] expects.
 */
// Use a stub Application: the real one initialises the Android keystore, which isn't available
// under Robolectric. This test only needs a Context to open an in-memory SQLite database.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RecompDatabaseMigrationTest {

    private fun openEmptyDatabase(): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext())
                .name(null) // null name → in-memory database, discarded when closed
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build(),
        )

    @Test
    fun `migration creates plan_versions with the expected columns`() {
        val helper = openEmptyDatabase()
        try {
            val db = helper.writableDatabase

            RecompDatabase.MIGRATION_12_13.migrate(db)

            // The table now exists.
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='plan_versions'").use {
                assertTrue("plan_versions table should exist after MIGRATION_12_13", it.moveToFirst())
            }

            // Every column the entity needs is present, NOT NULL, and effectiveFrom is the PK.
            val notNull = mutableMapOf<String, Boolean>()
            val primaryKeys = mutableSetOf<String>()
            db.query("PRAGMA table_info(plan_versions)").use { c ->
                val nameIdx = c.getColumnIndexOrThrow("name")
                val notNullIdx = c.getColumnIndexOrThrow("notnull")
                val pkIdx = c.getColumnIndexOrThrow("pk")
                while (c.moveToNext()) {
                    val name = c.getString(nameIdx)
                    notNull[name] = c.getInt(notNullIdx) == 1
                    if (c.getInt(pkIdx) > 0) primaryKeys.add(name)
                }
            }

            val expectedColumns = setOf(
                "effectiveFrom",
                "targetCalories",
                "targetProteinG",
                "targetCarbsG",
                "targetFatG",
                "calorieZoneLowerBound",
                "calorieZoneUpperBound",
                "createdAt",
            )
            assertEquals(expectedColumns, notNull.keys)
            assertTrue("all plan_versions columns are NOT NULL", notNull.values.all { it })
            assertEquals(setOf("effectiveFrom"), primaryKeys)
        } finally {
            helper.close()
        }
    }

    @Test
    fun `migration is re-runnable (CREATE TABLE IF NOT EXISTS)`() {
        val helper = openEmptyDatabase()
        try {
            val db = helper.writableDatabase
            RecompDatabase.MIGRATION_12_13.migrate(db)
            // A second run must not throw — guards against a dropped IF NOT EXISTS.
            RecompDatabase.MIGRATION_12_13.migrate(db)
        } finally {
            helper.close()
        }
    }

    @Test
    fun `migration 13 to 14 adds nullable stepsSource column to daily_logs`() {
        val helper = openEmptyDatabase()
        try {
            val db = helper.writableDatabase
            // A pre-v14 daily_logs (no stepsSource), with one legacy row.
            db.execSQL(
                "CREATE TABLE daily_logs (" +
                    "date TEXT PRIMARY KEY NOT NULL, bodyWeightKg REAL, waistCm REAL, " +
                    "waistSkinfoldMm REAL, steps INTEGER, sleepHours REAL, energyScore INTEGER, " +
                    "hungerScore INTEGER, sorenessScore INTEGER, trained INTEGER NOT NULL DEFAULT 0, " +
                    "notes TEXT NOT NULL DEFAULT '')",
            )
            db.execSQL("INSERT INTO daily_logs (date, steps) VALUES ('2026-06-20', 5000)")

            RecompDatabase.MIGRATION_13_14.migrate(db)

            // Column now exists and is nullable (legacy rows get NULL = unknown source).
            var hasStepsSource = false
            var stepsSourceNotNull = true
            db.query("PRAGMA table_info(daily_logs)").use { c ->
                val nameIdx = c.getColumnIndexOrThrow("name")
                val notNullIdx = c.getColumnIndexOrThrow("notnull")
                while (c.moveToNext()) {
                    if (c.getString(nameIdx) == "stepsSource") {
                        hasStepsSource = true
                        stepsSourceNotNull = c.getInt(notNullIdx) == 1
                    }
                }
            }
            assertTrue("stepsSource column should exist after MIGRATION_13_14", hasStepsSource)
            assertTrue("stepsSource should be nullable", !stepsSourceNotNull)

            // The legacy row survives with a NULL stepsSource.
            db.query("SELECT stepsSource FROM daily_logs WHERE date='2026-06-20'").use {
                assertTrue(it.moveToFirst())
                assertTrue("legacy row has null stepsSource", it.isNull(0))
            }
        } finally {
            helper.close()
        }
    }
}
