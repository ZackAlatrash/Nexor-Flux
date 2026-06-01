package com.zack.recomptracker.ui.foodlibrary

import com.zack.recomptracker.core.model.MacroTotals
import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.preferences.PlanPreferences
import com.zack.recomptracker.data.repository.BarcodeProduct
import com.zack.recomptracker.data.repository.BarcodeRepository
import com.zack.recomptracker.data.repository.DayLog
import com.zack.recomptracker.data.repository.FoodCatalogRepository
import com.zack.recomptracker.data.repository.LogRepository
import com.zack.recomptracker.data.repository.PlanRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class FoodLibraryViewModelOffSearchTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var barcodeRepository: BarcodeRepository
    private lateinit var logRepository: LogRepository
    private lateinit var planRepository: PlanRepository
    private lateinit var foodCatalogRepository: FoodCatalogRepository
    private val dateProvider = object : DateProvider {
        override fun today(): LocalDate = LocalDate.of(2026, 6, 1)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        barcodeRepository = mock()
        logRepository = mock()
        planRepository = mock()
        foodCatalogRepository = mock()
        whenever(logRepository.observeSavedFoods()).thenReturn(flowOf(emptyList()))
        whenever(foodCatalogRepository.observeCatalogFoods()).thenReturn(flowOf(emptyList()))
        whenever(logRepository.observeSavedMeals()).thenReturn(flowOf(emptyList()))
        whenever(planRepository.preferences).thenReturn(flowOf(PlanPreferences()))
        whenever(logRepository.observeDay(any())).thenReturn(flowOf(
            DayLog(
                date = LocalDate.of(2026, 6, 1),
                dailyLog = null,
                meals = emptyList(),
                totals = MacroTotals(0, 0.0, 0.0, 0.0),
            )
        ))
        whenever(logRepository.observeRecentFoods()).thenReturn(flowOf(emptyList()))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = FoodLibraryViewModel(
        logRepository = logRepository,
        planRepository = planRepository,
        dateProvider = dateProvider,
        foodCatalogRepository = foodCatalogRepository,
        barcodeRepository = barcodeRepository,
    )

    @Test
    fun `searchOff populates offSearchResults on success`() = runTest {
        val products = listOf(BarcodeProduct("Hagelslag", 408, 5.3, 72.4, 11.2, null, null, true))
        whenever(barcodeRepository.searchFoods("hagelslag")).thenReturn(products)

        val vm = viewModel()
        vm.init(slotId = null, slotName = "")
        vm.onQueryChanged("hagelslag")
        vm.searchOff()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.offSearchLoading)
        assertEquals(1, state.offSearchResults.size)
        assertEquals("Hagelslag", state.offSearchResults[0].name)
    }

    @Test
    fun `searchOff with blank query sets message without calling repository`() = runTest {
        val vm = viewModel()
        vm.init(slotId = null, slotName = "")
        vm.onQueryChanged("")
        vm.searchOff()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Enter a search term.", vm.uiState.value.message)
        assertFalse(vm.uiState.value.offSearchLoading)
        assertTrue(vm.uiState.value.offSearchResults.isEmpty())
    }

    @Test
    fun `searchOff sets message when no results found`() = runTest {
        whenever(barcodeRepository.searchFoods(any())).thenReturn(emptyList())

        val vm = viewModel()
        vm.init(slotId = null, slotName = "")
        vm.onQueryChanged("xyzzy123")
        vm.searchOff()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.offSearchResults.isEmpty())
        assertEquals("No products found for 'xyzzy123'.", vm.uiState.value.message)
    }

    @Test
    fun `filteredFoods returns OFF results with Open Food Facts source label when category is OFF`() = runTest {
        val products = listOf(BarcodeProduct("Stroopwafel", 450, 4.5, 68.0, 16.0, null, null, true))
        whenever(barcodeRepository.searchFoods(any())).thenReturn(products)

        val vm = viewModel()
        vm.init(slotId = null, slotName = "")
        vm.onCategoryChanged(FoodCategory.OFF)
        vm.onQueryChanged("stroopwafel")
        vm.searchOff()
        testDispatcher.scheduler.advanceUntilIdle()

        val items = vm.uiState.value.filteredFoods
        assertEquals(1, items.size)
        assertEquals("Stroopwafel", items[0].food.name)
        assertEquals("Open Food Facts", items[0].sourceLabel)
    }
}
