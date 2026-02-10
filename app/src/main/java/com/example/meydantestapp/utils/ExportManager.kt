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
     * سيتم تنفيذ الدوال الفعلية لإنشاء الـ PDF والـ Excel في المراحل القادمة
     */
    suspend fun exportDailyReportToPdf(report: DailyReport, project: Project): File? {
        // سيتم تنفيذه في المرحلة 2
        return null
    }

    suspend fun exportProjectToExcel(project: Project): File? {
        // سيتم تنفيذه في المرحلة 3
        return null
    }
}
