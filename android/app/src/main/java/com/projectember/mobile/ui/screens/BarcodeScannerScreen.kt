package com.projectember.mobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import com.projectember.mobile.ui.theme.KetoAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    viewModel: BarcodeScannerViewModel,
    onNavigateBack: () -> Unit,
    onBarcodeFound: (ingredientId: Int) -> Unit,
    onBarcodeNotFound: (barcode: String) -> Unit
) {
    val context = LocalContext.current
    val scanState by viewModel.scanState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Barcode") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!hasCameraPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Camera permission is required to scan barcodes.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            } else {
                CameraPreviewContent(
                    onBarcodeDetected = viewModel::onBarcodeDetected,
                    modifier = Modifier.fillMaxSize()
                )

                when (val state = scanState) {
                    is BarcodeScannerViewModel.ScanState.Found -> {
                        ScanResultPanel(
                            title = "Match Found",
                            body = state.ingredient.name,
                            primaryLabel = "View Ingredient",
                            onPrimary = { onBarcodeFound(state.ingredient.id) },
                            secondaryLabel = "Scan Again",
                            onSecondary = viewModel::reset,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                    is BarcodeScannerViewModel.ScanState.NotFound -> {
                        ScanResultPanel(
                            title = "No Match",
                            body = "Barcode: ${state.barcode}\nNot in your ingredient index.",
                            primaryLabel = "Create Ingredient",
                            onPrimary = { onBarcodeNotFound(state.barcode) },
                            secondaryLabel = "Scan Again",
                            onSecondary = viewModel::reset,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                    is BarcodeScannerViewModel.ScanState.Idle -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(vertical = 16.dp, horizontal = 24.dp)
                        ) {
                            Text(
                                text = "Point the camera at a barcode",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanResultPanel(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = KetoAccent
            )
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = KetoAccent)
            ) {
                Text(primaryLabel)
            }
            OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                Text(secondaryLabel)
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Decode off the main thread so the UI never stalls during ZXing processing.
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    val reader = remember {
        MultiFormatReader().apply {
            setHints(mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39,
                    BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX
                ),
                // More aggressive scan — essential for real-world slightly-off-axis barcodes.
                DecodeHintType.TRY_HARDER to true
            ))
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraPreview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(executor) { imageProxy ->
                        try {
                            // toBitmap() handles YUV stride/format internally — no manual
                            // Y-plane extraction needed. Rotate via Matrix so ZXing sees
                            // an upright frame without calling rotateCounterClockwise(),
                            // which crashes when passed a Y-only (non-interleaved YUV) source.
                            val rawBitmap = imageProxy.toBitmap()
                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                            val bitmap = if (rotationDegrees != 0) {
                                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                Bitmap.createBitmap(
                                    rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                                ).also { rawBitmap.recycle() }
                            } else rawBitmap

                            val bw = bitmap.width
                            val bh = bitmap.height
                            val pixels = IntArray(bw * bh)
                            bitmap.getPixels(pixels, 0, bw, 0, 0, bw, bh)
                            bitmap.recycle()

                            val source = RGBLuminanceSource(bw, bh, pixels)
                            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                            try {
                                val result = reader.decodeWithState(binaryBitmap)
                                Log.d("BarcodeScanner", "Decoded: ${result.text} [${result.barcodeFormat}]")
                                onBarcodeDetected(result.text)
                            } catch (_: NotFoundException) {
                                // No barcode in this frame — normal, keep scanning.
                            } finally {
                                reader.reset()
                            }
                        } finally {
                            imageProxy.close()
                        }
                    }
                }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        cameraPreview,
                        imageAnalysis
                    )
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}
