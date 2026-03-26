package com.projectember.mobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
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

    // ── Permission ────────────────────────────────────────────────────────────
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

    // ── ML Kit recognizer — created once on main thread, closed on dispose ───
    // BUG FIX: was previously created inside onCaptureSuccess (background thread).
    // TextRecognition.getClient() must be called on the main thread.
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) { onDispose { recognizer.close() } }

    // ── Capture executor — background thread for ImageCapture callbacks ───────
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { captureExecutor.shutdown() } }

    // ── Camera readiness ──────────────────────────────────────────────────────
    // Slot filled when the camera is bound and ImageCapture is ready.
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    // Derived: true once imageCaptureRef is populated.
    val isCameraReady = imageCaptureRef.value != null

    // ── Capture handler ───────────────────────────────────────────────────────
    val handleCapture: () -> Unit = capture@{
        val imageCapture = imageCaptureRef.value
        if (imageCapture == null) {
            // Camera not ready yet — log so it shows in Logcat
            Log.w(TAG, "CAPTURE_ATTEMPTED: camera not ready yet — imageCaptureRef is null")
            return@capture
        }
        Log.d(TAG, "CAPTURE_STARTED: requesting image from ImageCapture")
        viewModel.onCapturing()

        imageCapture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                Log.d(TAG, "IMAGE_CAPTURED: format=${image.format} rotation=${image.imageInfo.rotationDegrees}")
                val mediaImage = image.image
                if (mediaImage == null) {
                    Log.e(TAG, "IMAGE_CAPTURE_NULL: image.image returned null — cannot run OCR")
                    image.close()
                    viewModel.onTextRecognized("")
                    return
                }
                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    image.imageInfo.rotationDegrees
                )
                // recognizer was created on the main thread above — safe to call process() from here
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text
                        image.close()
                        Log.d(TAG, "OCR_TEXT_CAPTURED (${text.length} chars):\n$text")
                        viewModel.onTextRecognized(text)
                    }
                    .addOnFailureListener { e ->
                        image.close()
                        Log.e(TAG, "OCR_FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
                        viewModel.onOcrFailed(e.message ?: e.javaClass.simpleName)
                    }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "IMAGE_CAPTURE_ERROR: ${exception.imageCaptureError} — ${exception.message}", exception)
                viewModel.onOcrFailed("Camera capture error: ${exception.message}")
            }
        })
    }

    // ── UI ────────────────────────────────────────────────────────────────────
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
        if (!hasCameraPermission) {
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
            LabelCameraPreview(
                modifier = Modifier.fillMaxSize(),
                onImageCaptureReady = { capture ->
                    Log.d(TAG, "CAMERA_READY: ImageCapture use-case bound")
                    imageCaptureRef.value = capture
                }
            )

            when (val state = scanState) {
                is LabelScanState.Idle -> {
                    LabelGuideOverlay(
                        cameraReady = isCameraReady,
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
                        rawOcrText = state.rawText,
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
                            if (state.reason.isNotBlank()) {
                                Text(
                                    "Reason: ${state.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                "Try again with better lighting, or ensure the Nutrition Facts panel is fully visible and steady.",
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
                    Log.e(TAG, "CAMERA_BIND_FAILED: ${e.message}", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

@Composable
private fun LabelGuideOverlay(
    cameraReady: Boolean,
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
                text = if (cameraReady)
                    "Point at the Nutrition Facts panel, then tap Capture"
                else
                    "Starting camera…",
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
                .border(
                    width = 2.dp,
                    color = if (cameraReady) KetoAccent else Color.Gray,
                    shape = RoundedCornerShape(8.dp)
                )
        )

        Button(
            onClick = onCapture,
            enabled = cameraReady,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.65f),
            colors = ButtonDefaults.buttonColors(containerColor = KetoAccent)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (cameraReady) "Capture Label" else "Starting…")
        }
    }
}

@Composable
private fun ParseResultPanel(
    result: NutritionParseResult,
    rawOcrText: String,
    onUseDraft: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasAnyData = listOf(
        result.calories, result.proteinG, result.fatG, result.totalCarbsG,
        result.fiberG, result.sodiumMg, result.potassiumMg, result.magnesiumMg
    ).any { it != null }

    var showRawText by remember { mutableStateOf(false) }

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
                    ParsedRow("Serving", "${fmtNum(it)} ${result.servingUnit ?: ""}".trim())
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
                    Spacer(Modifier.height(6.dp))
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
                    "OCR captured text but could not extract nutrition values. " +
                        "Check the raw text below to see what was read.",
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

            // ── Debug: raw OCR text ───────────────────────────────────────────
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Raw OCR text (${rawOcrText.length} chars)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.TextButton(onClick = { showRawText = !showRawText }) {
                    Text(if (showRawText) "Hide" else "Show")
                }
            }
            if (showRawText) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (rawOcrText.isBlank()) "(empty)" else rawOcrText,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
