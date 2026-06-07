package com.zack.recomptracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.local.entity.RecipeWithIngredientsDb
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY id ASC")
    abstract fun observeAllWithIngredients(): Flow<List<RecipeWithIngredientsDb>>

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY id ASC")
    abstract suspend fun getAllWithIngredients(): List<RecipeWithIngredientsDb>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    abstract suspend fun getWithIngredients(recipeId: Long): RecipeWithIngredientsDb?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    abstract suspend fun updateRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    abstract suspend fun deleteRecipeById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertIngredient(ingredient: RecipeIngredientEntity): Long

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    abstract suspend fun deleteIngredientsByRecipeId(recipeId: Long)

    @Transaction
    open suspend fun replaceIngredients(recipeId: Long, ingredients: List<RecipeIngredientEntity>) {
        deleteIngredientsByRecipeId(recipeId)
        ingredients.forEachIndexed { index, ingredient ->
            insertIngredient(ingredient.copy(recipeId = recipeId, sortOrder = index, id = 0))
        }
    }
}
