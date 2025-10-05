package com.example.meydantestapp.utils

import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

object ProjectDateUtils {

    private const val LEGACY_PATTERN = "yyyy-M-d"
    private const val LEGACY_SLASH_PATTERN = "yyyy/MM/dd"

    fun parseUserInput(value: String, locale: Locale = Locale.getDefault()): LocalDate? {
        val normalized = value.trim().takeIf { it.isNotEmpty() }?.toLatinDigits() ?: return null
        val formatters = listOf(
            DateTimeFormatter.ofPattern(Constants.DATE_FORMAT_DISPLAY, locale),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern(LEGACY_PATTERN, locale),
            DateTimeFormatter.ofPattern(LEGACY_SLASH_PATTERN, locale)
        ).map { it.withLocale(locale).withDecimalStyle(DecimalStyle.STANDARD) }

        for (formatter in formatters) {
            runCatching { LocalDate.parse(normalized, formatter) }.getOrNull()?.let { return it }
        }
        return null
    }

    fun formatForDisplay(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        val formatter = DateTimeFormatter
            .ofPattern(Constants.DATE_FORMAT_DISPLAY, locale)
            .withDecimalStyle(DecimalStyle.of(locale))
        return formatter.format(date)
    }

    fun LocalDate.toUtcTimestamp(): Timestamp {
        val instant = atStartOfDay(ZoneOffset.UTC).toInstant()
        return Timestamp(instant.epochSecond, instant.nano)
    }

    fun Timestamp.toUtcLocalDate(): LocalDate {
        return java.time.Instant.ofEpochSecond(seconds.toLong(), nanoseconds.toLong())
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
    }

    fun Timestamp?.toEpochDayUtc(): Long? = this?.let { toUtcLocalDate().toEpochDay() }
}
