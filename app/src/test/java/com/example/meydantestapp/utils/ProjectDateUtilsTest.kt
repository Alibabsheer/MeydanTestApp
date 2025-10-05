package com.example.meydantestapp.utils

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import com.example.meydantestapp.utils.ProjectDateUtils.toEpochDayUtc
import com.example.meydantestapp.utils.ProjectDateUtils.toUtcTimestamp

class ProjectDateUtilsTest {

    @Test
    fun `parseUserInput accepts arabic locale digits`() {
        val locale = Locale("ar")
        val result = ProjectDateUtils.parseUserInput("٠٣/٠٤/٢٠٢٤", locale)
        assertEquals(LocalDate.of(2024, 4, 3), result)
    }

    @Test
    fun `parseUserInput returns null for invalid input`() {
        val locale = Locale.US
        val result = ProjectDateUtils.parseUserInput("not-a-date", locale)
        assertNull(result)
    }

    @Test
    fun `toUtcTimestamp normalizes to midnight utc`() {
        val date = LocalDate.of(2024, 4, 3)
        val timestamp = date.toUtcTimestamp()
        val expectedSeconds = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        assertEquals(expectedSeconds, timestamp.seconds)
        assertEquals(0, timestamp.nanoseconds)
    }

    @Test
    fun `toEpochDayUtc extracts epoch day`() {
        val date = LocalDate.of(2024, 4, 3)
        val instant = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val timestamp = Timestamp(instant.epochSecond, instant.nano)
        assertEquals(date.toEpochDay(), timestamp.toEpochDayUtc())
    }
}
