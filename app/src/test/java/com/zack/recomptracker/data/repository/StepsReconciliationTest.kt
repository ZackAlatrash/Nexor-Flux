package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.entity.StepsSource
import org.junit.Assert.assertEquals
import org.junit.Test

class StepsReconciliationTest {

    @Test
    fun `no health-connect steps keeps existing value and source`() {
        assertEquals(
            StepsReconciliation(8_000, StepsSource.MANUAL),
            reconcileSteps(existingSteps = 8_000, existingSource = StepsSource.MANUAL, healthConnectSteps = null),
        )
    }

    @Test
    fun `manual value is never clobbered by a sync`() {
        assertEquals(
            StepsReconciliation(10_000, StepsSource.MANUAL),
            reconcileSteps(existingSteps = 10_000, existingSource = StepsSource.MANUAL, healthConnectSteps = 5_000),
        )
    }

    @Test
    fun `health-connect value refreshes a prior health-connect value`() {
        assertEquals(
            StepsReconciliation(11_000, StepsSource.HEALTH_CONNECT),
            reconcileSteps(existingSteps = 2_000, existingSource = StepsSource.HEALTH_CONNECT, healthConnectSteps = 11_000),
        )
    }

    @Test
    fun `health-connect value refreshes a legacy null-source value`() {
        assertEquals(
            StepsReconciliation(11_000, StepsSource.HEALTH_CONNECT),
            reconcileSteps(existingSteps = 2_000, existingSource = null, healthConnectSteps = 11_000),
        )
    }

    @Test
    fun `health-connect value populates an empty day`() {
        assertEquals(
            StepsReconciliation(6_500, StepsSource.HEALTH_CONNECT),
            reconcileSteps(existingSteps = null, existingSource = null, healthConnectSteps = 6_500),
        )
    }
}
