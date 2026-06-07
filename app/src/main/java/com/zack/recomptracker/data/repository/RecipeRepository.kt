package com.zack.recomptracker.data.repository

import com.zack.recomptracker.data.local.dao.RecipeDao
import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.local.entity.RecipeWithIngredientsDb
import com.zack.recomptracker.domain.food.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class RecipeRepository(private val recipeDao: RecipeDao) {

    open fun observeAll(): Flow<List<RecipeWithIngredients>> =
        recipeDao.observeAllWithIngredients().map { list -> list.map { it.toDomain() } }

    open suspend fun getById(id: Long): RecipeWithIngredients? =
        recipeDao.getWithIngredients(id)?.toDomain()

    open suspend fun saveRecipe(name: String, ingredients: List<RecipeIngredientEntity>): Long {
        val recipeId = recipeDao.insertRecipe(RecipeEntity(name = name.trim()))
        recipeDao.replaceIngredients(recipeId, ingredients)
        return recipeId
    }

    open suspend fun updateRecipe(recipeId: Long, name: String, ingredients: List<RecipeIngredientEntity>) {
        recipeDao.updateRecipe(RecipeEntity(id = recipeId, name = name.trim()))
        recipeDao.replaceIngredients(recipeId, ingredients)
    }

    open suspend fun deleteRecipe(recipeId: Long) {
        recipeDao.deleteRecipeById(recipeId)
    }
}

private fun RecipeWithIngredientsDb.toDomain() = RecipeWithIngredients(
    recipe = recipe,
    ingredients = ingredients.sortedBy { it.sortOrder },
)
