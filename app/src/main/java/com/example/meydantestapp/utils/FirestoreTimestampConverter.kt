package com.example.meydantestapp.utils

import android.util.Log
import com.google.firebase.Timestamp
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Helper responsible for normalising Firestore date values.
 * Accepts values saved as Timestamp, java.util.Date, numbers (seconds / millis),
 * ISO strings and even the literal string "Timestamp(seconds=..., nanoseconds=...)".
 */
object FirestoreTimestampConverter {

    private const val TAG = "FsTimestampConverter"

    private val literalTimestampRegex =
        Regex("^Timestamp\\(seconds=(\\d+),\\s*nanoseconds=(\\d+)\\)$")

    private val isoPatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd"
    )

    data class ConversionResult(
        val timestamp: Timestamp?,
        val migratedFromString: Boolean,
        val originalString: String? = null
    )

    fun fromAny(raw: Any?): ConversionResult {
        return when (raw) {
            null -> ConversionResult(null, migratedFromString = false)
            is Timestamp -> ConversionResult(raw, migratedFromString = false)
            is Date -> ConversionResult(Timestamp(raw), migratedFromString = false)
            is java.sql.Timestamp -> ConversionResult(
                Timestamp(raw.time / 1000, raw.nanos),
                migratedFromString = false
            )
            is Number -> ConversionResult(fromNumber(raw), migratedFromString = false)
            is Map<*, *> -> ConversionResult(fromMap(raw), migratedFromString = false)
            is String -> fromString(raw)
            else -> {
                Log.w(TAG, "Unsupported timestamp value type: ${raw::class.java.simpleName}")
                ConversionResult(null, migratedFromString = false)
            }
        }
    }

    private fun fromNumber(number: Number): Timestamp? {
        if (number is Double || number is Float) {
            val doubleValue = number.toDouble()
            val secondsPart = doubleValue.toLong()
            val nanosPart = ((doubleValue - secondsPart) * 1_000_000_000).toInt()
            return try {
                Timestamp(secondsPart, nanosPart)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Failed to convert fractional number $doubleValue to Timestamp", e)
                null
            }
        }

        val longValue = number.toLong()
        return fromEpochGuess(longValue)
    }

    private fun fromMap(map: Map<*, *>): Timestamp? {
        val seconds = (map["seconds"] as? Number)?.toLong()
        val nanoseconds = (map["nanoseconds"] as? Number)?.toInt() ?: 0
        return if (seconds != null) {
            try {
                Timestamp(seconds, nanoseconds)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid map timestamp seconds=$seconds nanos=$nanoseconds")
                null
            }
        } else {
            null
        }
    }

    private fun fromString(value: String): ConversionResult {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return ConversionResult(null, migratedFromString = false)
        }

        literalTimestampRegex.matchEntire(trimmed)?.let { matchResult ->
            val seconds = matchResult.groupValues[1].toLongOrNull()
            val nanos = matchResult.groupValues[2].toIntOrNull() ?: 0
            if (seconds != null) {
                return ConversionResult(Timestamp(seconds, nanos), migratedFromString = true, originalString = trimmed)
            }
        }

        trimmed.toLongOrNull()?.let { numeric ->
            val ts = fromEpochGuess(numeric)
            if (ts != null) {
                return ConversionResult(ts, migratedFromString = true, originalString = trimmed)
            }
        }

        parseIsoDate(trimmed)?.let { parsed ->
            return ConversionResult(Timestamp(parsed), migratedFromString = true, originalString = trimmed)
        }

        Log.w(TAG, "Unable to parse date string: $trimmed")
        return ConversionResult(null, migratedFromString = true, originalString = trimmed)
    }

    private fun parseIsoDate(value: String): Date? {
        for (pattern in isoPatterns) {
            val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                if (pattern.contains("'Z'", ignoreCase = true) || pattern.contains("XXX")) {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            }
            try {
                return sdf.parse(value)
            } catch (_: ParseException) {
                // try next
            }
        }
        return null
    }

    private fun fromEpochGuess(epochValue: Long): Timestamp? {
        // Values larger than Jan 2286 seconds range imply milliseconds input.
        return if (epochValue > 9_999_999_999L) {
            Timestamp(Date(epochValue))
        } else {
            Timestamp(epochValue, 0)
        }
    }
}
