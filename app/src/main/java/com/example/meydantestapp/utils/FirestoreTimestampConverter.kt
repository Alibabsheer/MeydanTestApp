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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.text.RegexOption

/**
 * Helper responsible for normalising Firestore date values.
 * Accepts values saved as Timestamp, java.util.Date, numbers (seconds / millis),
 * ISO strings and the literal string "Timestamp(seconds=..., nanoseconds=...)".
 */
object FirestoreTimestampConverter {

    private val literalTimestampRegex = Regex(
        pattern = "^Timestamp\\(\\s*seconds\\s*=\\s*(-?\\d+),\\s*nanoseconds\\s*=\\s*(-?\\d+)\\s*\\)$",
        option = RegexOption.IGNORE_CASE
    )

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
                val (normalizedSeconds, normalizedNanos) = normalizeSecondsAndNanos(seconds, nanos)
                return runCatching { Timestamp(normalizedSeconds, normalizedNanos) }.getOrNull()
            }
        }

        trimmed.toLongOrNull()?.let { numeric ->
            fromEpochGuess(numeric)?.let { return it }
        }

        trimmed.toDoubleOrNull()?.let { numericDouble ->
            fromNumber(numericDouble)?.let { return it }
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
            if (doubleValue.isNaN() || doubleValue.isInfinite()) {
                return null
            }
            if (abs(doubleValue) >= 9_999_999_999L) {
                return Timestamp(Date(doubleValue.toLong()))
            }
            val secondsPart = doubleValue.toLong()
            val nanosRaw = ((doubleValue - secondsPart) * 1_000_000_000.0).roundToInt()
            val (normalizedSeconds, normalizedNanos) = normalizeSecondsAndNanos(secondsPart, nanosRaw)
            runCatching { Timestamp(normalizedSeconds, normalizedNanos) }.getOrNull()
        }
        else -> fromEpochGuess(number.toLong())
    }

    private fun fromMap(map: Map<*, *>): Timestamp? {
        val seconds = map["seconds"].toLongStrict()
            ?: map["_seconds"].toLongStrict()
            ?: map["secs"].toLongStrict()
        val nanos = map["nanoseconds"].toIntStrict()
            ?: map["_nanoseconds"].toIntStrict()
            ?: map["nanos"].toIntStrict()
            ?: 0
        if (seconds != null) {
            val (normalizedSeconds, normalizedNanos) = normalizeSecondsAndNanos(seconds, nanos)
            return runCatching { Timestamp(normalizedSeconds, normalizedNanos) }.getOrNull()
        }
        val millis = map["milliseconds"].toLongStrict()
            ?: map["millis"].toLongStrict()
            ?: map["ms"].toLongStrict()
        return millis?.let { Timestamp(Date(it)) }
    }

    private fun fromEpochGuess(epochValue: Long): Timestamp? {
        return if (abs(epochValue) > 9_999_999_999L) {
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

    private fun normalizeSecondsAndNanos(seconds: Long, nanos: Int): Pair<Long, Int> {
        var sec = seconds
        var nano = nanos
        if (nano >= 1_000_000_000 || nano <= -1_000_000_000) {
            sec += nano / 1_000_000_000
            nano %= 1_000_000_000
        }
        if (nano < 0) {
            val borrow = (abs(nano) + 999_999_999) / 1_000_000_000
            sec -= borrow
            nano += borrow * 1_000_000_000
        }
        return sec to nano
    }

    private fun Any?.toLongStrict(): Long? = when (this) {
        is Number -> this.toLong()
        is String -> this.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
        else -> null
    }

    private fun Any?.toIntStrict(): Int? = when (this) {
        is Number -> this.toInt()
        is String -> {
            val trimmed = this.trim()
            trimmed.toIntOrNull()
                ?: trimmed.toLongOrNull()?.toInt()
                ?: trimmed.toDoubleOrNull()?.roundToInt()
        }
        else -> null
    }
}
