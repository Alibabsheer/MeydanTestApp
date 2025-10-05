package com.example.meydantestapp.utils

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FirestoreTimestampConverterTest {

    @Test
    fun `parse literal timestamp string`() {
        val input = "Timestamp(seconds=1744837200, nanoseconds=0)"
        val result = FirestoreTimestampConverter.fromAny(input)
        assertNotNull(result)
        assertEquals(1744837200L, result?.seconds)
        assertEquals(0, result?.nanoseconds)
    }

    @Test
    fun `parse literal timestamp string with spaces`() {
        val input = "  Timestamp( seconds=1744837200,   nanoseconds=42 )  "
        val result = FirestoreTimestampConverter.fromAny(input)
        assertNotNull(result)
        assertEquals(1744837200L, result?.seconds)
        assertEquals(42, result?.nanoseconds)
    }

    @Test
    fun `parse iso instant string`() {
        val input = "2025-09-30T16:45:00Z"
        val result = FirestoreTimestampConverter.fromAny(input)
        assertNotNull(result)
        assertEquals(1759250700L, result?.seconds)
    }

    @Test
    fun `parse iso date only`() {
        val input = "2025-09-30"
        val result = FirestoreTimestampConverter.fromAny(input)
        assertNotNull(result)
        val expectedSeconds = LocalDate.of(2025, 9, 30).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        assertEquals(expectedSeconds, result?.seconds)
    }

    @Test
    fun `parse display date format`() {
        val input = "30/09/2025"
        val result = FirestoreTimestampConverter.fromAny(input)
        assertNotNull(result)
        val expectedSeconds = LocalDate.of(2025, 9, 30).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        assertEquals(expectedSeconds, result?.seconds)
    }

    @Test
    fun `parse legacy year month day format`() {
        val input = "2025-9-30"
        val result = FirestoreTimestampConverter.fromAny(input)
        assertNotNull(result)
        assertEquals("30/09/2025", result?.toDisplayDateString())
    }

    @Test
    fun `parse arabic indic digits`() {
        val input = "٠١/١٠/٢٠٢٥"
        val result = FirestoreTimestampConverter.fromAny(input)
        assertNotNull(result)
        val expectedSeconds = LocalDate.of(2025, 10, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        assertEquals(expectedSeconds, result?.seconds)
    }

    @Test
    fun `string milliseconds parsed to timestamp`() {
        val result = FirestoreTimestampConverter.fromAny("1744837200000")
        assertNotNull(result)
        assertEquals(1744837200L, result?.seconds)
        assertEquals(0, result?.nanoseconds)
    }

    @Test
    fun `string fractional seconds parsed`() {
        val result = FirestoreTimestampConverter.fromAny("1744837200.25")
        assertNotNull(result)
        assertEquals(1744837200L, result?.seconds)
        assertEquals(250_000_000, result?.nanoseconds)
    }

    @Test
    fun `invalid string returns null`() {
        val result = FirestoreTimestampConverter.fromAny("not-a-date")
        assertNull(result)
    }

    @Test
    fun `detect milliseconds epoch`() {
        val millis = 1744837200000L
        val result = FirestoreTimestampConverter.fromAny(millis)
        assertEquals(1744837200L, result?.seconds)
    }

    @Test
    fun `detect seconds epoch`() {
        val seconds = 1744837200L
        val result = FirestoreTimestampConverter.fromAny(seconds)
        assertEquals(seconds, result?.seconds)
    }

    @Test
    fun `parse fractional seconds`() {
        val fractional = 1744837200.5
        val result = FirestoreTimestampConverter.fromAny(fractional)
        assertEquals(1744837200L, result?.seconds)
        assertEquals(500_000_000, result?.nanoseconds)
    }

    @Test
    fun `nan double returns null`() {
        val result = FirestoreTimestampConverter.fromAny(Double.NaN)
        assertNull(result)
    }

    @Test
    fun `infinite double returns null`() {
        val result = FirestoreTimestampConverter.fromAny(Double.POSITIVE_INFINITY)
        assertNull(result)
    }

    @Test
    fun `parse map structure`() {
        val mapValue = mapOf(
            "seconds" to 1744837200L,
            "nanoseconds" to 123456789
        )
        val result = FirestoreTimestampConverter.fromAny(mapValue)
        assertNotNull(result)
        assertEquals(1744837200L, result?.seconds)
        assertEquals(123456789, result?.nanoseconds)
    }

    @Test
    fun `parse map with string values`() {
        val mapValue = mapOf(
            "seconds" to "1744837200",
            "nanoseconds" to "321"
        )
        val result = FirestoreTimestampConverter.fromAny(mapValue)
        assertNotNull(result)
        assertEquals(1744837200L, result?.seconds)
        assertEquals(321, result?.nanoseconds)
    }

    @Test
    fun `parse map with underscore keys`() {
        val mapValue = mapOf(
            "_seconds" to 1744837200,
            "_nanoseconds" to 987654321
        )
        val result = FirestoreTimestampConverter.fromAny(mapValue)
        assertNotNull(result)
        assertEquals(1744837200L, result?.seconds)
        assertEquals(987654321, result?.nanoseconds)
    }

    @Test
    fun `map milliseconds fallback`() {
        val mapValue = mapOf(
            "milliseconds" to "1744837200000"
        )
        val result = FirestoreTimestampConverter.fromAny(mapValue)
        assertNotNull(result)
        assertEquals(1744837200L, result?.seconds)
    }

    @Test
    fun `null input returns null`() {
        val result = FirestoreTimestampConverter.fromAny(null)
        assertNull(result)
    }

    @Test
    fun `timestamp passthrough`() {
        val ts = Timestamp.now()
        val result = FirestoreTimestampConverter.fromAny(ts)
        assertEquals(ts, result)
    }
}
