package com.zack.recomptracker.ui

import androidx.lifecycle.SavedStateHandle
import com.zack.recomptracker.data.local.dao.RecipeDao
import com.zack.recomptracker.data.local.entity.RecipeEntity
import com.zack.recomptracker.data.local.entity.RecipeIngredientEntity
import com.zack.recomptracker.data.local.entity.RecipeWithIngredientsDb
import com.zack.recomptracker.data.repository.RecipeRepository
import com.zack.recomptracker.domain.food.RecipeWithIngredients
import com.zack.recomptracker.ui.recipes.RecipeBuilderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeBuilderViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()


    private fun ingredient(name: String = "test") = RecipeIngredientEntity(
        recipeId = 0, name = name, sortOrder = 0,
        calories = 100, proteinG = 5.0, carbsG = 15.0, fatG = 2.0,
    )

    private fun stubRepo() = object : RecipeRepository(
        recipeDao = object : RecipeDao() {
            override fun observeAllWithIngredients(): Flow<List<RecipeWithIngredientsDb>> =
                flowOf(emptyList())
            override suspend fun getAllWithIngredients(): List<RecipeWithIngredientsDb> =
                emptyList()
            override suspend fun getWithIngredients(recipeId: Long): RecipeWithIngredientsDb? =
                null
            override suspend fun insertRecipe(recipe: RecipeEntity): Long = 0L
            override suspend fun updateRecipe(recipe: RecipeEntity) {}
            override suspend fun deleteRecipeById(id: Long) {}
            override suspend fun insertIngredient(ingredient: RecipeIngredientEntity): Long = 0L
            override suspend fun deleteIngredientsByRecipeId(recipeId: Long) {}
        },
    ) {
        override fun observeAll(): Flow<List<RecipeWithIngredients>> = flowOf(emptyList())
        override suspend fun getById(id: Long): RecipeWithIngredients? = null
        override suspend fun saveRecipe(name: String, ingredients: List<RecipeIngredientEntity>): Long = 99L
        override suspend fun updateRecipe(recipeId: Long, name: String, ingredients: List<RecipeIngredientEntity>) {}
        override suspend fun deleteRecipe(recipeId: Long) {}
    }

    @Test
    fun `addIngredient appends to list`() {
        val vm = RecipeBuilderViewModel(stubRepo(), SavedStateHandle())
        vm.addIngredient(ingredient("Rice"))
        vm.addIngredient(ingredient("Milk"))
        assertEquals(2, vm.uiState.value.ingredients.size)
        assertEquals("Milk", vm.uiState.value.ingredients[1].name)
    }

    @Test
    fun `removeIngredientAt removes correct index`() {
        val vm = RecipeBuilderViewModel(stubRepo(), SavedStateHandle())
        vm.addIngredient(ingredient("A"))
        vm.addIngredient(ingredient("B"))
        vm.addIngredient(ingredient("C"))
        vm.removeIngredientAt(1)
        assertEquals(listOf("A", "C"), vm.uiState.value.ingredients.map { it.name })
    }

    @Test
    fun `save with empty name sets error`() {
        val vm = RecipeBuilderViewModel(stubRepo(), SavedStateHandle())
        vm.addIngredient(ingredient())
        vm.save()
        assertTrue(vm.uiState.value.message?.isNotBlank() == true)
        assertEquals(false, vm.navigateBack.value)
    }

    @Test
    fun `save with no ingredients sets error`() {
        val vm = RecipeBuilderViewModel(stubRepo(), SavedStateHandle())
        vm.onNameChanged("Rice Pudding")
        vm.save()
        assertTrue(vm.uiState.value.message?.isNotBlank() == true)
    }

    @Test
    fun `save valid recipe triggers navigate back`() = runTest {
        val vm = RecipeBuilderViewModel(stubRepo(), SavedStateHandle())
        vm.onNameChanged("Rice Pudding")
        vm.addIngredient(ingredient("Rice"))
        vm.save()
        assertEquals(true, vm.navigateBack.value)
    }
}
