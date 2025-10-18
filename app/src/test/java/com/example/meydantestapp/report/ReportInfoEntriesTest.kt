package com.example.meydantestapp.report

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.meydantestapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportInfoEntriesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

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

        val entries = buildReportInfoEntries(context, data)

        val expectedLabels = listOf(
            R.string.label_project_name,
            R.string.label_project_owner,
            R.string.label_project_contractor,
            R.string.label_project_consultant,
            R.string.label_report_number,
            R.string.label_report_date,
            R.string.label_temperature,
            R.string.label_weather_status,
            R.string.label_project_location,
            R.string.label_report_created_by
        ).map { context.getString(it) }

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

        val entries = buildReportInfoEntries(context, data)

        assertEquals(10, entries.size)
        assertTrue(entries.all { it.value == "—" })
        assertNull(entries[8].linkUrl)
    }
}
