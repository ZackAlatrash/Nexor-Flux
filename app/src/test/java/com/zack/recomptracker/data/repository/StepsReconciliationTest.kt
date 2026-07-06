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
    fun `lower health-connect value never clobbers a manual entry`() {
        assertEquals(
            StepsReconciliation(10_000, StepsSource.MANUAL),
            reconcileSteps(existingSteps = 10_000, existingSource = StepsSource.MANUAL, healthConnectSteps = 5_000),
        )
    }

    @Test
    fun `higher health-connect value overtakes a manual entry`() {
        assertEquals(
            StepsReconciliation(9_000, StepsSource.HEALTH_CONNECT),
            reconcileSteps(existingSteps = 8_000, existingSource = StepsSource.MANUAL, healthConnectSteps = 9_000),
        )
    }

    @Test
    fun `equal health-connect value keeps the manual entry`() {
        assertEquals(
            StepsReconciliation(8_000, StepsSource.MANUAL),
            reconcileSteps(existingSteps = 8_000, existingSource = StepsSource.MANUAL, healthConnectSteps = 8_000),
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
    fun `lower health-connect value still refreshes a prior health-connect value`() {
        assertEquals(
            StepsReconciliation(2_000, StepsSource.HEALTH_CONNECT),
            reconcileSteps(existingSteps = 5_000, existingSource = StepsSource.HEALTH_CONNECT, healthConnectSteps = 2_000),
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

class ResolveSavedStepsTest {

    @Test
    fun `edited steps are saved as manual`() {
        assertEquals(
            StepsReconciliation(7_000, StepsSource.MANUAL),
            resolveSavedSteps(
                stepsEdited = true,
                inputSteps = 7_000,
                existingSteps = 5_000,
                existingSource = StepsSource.HEALTH_CONNECT,
            ),
        )
    }

    @Test
    fun `clearing the steps field removes value and source`() {
        assertEquals(
            StepsReconciliation(null, null),
            resolveSavedSteps(
                stepsEdited = true,
                inputSteps = null,
                existingSteps = 8_000,
                existingSource = StepsSource.MANUAL,
            ),
        )
    }

    @Test
    fun `untouched prefill keeps health-connect provenance`() {
        assertEquals(
            StepsReconciliation(5_000, StepsSource.HEALTH_CONNECT),
            resolveSavedSteps(
                stepsEdited = false,
                inputSteps = 5_000,
                existingSteps = 5_000,
                existingSource = StepsSource.HEALTH_CONNECT,
            ),
        )
    }

    @Test
    fun `untouched save preserves a value synced after the form was opened`() {
        assertEquals(
            StepsReconciliation(6_000, StepsSource.HEALTH_CONNECT),
            resolveSavedSteps(
                stepsEdited = false,
                inputSteps = 5_000, // stale prefill from before the sync
                existingSteps = 6_000,
                existingSource = StepsSource.HEALTH_CONNECT,
            ),
        )
    }

    @Test
    fun `untouched save keeps an earlier manual entry`() {
        assertEquals(
            StepsReconciliation(9_000, StepsSource.MANUAL),
            resolveSavedSteps(
                stepsEdited = false,
                inputSteps = 9_000,
                existingSteps = 9_000,
                existingSource = StepsSource.MANUAL,
            ),
        )
    }

    @Test
    fun `untouched save on an empty day stays empty`() {
        assertEquals(
            StepsReconciliation(null, null),
            resolveSavedSteps(
                stepsEdited = false,
                inputSteps = null,
                existingSteps = null,
                existingSource = null,
            ),
        )
    }
}
