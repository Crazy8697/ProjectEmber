package com.projectember.mobile.import

import java.time.LocalDate

sealed interface ImportDomain {
    val domainName: String
}

data class ImportEnvelope(
    val schemaVersion: Int = 1,
    val domain: String,
    val items: List<Map<String, Any?>>
)

data class ImportPreviewItem(
    val raw: Map<String, Any?>,
    val valid: Boolean,
    val reason: String? = null,
    val isDuplicate: Boolean = false
)

data class ImportSummary(
    val domain: String,
    val total: Int,
    val valid: Int,
    val invalid: Int,
    val duplicates: Int,
    val errors: List<String> = emptyList()
)

