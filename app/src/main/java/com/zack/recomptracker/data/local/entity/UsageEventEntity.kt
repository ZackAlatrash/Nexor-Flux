package com.zack.recomptracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single locally-recorded usage event. Fully private, single-user, offline — no external analytics.
 *
 * @param type an event-type string from [com.zack.recomptracker.data.usage.UsageEvents] (a small
 *   closed vocabulary; stored as text so the table needs no schema change to add an event).
 * @param label optional discriminator carried by the event — the insight-card kind for INSIGHT_*,
 *   the tab name for TAB_VIEW, the slot/card kind for the today-slot events; null when not applicable.
 */
@Entity(tableName = "usage_events")
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMs: Long,
    val type: String,
    val label: String? = null,
)
