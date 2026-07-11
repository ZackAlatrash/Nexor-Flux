package com.zack.recomptracker.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zack.recomptracker.data.local.dao.DailyLogDao
import com.zack.recomptracker.data.local.entity.DailyLogEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Review P2-7: the coach's log_metric must write a single metric with a partial column UPDATE, not a
 * whole-row read-modify-write, so it can't clobber the other columns a concurrent check-in save
 * wrote. Real in-memory Room so the actual SQL column semantics are exercised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class DailyLogPartialMetricTest {

    private lateinit var database: RecompDatabase
    private lateinit var dao: DailyLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RecompDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.dailyLogDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `upsertMetric updates only its own column and preserves the siblings`() = runTest {
        val date = "2026-07-11"
        dao.upsert(
            DailyLogEntity(date = date, bodyWeightKg = 80.0, waistCm = 90.0, energyScore = 5, notes = "leg day"),
        )

        dao.upsertMetric(date, "weight_kg", 81.0)

        val row = dao.getByDate(date)!!
        assertEquals(81.0, row.bodyWeightKg!!, 0.0) // the metric was updated
        assertEquals(90.0, row.waistCm!!, 0.0) // siblings preserved — NOT clobbered
        assertEquals(5, row.energyScore)
        assertEquals("leg day", row.notes)
    }

    @Test
    fun `upsertMetric creates a fresh row when none exists for the date`() = runTest {
        val date = "2026-07-11"
        assertNull(dao.getByDate(date))

        dao.upsertMetric(date, "energy_score", 7.0)

        val row = dao.getByDate(date)!!
        assertEquals(7, row.energyScore)
        assertNull(row.bodyWeightKg) // only the set metric is populated
        assertEquals("", row.notes)
        assertEquals(false, row.trained)
    }

    @Test
    fun `upsertMetric stores scores as their integer part (truncates)`() = runTest {
        val date = "2026-07-11"
        // A fractional value genuinely exercises truncation (a rounding impl would store 7).
        dao.upsertMetric(date, "soreness_score", 6.7)
        assertEquals(6, dao.getByDate(date)!!.sorenessScore)
    }

    @Test
    fun `upsertMetric ignores an unknown metric and creates no row`() = runTest {
        val date = "2026-07-11"
        dao.upsertMetric(date, "mood", 5.0)
        assertNull(dao.getByDate(date)) // a bad name must not leave a stray empty row
    }
}
