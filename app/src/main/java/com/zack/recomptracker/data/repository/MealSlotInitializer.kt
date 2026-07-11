package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.MealSlotDao
import com.zack.recomptracker.data.local.entity.DEFAULT_MEAL_SLOTS

/**
 * Seeds the default meal slots when the table is empty. The slots used to be seeded only by
 * MIGRATION_1_2, so a device whose schema was first created at v>=2 (a fresh install skips
 * migrations) reached v15 with an empty meal_slots table and an empty Food Log (P1-21). onCreate
 * only fixes brand-new installs — this runs at app start and remediates already-affected devices,
 * mirroring [PlanHistoryInitializer]. Idempotent: a populated table is left untouched, so it never
 * double-seeds the onCreate slots nor an upgrader's MIGRATION_1_2 slots.
 */
class MealSlotInitializer(private val mealSlotDao: MealSlotDao) {
    suspend fun seedIfEmpty() {
        if (mealSlotDao.getAll().isNotEmpty()) return
        DEFAULT_MEAL_SLOTS.forEach { mealSlotDao.insert(it) }
    }
}
