package com.projectember.mobile.import

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val repo: ImportRepository
) : ViewModel() {

    private val _state = MutableStateFlow<JsonImportState>(JsonImportState.Idle)
    val state: StateFlow<JsonImportState> = _state

    private val moshi = Moshi.Builder().build()
    // Optional domain suggested by the caller (e.g., history screen context).
    private var suggestedDomain: String? = null

    fun setSuggestedDomain(domain: String?) {
        suggestedDomain = domain?.takeIf { it.isNotBlank() }
    }

    fun parseAndPreview(rawJson: String) {
        viewModelScope.launch {
            try {
                // Try parsing as an array of items first, then as an object envelope.
                val listAdapter = moshi.adapter<List<Map<String, Any?>>>(Types.newParameterizedType(List::class.java, Map::class.java))
                val maybeList = try {
                    listAdapter.fromJson(rawJson)
                } catch (_: Exception) {
                    null
                }

                if (maybeList != null) {
                    buildPreview(domain = "unknown", items = maybeList)
                    return@launch
                }

                // Fallback: parse as object/envelope
                val adapter = moshi.adapter(Map::class.java)
                val parsed = try {
                    @Suppress("UNCHECKED_CAST")
                    val map = adapter.fromJson(rawJson) as? Map<String, Any?>
                    map
                } catch (_: Exception) {
                    null
                }

                if (parsed == null) {
                    _state.value = JsonImportState.Error("Unable to parse JSON as object or array")
                    return@launch
                }

                // look for envelope keys
                val domain = parsed["domain"]?.toString() ?: parsed["type"]?.toString() ?: "unknown"
                val itemsRaw = (parsed["items"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }
                if (itemsRaw != null) {
                    buildPreview(domain = domain, items = itemsRaw)
                } else {
                    val dataNode = parsed["data"]
                    if (dataNode is List<*>) {
                        val dataItems = dataNode.mapNotNull { it as? Map<String, Any?> }
                        buildPreview(domain = domain, items = dataItems)
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
        // If the payload didn't specify a domain, allow the caller-suggested domain to be used.
        val effectiveDomain = if (domain == "unknown") suggestedDomain ?: "unknown" else domain
        val normalizedDomain = effectiveDomain.lowercase()
        val preview = items.map { item ->
            val validation = ImportValidator.validate(normalizedDomain, item)
            if (validation.valid) {
                ImportPreviewItem(raw = item, valid = true)
            } else {
                ImportPreviewItem(raw = item, valid = false, reason = validation.reason)
            }
        }
        val total = preview.size
        val validCount = preview.count { it.valid }
        val invalidCount = total - validCount
        val summary = ImportSummary(domain = effectiveDomain, total = total, valid = validCount, invalid = invalidCount, duplicates = 0)
        _state.value = JsonImportState.Preview(domain = effectiveDomain, items = preview, summary = summary)
    }

    fun reset() {
        _state.value = JsonImportState.Idle
    }

    fun importConfirmed(includeDuplicates: Boolean = false) {
        viewModelScope.launch {
            val current = _state.value
            if (current is JsonImportState.Preview) {
                _state.value = JsonImportState.Importing
                try {
                    val itemsToWrite = current.items.filter { it.valid }.map { it.raw }
                    val result = withContext(Dispatchers.IO) {
                        repo.importItems(current.domain, itemsToWrite, includeDuplicates)
                    }
                    _state.value = JsonImportState.Done(result)
                } catch (e: Exception) {
                    _state.value = JsonImportState.Error("Import failed: ${e.message}")
                }
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(JsonImportViewModel::class.java)) {
                        val repo = ImportRepositoryProvider.provide(appContext)
                        return JsonImportViewModel(repo) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class ${'$'}modelClass")
                }
            }
        }
    }
}


