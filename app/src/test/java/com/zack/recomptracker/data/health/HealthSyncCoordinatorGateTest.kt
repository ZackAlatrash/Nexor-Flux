package com.zack.recomptracker.data.health

import com.zack.recomptracker.data.health.HealthSyncCoordinator.Companion.isAutoSyncDue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSyncCoordinatorGateTest {

    private val interval = HealthSyncCoordinator.DEFAULT_MIN_INTERVAL_MS
    private val now = 10_000_000L

    @Test
    fun `not due when health connect is disabled`() {
        assertFalse(isAutoSyncDue(enabled = false, lastSyncEpochMs = null, nowEpochMs = now, minIntervalMs = interval))
    }

    @Test
    fun `due when enabled and never synced`() {
        assertTrue(isAutoSyncDue(enabled = true, lastSyncEpochMs = null, nowEpochMs = now, minIntervalMs = interval))
    }

    @Test
    fun `not due when last sync is within the debounce window`() {
        val last = now - (interval - 1)
        assertFalse(isAutoSyncDue(enabled = true, lastSyncEpochMs = last, nowEpochMs = now, minIntervalMs = interval))
    }

    @Test
    fun `due when last sync is older than the debounce window`() {
        val last = now - interval
        assertTrue(isAutoSyncDue(enabled = true, lastSyncEpochMs = last, nowEpochMs = now, minIntervalMs = interval))
    }
}
