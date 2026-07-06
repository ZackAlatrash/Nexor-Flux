package com.zack.recomptracker.data.local.entity

/**
 * Provenance of a day's logged step count, stored in [DailyLogEntity.stepsSource].
 *
 * Drives the reconciliation rule in `reconcileSteps`: a [MANUAL] value is the user's deliberate
 * entry and is never clobbered by a sync, whereas a [HEALTH_CONNECT] (or unknown/legacy null)
 * value may be refreshed from Health Connect.
 */
object StepsSource {
    const val MANUAL = "manual"
    const val HEALTH_CONNECT = "health_connect"
}
