package com.example.meydantestapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectDateFormatterTest {

    @Test
    fun `resolve handles legacy literal timestamp string`() {
        val result = ProjectDateFormatter.resolve(
            startRaw = "Timestamp(seconds=1744837200, nanoseconds=0)",
            endRaw = 1744837200000L,
            placeholder = "-"
        )

        assertEquals(1744837200L, result.startTimestamp?.seconds)
        assertEquals(1744837200L, result.endTimestamp?.seconds)
        requireNotNull(result.startTimestamp)
        assertEquals(result.startTimestamp.toDisplayDateString(), result.startDisplay)
        requireNotNull(result.endTimestamp)
        assertEquals(result.endTimestamp.toDisplayDateString(), result.endDisplay)
    }

    @Test
    fun `resolve falls back to placeholder when missing`() {
        val result = ProjectDateFormatter.resolve(null, null, placeholder = "غير محدد")
        assertNull(result.startTimestamp)
        assertNull(result.endTimestamp)
        assertEquals("غير محدد", result.startDisplay)
        assertEquals("غير محدد", result.endDisplay)
    }

    @Test
    fun `resolve accepts iso8601 strings`() {
        val result = ProjectDateFormatter.resolve(
            startRaw = "2025-09-30T16:45:00Z",
            endRaw = "2025-10-02",
            placeholder = "-"
        )

        assertEquals("30/09/2025", result.startDisplay)
        assertEquals("02/10/2025", result.endDisplay)
    }
}
