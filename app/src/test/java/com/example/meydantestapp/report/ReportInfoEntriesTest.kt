package com.example.meydantestapp.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportInfoEntriesTest {

    @Test
    fun `location entry exposes hyperlink when url present`() {
        val report = ReportPdfBuilder.DailyReport(
            projectAddressText = "الرياض",
            projectGoogleMapsUrl = "https://maps.google.com/?q=riyadh"
        )

        val entries = buildReportInfoEntries(report)
        val locationEntry = entries.first { it.label == "موقع المشروع" }

        assertEquals("الرياض", locationEntry.value)
        assertEquals("https://maps.google.com/?q=riyadh", locationEntry.linkUrl)
    }

    @Test
    fun `location entry falls back to plain text when url missing`() {
        val report = ReportPdfBuilder.DailyReport(
            projectAddressText = "جدة",
            projectGoogleMapsUrl = " "
        )

        val entries = buildReportInfoEntries(report)
        val locationEntry = entries.first { it.label == "موقع المشروع" }

        assertEquals("جدة", locationEntry.value)
        assertNull(locationEntry.linkUrl)
    }
}
