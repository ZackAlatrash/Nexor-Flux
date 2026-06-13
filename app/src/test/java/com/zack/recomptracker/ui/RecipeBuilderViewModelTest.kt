package com.zack.recomptracker.ui

import androidx.lifecycle.SavedStateHandle
import com.zack.recomptracker.ai.RecipeNamer
import com.zack.recomptracker.core.model.MacroTotals
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    private class FakeNamer(
        private val result: Result<String>,
        avail: Boolean = true,
    ) : RecipeNamer {
        override val available: StateFlow<Boolean> = MutableStateFlow(avail)
        var calls = 0
        override suspend fun generate(
            ingredients: List<RecipeIngredientEntity>,
            totals: MacroTotals,
        ): Result<String> { calls++; return result }
    }

    private fun seedHandle(json: String) = SavedStateHandle(mapOf("seedIngredients" to json))

    @Test
    fun `addIngredient appends to list`() {
        val vm = RecipeBuilderViewModel(stubRepo(), FakeNamer(Result.success("X")), SavedStateHandle())
        vm.addIngredient(ingredient("Rice"))
        vm.addIngredient(ingredient("Milk"))
        assertEquals(2, vm.uiState.value.ingredients.size)
        assertEquals("Milk", vm.uiState.value.ingredients[1].name)
    }

    @Test
    fun `removeIngredientAt removes correct index`() {
        val vm = RecipeBuilderViewModel(stubRepo(), FakeNamer(Result.success("X")), SavedStateHandle())
        vm.addIngredient(ingredient("A"))
        vm.addIngredient(ingredient("B"))
        vm.addIngredient(ingredient("C"))
        vm.removeIngredientAt(1)
        assertEquals(listOf("A", "C"), vm.uiState.value.ingredients.map { it.name })
    }

    @Test
    fun `save with empty name sets error`() {
        val vm = RecipeBuilderViewModel(stubRepo(), FakeNamer(Result.success("X")), SavedStateHandle())
        vm.addIngredient(ingredient())
        vm.save()
        assertTrue(vm.uiState.value.message?.isNotBlank() == true)
        assertEquals(false, vm.navigateBack.value)
    }

    @Test
    fun `save with no ingredients sets error`() {
        val vm = RecipeBuilderViewModel(stubRepo(), FakeNamer(Result.success("X")), SavedStateHandle())
        vm.onNameChanged("Rice Pudding")
        vm.save()
        assertTrue(vm.uiState.value.message?.isNotBlank() == true)
    }

    @Test
    fun `save valid recipe triggers navigate back`() = runTest {
        val vm = RecipeBuilderViewModel(stubRepo(), FakeNamer(Result.success("X")), SavedStateHandle())
        vm.onNameChanged("Rice Pudding")
        vm.addIngredient(ingredient("Rice"))
        vm.save()
        assertEquals(true, vm.navigateBack.value)
    }

    @Test
    fun `generateName fills name on success`() = runTest {
        val vm = RecipeBuilderViewModel(stubRepo(), FakeNamer(Result.success("Gains Goblin Stew")), SavedStateHandle())
        vm.addIngredient(ingredient("Oats"))
        vm.generateName()
        assertEquals("Gains Goblin Stew", vm.uiState.value.name)
        assertEquals(false, vm.uiState.value.isGeneratingName)
    }

    @Test
    fun `generateName with no ingredients is a no-op`() = runTest {
        val namer = FakeNamer(Result.success("X"))
        val vm = RecipeBuilderViewModel(stubRepo(), namer, SavedStateHandle())
        vm.generateName()
        assertEquals(0, namer.calls)
        assertEquals("", vm.uiState.value.name)
    }

    @Test
    fun `generateName failure sets error message and leaves name untouched`() = runTest {
        val vm = RecipeBuilderViewModel(
            stubRepo(), FakeNamer(Result.failure(RuntimeException("boom"))), SavedStateHandle(),
        )
        vm.onNameChanged("My Name")
        vm.addIngredient(ingredient("Oats"))
        vm.generateName()
        assertEquals("My Name", vm.uiState.value.name)
        assertTrue(vm.uiState.value.message?.isNotBlank() == true)
        assertEquals(false, vm.uiState.value.isGeneratingName)
    }

    @Test
    fun `seed ingredients are loaded for a new recipe`() {
        val json = Json.encodeToString(listOf(ingredient("Chicken"), ingredient("Fries")))
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
        val vm = RecipeBuilderViewModel(stubRepo(), FakeNamer(Result.success("X")), seedHandle(encoded))
        assertEquals(listOf("Chicken", "Fries"), vm.uiState.value.ingredients.map { it.name })
    }
}
