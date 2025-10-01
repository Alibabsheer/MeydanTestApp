package com.example.meydantestapp.utils

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectDateFormatterTest {

    @Test
    fun `resolve formats legacy inputs with placeholder`() {
        val startRaw = "Timestamp(seconds=1744837200, nanoseconds=0)"
        val endRaw = mapOf("seconds" to 1744923600L)

        val result = ProjectDateFormatter.resolve(startRaw, endRaw)

        assertEquals(1744837200L, result.startTimestamp?.seconds)
        assertEquals(1744923600L, result.endTimestamp?.seconds)
        assertEquals(result.startTimestamp?.toDisplayDateString(), result.startDisplay)
        assertEquals(result.endTimestamp?.toDisplayDateString(), result.endDisplay)
    }

    @Test
    fun `resolve respects custom placeholder`() {
        val placeholder = ""
        val result = ProjectDateFormatter.resolve(null, null, placeholder)

        assertNull(result.startTimestamp)
        assertNull(result.endTimestamp)
        assertEquals(placeholder, result.startDisplay)
        assertEquals(placeholder, result.endDisplay)
    }

    @Test
    fun `from data map supports mixed shapes`() {
        val data = mapOf<String, Any?>(
            "startDate" to 1744837200000L,
            "endDate" to Timestamp(1744923600, 0)
        )

        val result = ProjectDateFormatter.fromDataMap(data, placeholder = "n-a")

        assertEquals(1744837200L, result.startTimestamp?.seconds)
        assertEquals(1744923600L, result.endTimestamp?.seconds)
        assertEquals(result.startTimestamp?.toDisplayDateString(), result.startDisplay)
        assertEquals(result.endTimestamp?.toDisplayDateString(), result.endDisplay)
    }
}
