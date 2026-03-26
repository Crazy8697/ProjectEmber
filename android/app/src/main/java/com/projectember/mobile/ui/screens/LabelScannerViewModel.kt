package com.projectember.mobile.ui.screens

import androidx.lifecycle.ViewModel
import com.projectember.mobile.data.label.NutritionLabelParser
import com.projectember.mobile.data.label.NutritionParseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class LabelScanState {
    /** Camera live, waiting for user to capture. */
    data object Idle : LabelScanState()
    /** Image captured, OCR in progress. */
    data object Capturing : LabelScanState()
    /** OCR + parsing complete. */
    data class ParseResult(val result: NutritionParseResult, val rawText: String) : LabelScanState()
    /** OCR returned no text or threw. */
    data object ParseFailed : LabelScanState()
}

class LabelScannerViewModel : ViewModel() {

    private val _scanState = MutableStateFlow<LabelScanState>(LabelScanState.Idle)
    val scanState: StateFlow<LabelScanState> = _scanState.asStateFlow()

    /** Called when the user taps Capture and the image is being processed. */
    fun onCapturing() {
        _scanState.value = LabelScanState.Capturing
    }

    /** Called when ML Kit returns recognized text (may be blank on failure). */
    fun onTextRecognized(rawText: String) {
        if (rawText.isBlank()) {
            _scanState.value = LabelScanState.ParseFailed
            return
        }
        val result = NutritionLabelParser.parse(rawText)
        _scanState.value = LabelScanState.ParseResult(result, rawText)
    }

    /** Reset to idle so the user can scan again. */
    fun onScanAgain() {
        _scanState.value = LabelScanState.Idle
    }
}
