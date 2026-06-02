package com.zack.recomptracker.ui.scanner

import com.zack.recomptracker.core.time.DateProvider
import com.zack.recomptracker.data.repository.BarcodeProduct
import com.zack.recomptracker.data.repository.BarcodeRepository
import com.zack.recomptracker.data.repository.BarcodeResult
import com.zack.recomptracker.data.repository.LogRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class BarcodeScannerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var barcodeRepository: BarcodeRepository
    private lateinit var logRepository: LogRepository
    private val dateProvider = object : DateProvider {
        override fun today(): LocalDate = LocalDate.of(2026, 6, 1)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        barcodeRepository = mock()
        logRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = BarcodeScannerViewModel(barcodeRepository, logRepository, dateProvider)

    @Test
    fun `initial state is Scanning`() {
        val vm = viewModel()
        assertTrue(vm.uiState.value.scanState is ScanState.Scanning)
    }

    @Test
    fun `onBarcodeDetected transitions to Loading then ProductFound`() = runTest {
        val product = BarcodeProduct("Hagelslag", 408, 5.3, 72.4, 11.2, null, null, true)
        whenever(barcodeRepository.lookupBarcode("8710522955")).thenReturn(BarcodeResult.Found(product))
        val vm = viewModel()
        vm.init(slotId = 1L, slotName = "Breakfast")

        vm.onBarcodeDetected("8710522955")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value.scanState
        assertTrue(state is ScanState.ProductFound)
        assertEquals("Hagelslag", (state as ScanState.ProductFound).product.name)
    }

    @Test
    fun `onBarcodeDetected transitions to NotFound when barcode unknown`() = runTest {
        whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.NotFound)
        val vm = viewModel()
        vm.onBarcodeDetected("0000000000")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.scanState is ScanState.NotFound)
    }

    @Test
    fun `onBarcodeDetected transitions to NetworkError on failure`() = runTest {
        whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.NetworkError)
        val vm = viewModel()
        vm.onBarcodeDetected("1111111111")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.scanState is ScanState.NetworkError)
    }

    @Test
    fun `second identical barcode is ignored while in Loading state`() = runTest {
        whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.NotFound)
        val vm = viewModel()
        vm.onBarcodeDetected("8710522955")
        // state is now Loading; second call with same barcode should be a no-op
        vm.onBarcodeDetected("8710522955")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.scanState is ScanState.NotFound)
    }

    @Test
    fun `resetScan returns to Scanning and clears lastScannedBarcode`() = runTest {
        whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.NotFound)
        val vm = viewModel()
        vm.onBarcodeDetected("1234")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.resetScan()

        assertTrue(vm.uiState.value.scanState is ScanState.Scanning)
    }

    @Test
    fun `onAmountChanged updates ProductFound state`() = runTest {
        val product = BarcodeProduct("Test", 100, 10.0, 20.0, 5.0, null, null, true)
        whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.Found(product))
        val vm = viewModel()
        vm.onBarcodeDetected("abc")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAmountChanged("150")

        val state = vm.uiState.value.scanState as ScanState.ProductFound
        assertEquals("150", state.amountInput)
    }

    @Test
    fun `confirmLogAndSave logs food and saves to library then transitions to Logged`() = runTest {
        val product = BarcodeProduct("Hagelslag", 408, 5.3, 72.4, 11.2, "15g", 15.0, true)
        whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.Found(product))
        whenever(logRepository.addMealToSlot(any(), any())).thenReturn(1L)
        whenever(logRepository.saveFood(any())).thenReturn(1L)
        val vm = viewModel()
        vm.init(slotId = 1L, slotName = "Breakfast")
        vm.onBarcodeDetected("8710522955")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.confirmLogAndSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.scanState is ScanState.Logged)
        org.mockito.kotlin.verify(logRepository).addMealToSlot(any(), any())
        org.mockito.kotlin.verify(logRepository).saveFood(any())
    }

    @Test
    fun `confirmLogAndSave shows message for invalid amount`() = runTest {
        val product = BarcodeProduct("Hagelslag", 408, 5.3, 72.4, 11.2, null, null, true)
        whenever(barcodeRepository.lookupBarcode(any())).thenReturn(BarcodeResult.Found(product))
        val vm = viewModel()
        vm.onBarcodeDetected("8710522955")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAmountChanged("0")
        vm.confirmLogAndSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.scanState is ScanState.ProductFound)
        assertEquals("Enter a valid amount (min 1g).", vm.uiState.value.message)
    }
}
