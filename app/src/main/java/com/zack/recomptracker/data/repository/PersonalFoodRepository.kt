package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.SavedFoodDao
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.domain.foodimport.FoodImportCandidate
import com.zack.recomptracker.domain.foodimport.FoodImportSummary
import com.zack.recomptracker.domain.foodimport.PersonalFoodMerger
import com.zack.recomptracker.domain.foodimport.PersonalFoodsJsonCodec

class PersonalFoodRepository(
    private val savedFoodDao: SavedFoodDao,
    private val jsonCodec: PersonalFoodsJsonCodec = PersonalFoodsJsonCodec(),
) {
    suspend fun getPersonalFoods(): List<FoodImportCandidate> =
        savedFoodDao.getAll().map(SavedFoodEntity::toImportCandidate)

    /** Removes every personal/saved food. Returns how many were deleted. */
    suspend fun clearPersonalFoods(): Int {
        val count = savedFoodDao.getAll().size
        savedFoodDao.deleteAll()
        return count
    }

    suspend fun createPersonalFoodsJson(): String = jsonCodec.encode(
        getPersonalFoods(),
    )

    suspend fun mergePersonalFoodsJson(rawJson: String): FoodImportSummary =
        mergePersonalFoods(jsonCodec.decode(rawJson).foods)

    suspend fun mergePersonalFoods(candidates: List<FoodImportCandidate>): FoodImportSummary {
        val merge = PersonalFoodMerger.newFoods(
            existing = getPersonalFoods(),
            incoming = candidates,
        )
        savedFoodDao.insertAll(merge.foodsToInsert.map(FoodImportCandidate::toSavedFoodEntity))
        return FoodImportSummary(
            importedCount = merge.foodsToInsert.size,
            duplicateCount = merge.duplicateCount,
        )
    }
}

fun SavedFoodEntity.toImportCandidate(): FoodImportCandidate = FoodImportCandidate(
    name = name,
    servingName = servingName,
    calories = calories,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
)

fun FoodImportCandidate.toSavedFoodEntity(): SavedFoodEntity = SavedFoodEntity(
    name = name.trim(),
    servingName = servingName.trim(),
    calories = calories,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
)
