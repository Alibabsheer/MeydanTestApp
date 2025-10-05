package com.example.meydantestapp.report

import com.example.meydantestapp.report.ReportPdfBuilder
import com.example.meydantestapp.report.buildReportInfoEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportInfoEntriesTest {

    @Test
    fun `buildReportInfoEntries omits organization label`() {
        val data = ReportPdfBuilder.DailyReport(
            organizationName = "مؤسسة الريادة",
            projectName = "برج الرياض",
            projectLocation = "الرياض",
            projectLocationGoogleMapsUrl = "https://maps.google.com/?q=riyadh",
            reportNumber = "DailyReport-5",
            dateText = "2025-01-02",
            createdBy = "المهندس أحمد"
        )

        val entries = buildReportInfoEntries(data)

        assertEquals(
            listOf(
                "اسم المشروع",
                "موقع المشروع",
                "رقم التقرير",
                "تاريخ التقرير",
                "تم إنشاء التقرير بواسطة"
            ),
            entries.map { it.label }
        )
        assertTrue(entries.none { it.label.contains("المؤسسة") })
    }

    @Test
    fun `buildReportInfoEntries handles blank fields`() {
        val data = ReportPdfBuilder.DailyReport(
            projectName = "مشروع الاختبار",
            projectLocation = " ",
            reportNumber = null,
            dateText = "2025-01-03",
            createdBy = ""
        )

        val entries = buildReportInfoEntries(data)

        assertEquals(listOf("اسم المشروع", "تاريخ التقرير"), entries.map { it.label })
    }
}
