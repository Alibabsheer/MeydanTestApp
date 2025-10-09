package com.example.meydantestapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectLocationSnapshotFactoryTest {

    @Test
    fun `snapshotForReport prefers provided maps url`() {
        val snapshot = ProjectLocationSnapshotFactory.snapshotForReport(
            addressText = "الرياض",
            googleMapsUrl = "https://maps.google.com/?q=custom",
            latitude = 24.7136,
            longitude = 46.6753
        )

        assertEquals("الرياض", snapshot.addressText)
        assertEquals("https://maps.google.com/?q=custom", snapshot.googleMapsUrl)
    }

    @Test
    fun `snapshotForReport builds coordinates url when missing`() {
        val snapshot = ProjectLocationSnapshotFactory.snapshotForReport(
            addressText = "مشروع",
            googleMapsUrl = null,
            latitude = 24.7136,
            longitude = 46.6753
        )

        assertTrue(snapshot.googleMapsUrl!!.contains("query=24.7136,46.6753"))
    }

    @Test
    fun `snapshotForReport falls back to search url when only address`() {
        val snapshot = ProjectLocationSnapshotFactory.snapshotForReport(
            addressText = "حي الملز، الرياض",
            googleMapsUrl = " ",
            latitude = null,
            longitude = null
        )

        assertEquals("حي الملز، الرياض", snapshot.addressText)
        assertTrue(snapshot.googleMapsUrl!!.contains("maps/search"))
        assertTrue(snapshot.googleMapsUrl!!.contains("query="))
    }

    @Test
    fun `fromDailyReportData resolves legacy address`() {
        val snapshot = ProjectLocationSnapshotFactory.fromDailyReportData(
            mapOf(
                "projectLocation" to "جدة",
                "googleMapsUrl" to null
            )
        )

        assertEquals("جدة", snapshot.addressText)
        assertNull(snapshot.googleMapsUrl)
    }
}
