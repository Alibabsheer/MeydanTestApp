package com.example.meydantestapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateDailyReportPayloadTest {

    @Test
    fun `applyDailyReportOptionalFields excludes organization name`() {
        val payload = mutableMapOf<String, Any>("id" to "r-1")

        payload.applyDailyReportOptionalFields(
            projectName = "مشروع 1",
            ownerName = "مالك 1",
            contractorName = "مقاول 1",
            consultantName = "استشاري 1",
            projectNumber = "42",
            location = "الرياض",
            latitude = 24.5,
            longitude = 46.7,
            temperature = "30",
            weatherStatus = "مشمس",
            dailyActivities = listOf("نشاط"),
            skilledLabor = 5,
            unskilledLabor = 3,
            totalLabor = 8,
            resourcesUsed = listOf("رافعة"),
            challenges = listOf("لا يوجد"),
            notes = listOf("ملاحظة"),
            photos = listOf("gs://bucket/photo"),
            sitepages = listOf("https://example.com/page.webp"),
            sitepagesmeta = listOf(mapOf("index" to 0)),
            createdByName = "م. أحمد"
        )

        assertFalse(payload.containsKey("organizationName"))
        assertEquals("مشروع 1", payload["projectName"])
        assertEquals("مالك 1", payload["ownerName"])
        assertEquals("مقاول 1", payload["contractorName"])
        assertEquals("استشاري 1", payload["consultantName"])
        assertEquals("الرياض", payload["projectLocation"])
        assertEquals("الرياض", payload["location"])
        assertEquals("م. أحمد", payload["createdByName"])
    }

    @Test
    fun `applyDailyReportOptionalFields skips null and empty values`() {
        val payload = mutableMapOf<String, Any>()

        payload.applyDailyReportOptionalFields(
            projectName = null,
            ownerName = null,
            contractorName = " ",
            consultantName = null,
            projectNumber = "   ",
            location = null,
            latitude = null,
            longitude = null,
            temperature = null,
            weatherStatus = "",
            dailyActivities = emptyList(),
            skilledLabor = null,
            unskilledLabor = null,
            totalLabor = null,
            resourcesUsed = emptyList(),
            challenges = emptyList(),
            notes = emptyList(),
            photos = emptyList(),
            sitepages = emptyList(),
            sitepagesmeta = emptyList(),
            createdByName = null
        )

        assertTrue(payload.isEmpty())
    }
}
