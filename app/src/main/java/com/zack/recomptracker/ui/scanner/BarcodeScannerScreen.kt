package com.zack.recomptracker.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import com.zack.recomptracker.ui.liquidglass.LiquidGlassButton
import com.zack.recomptracker.ui.liquidglass.LiquidPrimaryButton
import com.zack.recomptracker.ui.liquidglass.LiquidSecondaryButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.zack.recomptracker.data.local.entity.SavedFoodEntity
import com.zack.recomptracker.ui.component.MessageKind
import com.zack.recomptracker.ui.component.MessageText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun BarcodeScannerScreen(
    viewModel: BarcodeScannerViewModel,
    slotId: Long?,
    slotName: String,
    pickerMode: Boolean = false,
    onFoodPicked: (String) -> Unit = {},
    onBack: () -> Unit,
) {
    LaunchedEffect(slotId, slotName, pickerMode) { viewModel.init(slotId, slotName, pickerMode) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.scanState) {
        when (val s = state.scanState) {
            is ScanState.Logged -> onBack()
            is ScanState.PickedForRecipe -> {
                onFoodPicked(Json.encodeToString<SavedFoodEntity>(s.food))
                onBack()
            }
            else -> Unit
        }
    }

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission && state.scanState is ScanState.Scanning) {
            CameraPreview(onBarcodeDetected = viewModel::onBarcodeDetected)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }

        if (!hasCameraPermission) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        "Camera permission is required to scan barcodes.",
                        color = Color.White,
                    )
                    LiquidPrimaryButton(
                        text = "Grant Permission",
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    )
                }
            }
        }

        if (state.scanState is ScanState.Scanning) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 260.dp, height = 160.dp)
                            .border(
                                width = 2.dp,
                                color = Color.White.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp),
                            ),
                    )
                    Text(
                        "Point at a barcode",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                    )
                }
            }
        }

        IconButton(
            onClick = onBack,
            enabled = state.scanState !is ScanState.ShowingSuccess,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        when (val scanState = state.scanState) {
            is ScanState.Loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text("Looking up product…", color = Color.White)
                    }
                }
            }
            is ScanState.NotFound -> {
                ScanErrorOverlay(
                    message = "Product not found in Open Food Facts.",
                    onRetry = viewModel::resetScan,
                )
            }
            is ScanState.NetworkError -> {
                ScanErrorOverlay(
                    message = "Network error. Check your connection.",
                    onRetry = viewModel::resetScan,
                )
            }
            is ScanState.ProductFound -> {
                ModalBottomSheet(
                    onDismissRequest = viewModel::resetScan,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    ProductFoundSheet(
                        state = scanState,
                        message = state.message,
                        pickerMode = pickerMode,
                        onAmountChanged = viewModel::onAmountChanged,
                        onLogModeChanged = viewModel::onLogModeChanged,
                        onLogAndSave = viewModel::confirmLogAndSave,
                        onLogOnly = viewModel::confirmLog,
                        onCancel = viewModel::resetScan,
                    )
                }
            }
            is ScanState.ShowingSuccess -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34d399),
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            scanState.message,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ScanErrorOverlay(message: String, onRetry: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(message, color = Color.White, fontWeight = FontWeight.SemiBold)
            LiquidPrimaryButton(text = "Scan Again", onClick = onRetry)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFoundSheet(
    state: ScanState.ProductFound,
    message: String?,
    pickerMode: Boolean = false,
    onAmountChanged: (String) -> Unit,
    onLogModeChanged: (LogMode) -> Unit,
    onLogAndSave: () -> Unit,
    onLogOnly: () -> Unit,
    onCancel: () -> Unit,
) {
    val product = state.product
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!product.hasCompleteData) {
            Text(
                "Warning: some nutritional data is missing — check values before logging.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MacroChip("Kcal", product.caloriesPer100g.toString())
            MacroChip("Protein", "${product.proteinPer100g}g")
            MacroChip("Carbs", "${product.carbsPer100g}g")
            MacroChip("Fat", "${product.fatPer100g}g")
        }
        Text("per 100g", fontSize = 11.sp, color = Color(0xFF6b7280))
        if (product.servingGrams != null) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.logMode == LogMode.GRAMS,
                    onClick = { onLogModeChanged(LogMode.GRAMS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Grams") },
                )
                SegmentedButton(
                    selected = state.logMode == LogMode.SERVING,
                    onClick = { onLogModeChanged(LogMode.SERVING) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Serving") },
                )
            }
        }
        val fieldLabel = if (state.logMode == LogMode.SERVING && product.servingGrams != null) {
            val gramsInt = product.servingGrams.toInt()
            if (product.servingName != null) "Servings (1 serving = ${gramsInt}g · ${product.servingName})"
            else "Servings (1 serving = ${gramsInt}g)"
        } else {
            "Amount (grams)"
        }
        OutlinedTextField(
            value = state.amountInput,
            onValueChange = onAmountChanged,
            label = { Text(fieldLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        MessageText(message, MessageKind.ERROR)
        if (pickerMode) {
            LiquidPrimaryButton(text = "Add to Recipe", onClick = onLogOnly)
        } else {
            LiquidPrimaryButton(text = "Log & Save to Library", onClick = onLogAndSave)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!pickerMode) LiquidSecondaryButton(text = "Log Only", onClick = onLogOnly, modifier = Modifier.weight(1f))
            LiquidSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(label, fontSize = 11.sp, color = Color(0xFF6b7280))
    }
}

@Composable
private fun CameraPreview(onBarcodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    // Shared state so DisposableEffect can unbind what LaunchedEffect bound
    val closed = remember { AtomicBoolean(false) }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            closed.set(true)
            providerHolder[0]?.unbindAll()
            barcodeScanner.close()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        if (closed.get()) return@LaunchedEffect
        providerHolder[0] = provider

        val preview = Preview.Builder().build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            if (!closed.get()) {
                processImageProxy(barcodeScanner, imageProxy, onBarcodeDetected)
            } else {
                imageProxy.close()
            }
        }

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis,
        )
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onDetected: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull()?.rawValue?.let { onDetected(it) }
        }
        .addOnCompleteListener { imageProxy.close() }
}
