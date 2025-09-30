package com.example.meydantestapp.utils

import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale

/**
 * Helper responsible for normalising Firestore date values.
 * Accepts values saved as Timestamp, java.util.Date, numbers (seconds / millis),
 * ISO strings and the literal string "Timestamp(seconds=..., nanoseconds=...)".
 */
object FirestoreTimestampConverter {

    private val literalTimestampRegex =
        Regex("^Timestamp\\(seconds=(\\d+),\\s*nanoseconds=(\\d+)\\)$")

    private val customDisplayFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

    fun fromAny(raw: Any?): Timestamp? = when (raw) {
        null -> null
        is Timestamp -> raw
        is Date -> Timestamp(raw)
        is java.sql.Timestamp -> Timestamp(raw.time / 1000, raw.nanos)
        is Map<*, *> -> fromMap(raw)
        is Number -> fromNumber(raw)
        is String -> fromString(raw)
        else -> null
    }

    fun fromString(value: String?): Timestamp? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        literalTimestampRegex.matchEntire(trimmed)?.let { match ->
            val seconds = match.groupValues.getOrNull(1)?.toLongOrNull()
            val nanos = match.groupValues.getOrNull(2)?.toIntOrNull()
            if (seconds != null && nanos != null) {
                return runCatching { Timestamp(seconds, nanos) }.getOrNull()
            }
        }

        trimmed.toLongOrNull()?.let { numeric ->
            fromEpochGuess(numeric)?.let { return it }
        }

        parseIsoInstant(trimmed)?.let { instant ->
            return Timestamp(Date.from(instant))
        }

        parseCustomLocalDate(trimmed)?.let { localDate ->
            val instant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            return Timestamp(Date.from(instant))
        }

        return null
    }

    private fun fromNumber(number: Number): Timestamp? = when (number) {
        is Double, is Float -> {
            val doubleValue = number.toDouble()
            val seconds = doubleValue.toLong()
            val nanos = ((doubleValue - seconds) * 1_000_000_000).toInt()
            runCatching { Timestamp(seconds, nanos) }.getOrNull()
        }
        else -> fromEpochGuess(number.toLong())
    }

    private fun fromMap(map: Map<*, *>): Timestamp? {
        val seconds = (map["seconds"] as? Number)?.toLong()
        val nanos = (map["nanoseconds"] as? Number)?.toInt() ?: 0
        if (seconds != null) {
            return runCatching { Timestamp(seconds, nanos) }.getOrNull()
        }
        val millis = (map["milliseconds"] as? Number)?.toLong()
        return millis?.let { Timestamp(Date(it)) }
    }

    private fun fromEpochGuess(epochValue: Long): Timestamp? {
        return if (epochValue > 9_999_999_999L) {
            Timestamp(Date(epochValue))
        } else {
            runCatching { Timestamp(epochValue, 0) }.getOrNull()
        }
    }

    private fun parseIsoInstant(value: String): Instant? {
        runCatching { Instant.parse(value) }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()?.let { return it }
        runCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        }.getOrNull()?.let { return it }
        runCatching {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
        }.getOrNull()?.let { return it }
        return null
    }

    private fun parseCustomLocalDate(value: String): LocalDate? {
        return try {
            LocalDate.parse(value, customDisplayFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
