package com.projectember.mobile.data.barcode

/**
 * Utilities for normalizing and cleaning ingredient/product names.
 *
 * - [normalize] produces a stable lowercase key for duplicate detection.
 * - [cleanProductName] produces a readable display name stripped of label spam.
 */
object NameNormalizer {

    /**
     * Produce a normalized key for duplicate detection and search.
     *
     * Steps: lowercase → strip punctuation → trim → collapse spaces.
     *
     * Example: "Chicken Breast - Tyson (Frozen)" → "chicken breast tyson frozen"
     */
    fun normalize(name: String): String =
        name
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")

    /**
     * Clean a raw product label name into a readable title-case display name.
     *
     * Removes:
     * - Trailing junk: "!!!", "(PACK OF N)", repeated punctuation, marketing suffixes
     * - ALL-CAPS words (likely brand spam) when the name contains mixed-case content too
     *
     * Converts to title case.
     */
    fun cleanProductName(raw: String): String {
        var s = raw.trim()

        // Remove common label noise patterns
        s = s.replace(Regex("\\(PACK OF \\d+\\)", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("\\(\\d+ PACK\\)", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("[!]{2,}"), "")          // repeated exclamation marks
        s = s.replace(Regex("[?]{2,}"), "")
        s = s.replace(Regex("[*]{2,}"), "")
        s = s.replace(Regex("\\s*[-–—]+\\s*$"), "") // trailing dashes
        s = s.trim()

        // Convert to title case
        s = s.lowercase().split(" ").joinToString(" ") { word ->
            if (word.isNotEmpty()) word[0].uppercaseChar() + word.substring(1) else word
        }

        // Collapse any double spaces left by removals
        s = s.replace(Regex("\\s+"), " ").trim()

        return s
    }
}
