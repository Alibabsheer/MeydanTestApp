package com.example.meydantestapp.utils

import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.text.RegexOption

/**
 * Helper responsible for normalising Firestore date values.
 *
 * The converter is tolerant to legacy shapes (string literals, maps or millis) and never throws.
 * Invalid inputs simply result in `null`, leaving the caller to decide on fallbacks.
 */
object FirestoreTimestampConverter {

    private const val NANOS_PER_SECOND = 1_000_000_000
    private const val EPOCH_MILLIS_THRESHOLD = 9_999_999_999L

    private val literalTimestampRegex = Regex(
        pattern = "^Timestamp\\(\\s*seconds\\s*=\\s*(-?\\d+),\\s*nanoseconds\\s*=\\s*(-?\\d+)\\s*\\)$",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val localDateFormatters: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ofPattern(Constants.DATE_FORMAT_DISPLAY, Locale.getDefault()),
        DateTimeFormatter.ofPattern("yyyy-M-d", Locale.getDefault()),
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.getDefault())
    )

    /**
     * Attempts to normalise any Firestore-stored date representation into [Timestamp].
     * Returns `null` when the value cannot be interpreted.
     */
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

    /**
     * Parses strings that may contain literal "Timestamp(...)" representations, ISO dates,
     * or plain epoch values. Returns `null` when none match.
     */
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

        parseLocalDate(trimmed)?.let { localDate ->
            val instant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            return Timestamp(Date.from(instant))
        }

        return null
    }

    /**
     * Converts numeric epochs expressed as seconds, milliseconds, or fractional seconds to [Timestamp].
     * Doubles that are NaN/Infinity return `null`.
     */
    private fun fromNumber(number: Number): Timestamp? {
        return when (number) {
            is Double, is Float -> number.toDouble().let { doubleValue ->
                if (doubleValue.isNaN() || doubleValue.isInfinite()) {
                    null
                } else if (abs(doubleValue) >= EPOCH_MILLIS_THRESHOLD) {
                    fromEpochGuess(doubleValue.toLong())
                } else {
                    val secondsPart = doubleValue.toLong()
                    val nanosRaw = ((doubleValue - secondsPart) * NANOS_PER_SECOND).roundToInt()
                    val (normalizedSeconds, normalizedNanos) = normalizeSecondsAndNanos(secondsPart, nanosRaw)
                    runCatching { Timestamp(normalizedSeconds, normalizedNanos) }.getOrNull()
                }
            }
            else -> fromEpochGuess(number.toLong())
        }
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
        if (epochValue == Long.MIN_VALUE) return null
        return if (abs(epochValue) > EPOCH_MILLIS_THRESHOLD) {
            runCatching { Timestamp(Date(epochValue)) }.getOrNull()
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
        return null
    }

    private fun parseLocalDate(value: String): LocalDate? {
        runCatching { LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()?.let { return it }
        for (formatter in localDateFormatters) {
            runCatching { LocalDate.parse(value, formatter) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun normalizeSecondsAndNanos(seconds: Long, nanos: Int): Pair<Long, Int> {
        var sec = seconds
        var nano = nanos
        if (nano >= NANOS_PER_SECOND || nano <= -NANOS_PER_SECOND) {
            sec += nano / NANOS_PER_SECOND
            nano %= NANOS_PER_SECOND
        }
        if (nano < 0) {
            val borrow = (abs(nano) + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND
            sec -= borrow
            nano += borrow * NANOS_PER_SECOND
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
