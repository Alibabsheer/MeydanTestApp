package com.example.meydantestapp.utils

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirestoreTimestampConverterTest {

    @Test
    fun `parse literal timestamp string`() {
        val input = "Timestamp(seconds=1744837200, nanoseconds=0)"
        val result = FirestoreTimestampConverter.fromAny(input)
        assertEquals(true, result.migratedFromString)
        assertEquals(1744837200L, result.timestamp?.seconds)
        assertEquals(0, result.timestamp?.nanoseconds)
    }

    @Test
    fun `parse iso instant string`() {
        val input = "2025-09-30T16:45:00Z"
        val result = FirestoreTimestampConverter.fromAny(input)
        assertEquals(true, result.migratedFromString)
        val expected = Timestamp(1759250700L, 0)
        assertEquals(expected.seconds, result.timestamp?.seconds)
    }

    @Test
    fun `parse iso date only`() {
        val input = "2025-09-30"
        val result = FirestoreTimestampConverter.fromAny(input)
        val timestamp = result.timestamp
        requireNotNull(timestamp)
        assertEquals(true, result.migratedFromString)
    }

    @Test
    fun `invalid string returns null`() {
        val result = FirestoreTimestampConverter.fromAny("not-a-date")
        assertNull(result.timestamp)
    }

    @Test
    fun `detect milliseconds epoch`() {
        val millis = 1744837200000L
        val result = FirestoreTimestampConverter.fromAny(millis)
        assertEquals(1744837200L, result.timestamp?.seconds)
    }

    @Test
    fun `detect seconds epoch`() {
        val seconds = 1744837200L
        val result = FirestoreTimestampConverter.fromAny(seconds)
        assertEquals(seconds, result.timestamp?.seconds)
    }
}
