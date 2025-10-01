package com.example.meydantestapp.utils

import com.google.firebase.Timestamp

/**
 * Normalises raw Firestore date fields for project screens and prepares display strings.
 */
object ProjectDateFormatter {

    data class Result(
        val startTimestamp: Timestamp?,
        val endTimestamp: Timestamp?,
        val startDisplay: String,
        val endDisplay: String
    )

    private const val DEFAULT_PLACEHOLDER = "غير محدد"

    /**
     * Converts Firestore raw values into timestamps and formatted strings.
     *
     * @param startRaw value read from Firestore for `startDate`.
     * @param endRaw value read from Firestore for `endDate`.
     * @param placeholder text to display when the timestamp cannot be resolved.
     */
    fun resolve(startRaw: Any?, endRaw: Any?, placeholder: String = DEFAULT_PLACEHOLDER): Result {
        val start = FirestoreTimestampConverter.fromAny(startRaw)
        val end = FirestoreTimestampConverter.fromAny(endRaw)
        return Result(
            startTimestamp = start,
            endTimestamp = end,
            startDisplay = start?.toDisplayDateString().orPlaceholder(placeholder),
            endDisplay = end?.toDisplayDateString().orPlaceholder(placeholder)
        )
    }

    /**
     * Convenience helper for working directly with document data maps.
     */
    fun fromDataMap(data: Map<String, Any?>, placeholder: String = DEFAULT_PLACEHOLDER): Result {
        return resolve(data["startDate"], data["endDate"], placeholder)
    }

    private fun String?.orPlaceholder(placeholder: String): String {
        return this?.takeIf { it.isNotBlank() } ?: placeholder
    }
}
