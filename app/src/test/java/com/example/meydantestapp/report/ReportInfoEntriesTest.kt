package com.example.meydantestapp.report

import com.example.meydantestapp.report.ReportPdfBuilder
import com.example.meydantestapp.report.buildReportInfoEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportInfoEntriesTest {

    @Test
    fun `buildReportInfoEntries returns ordered rows with values`() {
        val data = ReportPdfBuilder.DailyReport(
            organizationName = "مؤسسة الريادة",
            projectName = "برج الرياض",
            ownerName = "مالك المشروع",
            contractorName = "شركة المقاولات",
            consultantName = "المهندس الاستشاري",
            projectAddressText = "الرياض",
            projectGoogleMapsUrl = "https://maps.google.com/?q=riyadh",
            reportNumber = "DailyReport-5",
            dateText = "2025-01-02",
            temperatureC = "45",
            weatherCondition = "مشمس",
            createdBy = "المهندس أحمد"
        )

        val entries = buildReportInfoEntries(data)

        assertEquals(
            listOf(
                "اسم المشروع",
                "مالك المشروع",
                "مقاول المشروع",
                "الاستشاري",
                "رقم التقرير",
                "التاريخ",
                "درجة الحرارة",
                "حالة الطقس",
                "موقع المشروع",
                "تم إنشاء التقرير بواسطة"
            ),
            entries.map { it.label }
        )
        assertEquals(
            listOf(
                "برج الرياض",
                "مالك المشروع",
                "شركة المقاولات",
                "المهندس الاستشاري",
                "DailyReport-5",
                "2025-01-02",
                "45",
                "مشمس",
                "الرياض",
                "المهندس أحمد"
            ),
            entries.map { it.value }
        )
        assertEquals("https://maps.google.com/?q=riyadh", entries[8].linkUrl)
        assertTrue(entries.none { it.label.contains("المؤسسة") })
    }

    @Test
    fun `buildReportInfoEntries uses placeholder for blanks`() {
        val data = ReportPdfBuilder.DailyReport(
            projectName = null,
            ownerName = "  ",
            contractorName = null,
            consultantName = "",
            projectAddressText = " ",
            projectGoogleMapsUrl = "https://maps.google.com/?q=test",
            reportNumber = null,
            dateText = " ",
            temperatureC = null,
            weatherCondition = null,
            createdBy = ""
        )

        val entries = buildReportInfoEntries(data)

        assertEquals(10, entries.size)
        assertTrue(entries.all { it.value == "—" })
        assertNull(entries[8].linkUrl)
    }

    @Test
    fun `buildReportInfoEntries keeps address without link when url missing`() {
        val data = ReportPdfBuilder.DailyReport(
            projectAddressText = "الخبر",
            projectGoogleMapsUrl = null
        )

        val entries = buildReportInfoEntries(data)

        assertEquals("الخبر", entries[8].value)
        assertNull(entries[8].linkUrl)
    }
}
