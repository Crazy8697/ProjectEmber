package com.projectember.mobile.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

sealed interface JsonImportState {
    object Idle : JsonImportState
    data class Preview(
        val domain: String,
        val items: List<ImportPreviewItem>,
        val summary: ImportSummary
    ) : JsonImportState
    object Importing : JsonImportState
    data class Done(val summary: ImportSummary) : JsonImportState
    data class Error(val message: String) : JsonImportState
}

class JsonImportViewModel(
    private val repo: ImportRepository = StubImportRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<JsonImportState>(JsonImportState.Idle)
    val state: StateFlow<JsonImportState> = _state

    private val moshi = Moshi.Builder().build()

    fun parseAndPreview(rawJson: String) {
        viewModelScope.launch {
            try {
                // Expect either envelope or raw array/object
                val adapter = moshi.adapter(Map::class.java)
                val parsed = adapter.fromJson(rawJson) as? Map<String, Any?>
                if (parsed == null) {
                    // try array of items
                    val listAdapter = moshi.adapter<List<Map<String, Any?>>>(Types.newParameterizedType(List::class.java, Map::class.java))
                    val list = listAdapter.fromJson(rawJson)
                    if (list == null) {
                        _state.value = JsonImportState.Error("Unable to parse JSON as object or array")
                        return@launch
                    }
                    buildPreview(domain = "unknown", items = list)
                } else {
                    // look for envelope keys
                    val domain = parsed["domain"]?.toString() ?: parsed["type"]?.toString() ?: "unknown"
                    val itemsRaw = parsed["items"] as? List<Map<String, Any?>>
                    if (itemsRaw != null) {
                        buildPreview(domain = domain, items = itemsRaw)
                    } else if (parsed["data"] is List<*>) {
                        @Suppress("UNCHECKED_CAST")
                        buildPreview(domain = domain, items = parsed["data"] as List<Map<String, Any?>>)
                    } else {
                        // maybe the object itself is an item
                        buildPreview(domain = domain, items = listOf(parsed))
                    }
                }
            } catch (e: Exception) {
                _state.value = JsonImportState.Error("JSON parse error: ${e.message}")
            }
        }
    }

    private fun buildPreview(domain: String, items: List<Map<String, Any?>>) {
        val preview = items.map { item ->
            // basic validation: needs date and value or value1
            val date = item["date"]?.toString()
            val v1 = item["value"] ?: item["value1"]
            if (date == null || v1 == null) {
                ImportPreviewItem(raw = item, valid = false, reason = "missing date or value")
            } else {
                ImportPreviewItem(raw = item, valid = true)
            }
        }
        val total = preview.size
        val validCount = preview.count { it.valid }
        val invalidCount = total - validCount
        val summary = ImportSummary(domain = domain, total = total, valid = validCount, invalid = invalidCount, duplicates = 0)
        _state.value = JsonImportState.Preview(domain = domain, items = preview, summary = summary)
    }

    fun importConfirmed() {
        viewModelScope.launch {
            val current = _state.value
            if (current is JsonImportState.Preview) {
                _state.value = JsonImportState.Importing
                try {
                    val itemsToWrite = current.items.filter { it.valid }.map { it.raw }
                    val result = repo.importItems(current.domain, itemsToWrite)
                    _state.value = JsonImportState.Done(result)
                } catch (e: Exception) {
                    _state.value = JsonImportState.Error("Import failed: ${e.message}")
                }
            }
        }
    }
}

