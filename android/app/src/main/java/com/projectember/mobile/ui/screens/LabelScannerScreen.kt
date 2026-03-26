package com.projectember.mobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.projectember.mobile.data.label.NutritionParseResult
import com.projectember.mobile.ui.theme.KetoAccent
import java.util.concurrent.Executors

private const val TAG = "LabelScanner"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelScannerScreen(
    viewModel: LabelScannerViewModel,
    onNavigateBack: () -> Unit,
    onUseDraft: (NutritionParseResult) -> Unit
) {
    val context = LocalContext.current
    val scanState by viewModel.scanState.collectAsState()

    var hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission.value = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission.value) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Capture executor — one background thread for ImageCapture callbacks.
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { captureExecutor.shutdown() } }

    // Slot to hold the ImageCapture use-case once the camera is bound.
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }

    val handleCapture: () -> Unit = capture@{
        val imageCapture = imageCaptureRef.value ?: return@capture
        viewModel.onCapturing()

        imageCapture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val mediaImage = image.image
                if (mediaImage == null) {
                    Log.w(TAG, "onCaptureSuccess: image.image was null")
                    image.close()
                    viewModel.onTextRecognized("")
                    return
                }
                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    image.imageInfo.rotationDegrees
                )
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        image.close()
                        Log.d(TAG, "OCR_TEXT_CAPTURED: ${visionText.text.take(200)}")
                        viewModel.onTextRecognized(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        image.close()
                        Log.e(TAG, "OCR failed", e)
                        viewModel.onTextRecognized("")
                    }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                viewModel.onTextRecognized("")
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Nutrition Label") },
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
        if (!hasCameraPermission.value) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Camera access is needed to scan nutrition labels.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow Camera")
                }
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Camera preview always runs underneath — avoids re-binding on state changes.
            LabelCameraPreview(
                modifier = Modifier.fillMaxSize(),
                onImageCaptureReady = { imageCaptureRef.value = it }
            )

            when (val state = scanState) {
                is LabelScanState.Idle -> {
                    LabelGuideOverlay(
                        onCapture = handleCapture,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is LabelScanState.Capturing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            Text("Reading label…", color = Color.White)
                        }
                    }
                }

                is LabelScanState.ParseResult -> {
                    ParseResultPanel(
                        result = state.result,
                        onUseDraft = { onUseDraft(state.result) },
                        onScanAgain = viewModel::onScanAgain,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    )
                }

                is LabelScanState.ParseFailed -> {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Could not read label text.",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Try again with better lighting, or hold the camera steady with the Nutrition Facts panel fully visible.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = viewModel::onScanAgain) {
                                Text("Try Again")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelCameraPreview(
    modifier: Modifier = Modifier,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraPreview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
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
                        imageCapture
                    )
                    onImageCaptureReady(imageCapture)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind camera", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

@Composable
private fun LabelGuideOverlay(
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Text(
                text = "Point at the Nutrition Facts panel, then tap Capture",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        // Framing guide
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.85f)
                .aspectRatio(0.65f)
                .border(2.dp, KetoAccent, RoundedCornerShape(8.dp))
        )

        Button(
            onClick = onCapture,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.65f),
            colors = ButtonDefaults.buttonColors(containerColor = KetoAccent)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Capture Label")
        }
    }
}

@Composable
private fun ParseResultPanel(
    result: NutritionParseResult,
    onUseDraft: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasAnyData = listOf(
        result.calories, result.proteinG, result.fatG, result.totalCarbsG,
        result.fiberG, result.sodiumMg, result.potassiumMg, result.magnesiumMg
    ).any { it != null }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (hasAnyData) {
                Text(
                    "Nutrition Label Read",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (result.missingFields.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Partial read — some fields were not detected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))

                result.servingAmount?.let {
                    ParsedRow(
                        label = "Serving",
                        value = "${fmtNum(it)} ${result.servingUnit ?: ""}".trim()
                    )
                }
                result.calories?.let { ParsedRow("Calories", "${fmtNum(it)} kcal") }
                result.proteinG?.let { ParsedRow("Protein", "${fmtNum(it)} g") }
                result.fatG?.let { ParsedRow("Fat", "${fmtNum(it)} g") }
                result.totalCarbsG?.let { ParsedRow("Total Carbs", "${fmtNum(it)} g") }
                result.fiberG?.let { ParsedRow("Fiber", "${fmtNum(it)} g") }
                result.sodiumMg?.let { ParsedRow("Sodium", "${fmtNum(it)} mg") }
                result.potassiumMg?.let { ParsedRow("Potassium", "${fmtNum(it)} mg") }
                result.magnesiumMg?.let { ParsedRow("Magnesium", "${fmtNum(it)} mg") }

                if (result.missingFields.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Not detected: ${result.missingFields.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onUseDraft,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = KetoAccent)
                ) {
                    Text("Use This Draft")
                }
            } else {
                Text(
                    "No nutrition data found",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "The label could not be read clearly. Ensure the Nutrition Facts panel is " +
                        "fully in frame and well lit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = onScanAgain,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan Again")
            }
        }
    }
}

@Composable
private fun ParsedRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun fmtNum(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)
