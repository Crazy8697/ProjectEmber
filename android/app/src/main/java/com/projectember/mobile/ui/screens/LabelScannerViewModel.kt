package com.projectember.mobile.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import com.projectember.mobile.data.label.NutritionLabelParser
import com.projectember.mobile.data.label.NutritionParseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class LabelScanState {
    /** Camera live, waiting for user to tap Capture. */
    data object Idle : LabelScanState()
    /** Image captured, OCR + parsing in progress. */
    data object Capturing : LabelScanState()
    /** OCR + parsing complete — shows result panel. */
    data class ParseResult(val result: NutritionParseResult, val rawText: String) : LabelScanState()
    /** OCR threw an error or the ML Kit model could not process the image. */
    data class ParseFailed(val reason: String = "") : LabelScanState()
}

class LabelScannerViewModel : ViewModel() {

    private val _scanState = MutableStateFlow<LabelScanState>(LabelScanState.Idle)
    val scanState: StateFlow<LabelScanState> = _scanState.asStateFlow()

    /** Called immediately after the user taps Capture. */
    fun onCapturing() {
        _scanState.value = LabelScanState.Capturing
    }

    /**
     * Called when ML Kit returns recognized text.
     * [rawText] may be empty when OCR found nothing in the image.
     */
    fun onTextRecognized(rawText: String) {
        if (rawText.isBlank()) {
            Log.w("LabelScannerVM", "OCR returned blank text — showing ParseFailed")
            _scanState.value = LabelScanState.ParseFailed(reason = "No text detected in image")
            return
        }
        val result = NutritionLabelParser.parse(rawText)
        Log.d("LabelScannerVM", "NUTRITION_PARSE_RESULT: $result")
        _scanState.value = LabelScanState.ParseResult(result, rawText)
    }

    /**
     * Called when the camera or ML Kit threw an exception.
     * Shows the failure reason in the UI to aid debugging.
     */
    fun onOcrFailed(reason: String) {
        Log.e("LabelScannerVM", "OCR_FAILED: $reason")
        _scanState.value = LabelScanState.ParseFailed(reason = reason)
    }

    /** Reset to idle so the user can scan again. */
    fun onScanAgain() {
        _scanState.value = LabelScanState.Idle
    }
}
