package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zack.recomptracker.data.local.entity.UsageEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * One row per (type, label) with its occurrence count over a window — the shape the usage read-out
 * screen groups by. `label` is nullable (Room maps a NULL group key to null here).
 */
data class UsageCount(
    val type: String,
    val label: String?,
    val count: Int,
)

@Dao
interface UsageEventDao {
    @Insert
    suspend fun insert(event: UsageEventEntity)

    /** Counts grouped by (type, label) for events at or after [sinceEpochMs]. */
    @Query(
        "SELECT type, label, COUNT(*) AS count FROM usage_events " +
            "WHERE timestampEpochMs >= :sinceEpochMs GROUP BY type, label",
    )
    suspend fun countsSince(sinceEpochMs: Long): List<UsageCount>

    /** Reactive variant of [countsSince] for the read-out screen. */
    @Query(
        "SELECT type, label, COUNT(*) AS count FROM usage_events " +
            "WHERE timestampEpochMs >= :sinceEpochMs GROUP BY type, label",
    )
    fun observeCountsSince(sinceEpochMs: Long): Flow<List<UsageCount>>

    @Query("DELETE FROM usage_events")
    suspend fun deleteAll()
}
