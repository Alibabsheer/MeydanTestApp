package com.example.meydantestapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.meydantestapp.data.model.DailyReport
import com.example.meydantestapp.data.model.Project
import java.io.File

/**
 * مدير عمليات التصدير (Export Manager)
 * مسؤول عن إنشاء ملفات PDF و Excel وإدارة مشاركتها.
 */
class ExportManager(private val context: Context) {

    /**
     * مشاركة ملف تم تصديره مع تطبيقات أخرى (مثل WhatsApp أو البريد)
     */
    fun shareFile(file: File, title: String = "مشاركة التقرير") {
        if (!file.exists()) return

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "*/*"
        }
    }

    /**
     * تصدير التقرير اليومي إلى ملف PDF احترافي
     */
    suspend fun exportDailyReportToPdf(report: DailyReport, project: Project): File? {
        return try {
            val fileName = "DailyReport_${project.name}_${report.date.replace("/", "-")}.pdf"
            val file = File(context.cacheDir, fileName)
            
            val writer = com.itextpdf.kernel.pdf.PdfWriter(file)
            val pdf = com.itextpdf.kernel.pdf.PdfDocument(writer)
            val document = com.itextpdf.layout.Document(pdf)
            
            // إعدادات الخطوط العربية (تحتاج ملف خط في الموارد)
            // val font = com.itextpdf.kernel.font.PdfFontFactory.createFont("assets/fonts/arial.ttf", com.itextpdf.io.font.PdfEncodings.IDENTITY_H)
            
            // العنوان الرئيسي
            document.add(com.itextpdf.layout.element.Paragraph("التقرير اليومي للمشروع")
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setFontSize(20f)
                .setBold())
            
            // معلومات المشروع
            document.add(com.itextpdf.layout.element.Paragraph("اسم المشروع: ${project.name}")
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT))
            document.add(com.itextpdf.layout.element.Paragraph("التاريخ: ${report.date}")
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT))
            document.add(com.itextpdf.layout.element.Paragraph("حالة الطقس: ${report.weather}")
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT))
            
            document.add(com.itextpdf.layout.element.AreaBreak())
            
            // تفاصيل النشاطات
            document.add(com.itextpdf.layout.element.Paragraph("النشاطات المنجزة:")
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT)
                .setBold())
            document.add(com.itextpdf.layout.element.Paragraph(report.activities)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT))
            
            // إضافة الصور إذا وجدت
            if (report.images.isNotEmpty()) {
                document.add(com.itextpdf.layout.element.Paragraph("الصور الميدانية:")
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT)
                    .setBold())
                
                // ملاحظة: إضافة الصور تتطلب تحميلها أولاً من الروابط
                // سيتم تحسين هذا الجزء لاحقاً لدعم تحميل الصور من Firebase Storage
            }
            
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * تصدير بيانات المشروع وجداول الكميات إلى ملف Excel احترافي
     */
    suspend fun exportProjectToExcel(project: Project): File? {
        return try {
            val fileName = "Project_${project.name}_Summary.xlsx"
            val file = File(context.cacheDir, fileName)
            
            val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
            val sheet = workbook.createSheet("ملخص المشروع")
            
            // إعداد التنسيقات (العناوين)
            val headerFont = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 14
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                alignment = org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER
            }
            
            // إضافة البيانات الأساسية
            var rowNum = 0
            val headerRow = sheet.createRow(rowNum++)
            headerRow.createCell(0).apply {
                setCellValue("معلومات المشروع")
                cellStyle = headerStyle
            }
            
            sheet.createRow(rowNum++).createCell(0).setCellValue("اسم المشروع: ${project.name}")
            sheet.createRow(rowNum++).createCell(0).setCellValue("الموقع: ${project.location}")
            sheet.createRow(rowNum++).createCell(0).setCellValue("الحالة: ${project.status}")
            
            // إضافة مساحة
            rowNum++
            
            // إضافة جداول الكميات (إذا وجدت في كود المشروع)
            // ملاحظة: هذا الجزء يعتمد على هيكلية الـ BOQ في قاعدة البيانات الخاصة بك
            val boqHeaderRow = sheet.createRow(rowNum++)
            boqHeaderRow.createCell(0).setCellValue("جدول الكميات")
            
            val boqTableHeaders = sheet.createRow(rowNum++)
            boqTableHeaders.createCell(0).setCellValue("البند")
            boqTableHeaders.createCell(1).setCellValue("الوصف")
            boqTableHeaders.createCell(2).setCellValue("الكمية")
            boqTableHeaders.createCell(3).setCellValue("الوحدة")
            
            // حفظ الملف
            file.outputStream().use { 
                workbook.write(it)
            }
            workbook.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
