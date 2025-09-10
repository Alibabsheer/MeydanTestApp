package com.example.meydantestapp.ui.state

import android.graphics.Bitmap
import java.io.File

/**
 * حالات تحميل/توليد تقرير الـPDF لواجهة عرض التقرير اليومي.
 *
 * استخدمها في ViewModel لربط الواجهة وتفعيل/تعطيل الأزرار وإظهار مؤشر التحميل.
 */
sealed class ReportRenderState {

    /** الحالة الابتدائية قبل البدء */
    data object Idle : ReportRenderState()

    /** جلب بيانات التقرير (نصوص، حقول، روابط الصور) */
    data object LoadingReport : ReportRenderState()

    /** تحميل الصور كـ Bitmaps (مع تقدّم اختياري) */
    data class LoadingImages(
        val loaded: Int = 0,
        val total: Int = 0
    ) : ReportRenderState()

    /** توليد ملف PDF (تقدّم صفحـي اختياري) */
    data class RenderingPdf(
        val currentPage: Int = 0,
        val totalPagesPlanned: Int? = null
    ) : ReportRenderState()

    /** جاهز: ملف PDF النهائي (وعدد الصفحات)، مع معاينات اختيارية */
    data class Ready(
        val pdfFile: File,
        val pageCount: Int,
        val previews: List<Bitmap> = emptyList()
    ) : ReportRenderState()

    /** خطأ مُفسَّر لعرض رسالة للمستخدم */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ReportRenderState()
}
