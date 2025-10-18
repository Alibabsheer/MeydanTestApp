package com.example.meydantestapp.report

import com.example.meydantestapp.report.ReportPdfBuilder
import com.example.meydantestapp.report.ReportInfoLabels
import com.example.meydantestapp.report.buildReportInfoEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportInfoEntriesTest {

    private val testLabels = ReportInfoLabels(
        projectName = "اسم المشروع",
        ownerName = "مالك المشروع",
        contractorName = "مقاول المشروع",
        consultantName = "الاستشاري",
        reportNumber = "رقم التقرير",
        reportDate = "التاريخ",
        temperature = "درجة الحرارة",
        weatherStatus = "حالة الطقس",
        projectLocation = "موقع المشروع",
        createdBy = "تم إنشاء التقرير بواسطة"
    )

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

        val entries = buildReportInfoEntries(data, testLabels)

        val expectedLabels = listOf(
            testLabels.projectName,
            testLabels.ownerName,
            testLabels.contractorName,
            testLabels.consultantName,
            testLabels.reportNumber,
            testLabels.reportDate,
            testLabels.temperature,
            testLabels.weatherStatus,
            testLabels.projectLocation,
            testLabels.createdBy
        )

        assertEquals(expectedLabels, entries.map { it.label })
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

        val entries = buildReportInfoEntries(data, testLabels)

        assertEquals(10, entries.size)
        assertTrue(entries.all { it.value == "—" })
        assertNull(entries[8].linkUrl)
    }
}
