package com.zack.recomptracker.data.repository

import androidx.room.withTransaction
import com.zack.recomptracker.data.local.RecompDatabase
import com.zack.recomptracker.data.local.entity.CatalogFoodEntity
import com.zack.recomptracker.domain.foodimport.FoodImportSummary
import com.zack.recomptracker.domain.foodimport.NevoCsvParser
import java.io.InputStream
import kotlinx.coroutines.flow.Flow

class FoodCatalogRepository(
    private val database: RecompDatabase,
    private val nevoCsvParser: NevoCsvParser = NevoCsvParser(),
) {
    private val catalogFoodDao = database.catalogFoodDao()

    fun observeCatalogFoods(): Flow<List<CatalogFoodEntity>> = catalogFoodDao.observeAll()

    fun observeNevoSourceVersion(): Flow<String?> = catalogFoodDao.observeSourceVersion(NEVO_SOURCE)

    suspend fun replaceNevoCatalog(inputStream: InputStream): FoodImportSummary {
        val parsedFoods = nevoCsvParser.parse(inputStream)
        val entities = parsedFoods.map { food ->
            CatalogFoodEntity(
                source = NEVO_SOURCE,
                sourceVersion = SUPPORTED_NEVO_VERSION,
                externalId = requireNotNull(food.externalId),
                name = food.name,
                servingName = food.servingName,
                calories = food.calories,
                proteinG = food.proteinG,
                carbsG = food.carbsG,
                fatG = food.fatG,
            )
        }
        database.withTransaction {
            catalogFoodDao.deleteBySource(NEVO_SOURCE)
            catalogFoodDao.insertAll(entities)
        }
        return FoodImportSummary(importedCount = entities.size)
    }

    suspend fun removeNevoCatalog() {
        catalogFoodDao.deleteBySource(NEVO_SOURCE)
    }

    companion object {
        const val NEVO_SOURCE = "NEVO"
        const val SUPPORTED_NEVO_VERSION = "2025/9.0"
    }
}
