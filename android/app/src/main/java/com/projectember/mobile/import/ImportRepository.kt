package com.projectember.mobile.import

import kotlinx.coroutines.delay

interface ImportRepository {
    suspend fun importItems(domain: String, items: List<Map<String, Any?>>): ImportSummary
}

/**
 * A conservative stub repository used by the import ViewModel until real repositories are wired.
 * It simulates duplicate detection by building a simple signature from date/time/value fields.
 */
class StubImportRepository : ImportRepository {
    private val existingSignatures = mutableSetOf<String>()

    override suspend fun importItems(domain: String, items: List<Map<String, Any?>>): ImportSummary {
        // Simulate some work
        delay(200)
        var valid = 0
        var invalid = 0
        var duplicates = 0
        val errors = mutableListOf<String>()

        for ((idx, item) in items.withIndex()) {
            val date = item["date"]?.toString()
            val time = item["time"]?.toString() ?: "00:00"
            val value = item["value"] ?: item["value1"] ?: item["value1"]
            if (date == null || value == null) {
                invalid++
                errors.add("item[$idx] missing required fields")
                continue
            }
            val sig = "$date|$time|$value"
            if (existingSignatures.contains(sig)) {
                duplicates++
                continue
            }
            // pretend to write
            existingSignatures.add(sig)
            valid++
        }

        return ImportSummary(domain = domain, total = items.size, valid = valid, invalid = invalid, duplicates = duplicates, errors = errors)
    }
}

