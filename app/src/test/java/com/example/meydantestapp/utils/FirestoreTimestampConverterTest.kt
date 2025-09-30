package com.example.meydantestapp.utils

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
    }

    @Test
    fun `parse display date format`() {
        val input = "30/09/2025"
        val result = FirestoreTimestampConverter.fromAny(input)
        assertNotNull(result)
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
