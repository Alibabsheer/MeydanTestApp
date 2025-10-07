package com.example.meydantestapp.report

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ReportPdfBuilderSamplePdfTest {

    @Test
    fun `builds compact sample pdf`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val builder = ReportPdfBuilder(context)

        val outputDir = File(context.cacheDir, "report-pdf-samples").apply { mkdirs() }
        val outputFile = File(outputDir, "daily-report-sample.pdf")
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val data = ReportPdfBuilder.DailyReport(
            organizationName = "مؤسسة ميدان",
            projectName = "برج الميدان الإداري",
            ownerName = "شركة التطوير العربية",
            contractorName = "مقاولات الشرق",
            consultantName = "بيوت الخبرة",
            projectLocation = "الرياض - حي العقيق",
            projectLocationGoogleMapsUrl = "https://maps.example.com/location",
            reportNumber = "DR-1024",
            dateText = "25 أكتوبر 2024",
            temperatureC = "29°",
            weatherCondition = "غائم جزئيًا",
            createdBy = "م. أحمد السبيعي",
            dailyActivities = listOf("أعمال تشطيب الطابق 12", "توريد الدفعة الثانية من الزجاج"),
            resourcesUsed = listOf("خرسانة جاهزة 25 م³", "سقالات إضافية"),
            notes = listOf("تأكيد موعد زيارة الاستشاري الأسبوع القادم")
        )

        val result = builder.buildPdf(data, logo = null, outFile = outputFile)

        assertTrue("PDF file should exist", result.exists())
        assertTrue("PDF file should not be empty", result.length() > 0L)

        println("Sample PDF exported to: ${result.absolutePath}")
    }
}
