package com.example.meydantestapp.report

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * PdfDailyReportRenderer – مولّد PDF بقياس A4.
 *
 * يدعم ثلاث حالات رئيسية:
 * 1) شبكة صور مربّعة 2×3 مع رأس/ميتا وتذييل (الوضع القديم).
 * 2) صور صفحات جاهزة (sitepages) تُملأ الصفحة كاملة (توافق قديم).
 * 3) **جديد**: وضع صور الصفحات الجاهزة داخل **منطقة الصور** المحددة في صفحة A4 (المربع الأحمر)
 *    باستخدام fit-inside **بدون أي قص**.
 */
class PdfDailyReportRenderer(private val context: Context) {

    data class PdfResult(val file: File, val pageCount: Int)

    data class FooterConfig(
        val leftPageLabelArabic: String = "صفحة",
        val rightText: String = "تم إنشاء التقرير اليومي بواسطة تطبيق ميدان"
    )

    // أبعاد A4 (≈150dpi) — ثابتة عبر الملف لضمان تناسق التحديدات
    private val pageWidth = 1240
    private val pageHeight = 1754

    // هوامش ومقاسات ثابتة
    private val marginH = 64f
    private val marginV = 64f
    private val footerHeight = 96f
    private val footerGap = 16f // مسافة قبل التذييل (لرسم الخط)
    private val headerGap = 24f
    private val sectionGap = 24f

    // شبكة الصور (الوضع 1): 2 أعمدة × 3 صفوف (مربعات)
    private val gridCols = 2
    private val gridRows = 3
    private val gridHGap = 16f
    private val gridVGap = 16f

    // فرش الرسم
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 48f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 36f
        textAlign = Paint.Align.RIGHT
    }
    private val metaSmallPaint = Paint(metaPaint).apply { // لاسم المشروع فقط
        textSize = 28f
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 32f
        textAlign = Paint.Align.RIGHT
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 32f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 2f
    }

    /**
     * الوضع (1): إنشاء تقرير A4 بشبكة 2×3 مربّعات (قديم).
     * @param outputFile ملف الإخراج (سيُستبدل إن وُجد)
     * @param headerLogo شعار أعلى الصفحة (اختياري)
     * @param reportTitle عنوان التقرير (مثلاً: "التقرير اليومي")
     * @param meta قائمة حقول (عنوان → قيمة) تُعرض RTL تحت العنوان
     * @param photos قائمة Bitmaps للصور (6 صور لكل صفحة)
     */
    fun renderA4DailyReport(
        outputFile: File,
        headerLogo: Bitmap?,
        reportTitle: String,
        meta: List<Pair<String, String>>,
        photos: List<Bitmap>,
        footerConfig: FooterConfig = FooterConfig()
    ): PdfResult {
        if (outputFile.exists()) outputFile.delete()

        val pdf = PdfDocument()
        var pageCount = 0
        var currentPageNumber = 0

        val chunks: List<List<Bitmap>> = photos.chunked(gridCols * gridRows).ifEmpty { listOf(emptyList()) }

        lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var yCursor = 0f

        fun startPage() {
            currentPageNumber += 1
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            page = pdf.startPage(info)
            canvas = page.canvas
            yCursor = marginV

            // شعار بعرض الصفحة (اختياري)
            headerLogo?.let { logo ->
                val dest = fitWidthRect(logo.width, logo.height, pageWidth.toFloat())
                val dst = RectF(0f, yCursor, pageWidth.toFloat(), yCursor + dest.height())
                val scaled = Bitmap.createScaledBitmap(logo, dst.width().toInt(), dst.height().toInt(), true)
                canvas.drawBitmap(scaled, null, dst, null)
                yCursor = dst.bottom + headerGap
            }

            // عنوان التقرير
            canvas.drawText(reportTitle, pageWidth / 2f, yCursor + titlePaint.textSize, titlePaint)
            yCursor += titlePaint.textSize + sectionGap

            // حقول meta (يمين → يسار)
            yCursor = drawMetaKeyValues(canvas, meta, marginH, pageWidth - marginH, yCursor)
            yCursor += sectionGap
        }

        fun closePage() {
            // خط نهاية الصفحة + التذييل (يسار/يمين)
            val lineY = pageHeight - footerHeight - footerGap
            canvas.drawLine(marginH, lineY, pageWidth - marginH, lineY, linePaint)
            val baseY = pageHeight - marginV / 2f
            footerPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${footerConfig.leftPageLabelArabic} $currentPageNumber", marginH, baseY, footerPaint)
            footerPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(footerConfig.rightText, pageWidth - marginH, baseY, footerPaint)
            pdf.finishPage(page)
            pageCount++
        }

        // البدء
        startPage()

        chunks.forEachIndexed { idx, pagePhotos ->
            val contentLeft = marginH
            val contentRight = pageWidth - marginH
            val contentWidth = contentRight - contentLeft
            val bottomLimit = pageHeight - footerHeight - footerGap - marginV

            // سطر التاريخ فوق شبكة الصور
            extractDate(meta)?.let { dateStr ->
                val lh = datePaint.textSize * 1.5f
                if (yCursor + lh > bottomLimit) {
                    closePage(); startPage()
                }
                canvas.drawText(dateStr, contentRight, yCursor + datePaint.textSize, datePaint)
                yCursor += lh
            }

            // حساب مقاس المربّع: يلائم العرض والارتفاع المتاح
            val cols = gridCols
            val rows = gridRows
            val cellFromWidth = (contentWidth - gridHGap * (cols - 1)) / cols
            val cellFromHeight = ((bottomLimit - yCursor) - (gridVGap * (rows - 1))) / rows
            var cell = kotlin.math.min(cellFromWidth, cellFromHeight)

            // لو المساحة ضيقة جدًا — افتح صفحة جديدة قبل الشبكة
            if (cell < 60f) {
                closePage(); startPage()
                extractDate(meta)?.let { d ->
                    val lh = datePaint.textSize * 1.5f
                    canvas.drawText(d, contentRight, yCursor + datePaint.textSize, datePaint)
                    yCursor += lh
                }
                val cellFromWidth2 = (contentWidth - gridHGap * (cols - 1)) / cols
                val cellFromHeight2 = ((bottomLimit - yCursor) - (gridVGap * (rows - 1))) / rows
                cell = kotlin.math.min(cellFromWidth2, cellFromHeight2)
            }

            // هامش أمان بسيط
            cell *= 0.98f

            var photoIndex = 0
            for (r in 0 until rows) {
                val rowTop = yCursor + r * (cell + gridVGap)
                for (c in 0 until cols) {
                    if (photoIndex >= pagePhotos.size) break
                    val colLeft = contentLeft + c * (cell + gridHGap)
                    val dst = RectF(colLeft, rowTop, colLeft + cell, rowTop + cell)
                    drawBitmapCenterCropSquare(canvas, pagePhotos[photoIndex], dst)
                    photoIndex++
                }
            }

            closePage()
            if (idx < chunks.lastIndex) startPage()
        }

        FileOutputStream(outputFile).use { fos -> pdf.writeTo(fos) }
        pdf.close()
        return PdfResult(outputFile, pageCount)
    }

    /**
     * الوضع (2 – توافق قديم): إنشاء PDF من صور صفحات جاهزة (sitepages) بملء الصفحة.
     * يُفضَّل الآن استخدام الدالة الجديدة renderSitePagesIntoPhotoArea لمواءمة "منطقة الصور".
     */
    fun renderSitePages(
        outputFile: File,
        pageImageUrls: List<String>,
        footerConfig: FooterConfig = FooterConfig()
    ): PdfResult {
        if (outputFile.exists()) outputFile.delete()

        val urls = pageImageUrls.filter { it.startsWith("http://") || it.startsWith("https://") }
        val pdf = PdfDocument()
        var pageCount = 0
        var currentPageNumber = 0

        urls.forEach { url ->
            currentPageNumber += 1
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            val page = pdf.startPage(info)
            val canvas = page.canvas

            val bmp = downloadBitmap(url)
            val contentRect = RectF(marginH, marginV, pageWidth - marginH, pageHeight - footerHeight - footerGap)

            if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                val dst = fitInsideRect(contentRect, bmp.width, bmp.height)
                canvas.drawBitmap(bmp, null, dst, null)
            } else {
                // عنصر نائب رمادي
                val phPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EEEEEE") }
                canvas.drawRect(contentRect, phPaint)
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#CCCCCC"); style = Paint.Style.STROKE; strokeWidth = 3f
                }
                canvas.drawRect(contentRect, border)
            }

            // خط نهاية الصفحة + التذييل
            val lineY = pageHeight - footerHeight - footerGap
            canvas.drawLine(marginH, lineY, pageWidth - marginH, lineY, linePaint)
            val baseY = pageHeight - marginV / 2f
            footerPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${footerConfig.leftPageLabelArabic} $currentPageNumber", marginH, baseY, footerPaint)
            footerPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(footerConfig.rightText, pageWidth - marginH, baseY, footerPaint)

            pdf.finishPage(page)
            pageCount++
        }

        FileOutputStream(outputFile).use { fos -> pdf.writeTo(fos) }
        pdf.close()
        return PdfResult(outputFile, pageCount)
    }

    // ==========================================================
    // (3) جديد: عرض صفحات "site_pages" داخل منطقة الصور فقط (بدون قص)
    // ==========================================================

    /**
     * يرسم كل صورة صفحة مركّبة داخل **منطقة الصور** المحددة (المربع الأحمر)
     * باستخدام fit-inside بدون أي قص بعد الحفظ.
     *
     * @param outputFile ملف الإخراج.
     * @param pageImageUrls روابط صور الصفحات (A4 عموديّة محفوظة مسبقًا).
     * @param usedTopAreaPx الارتفاع المستهلك أعلى الصفحة (رأس+عنوان+ميتا) بالبكسل.
     * @param marginPx هامش الصفحة (يمين/يسار/أعلى) بالبكسل.
     * @param footerBlockPx ارتفاع كتلة التذييل (يتضمن الخط والنص) بالبكسل.
     */
    fun renderSitePagesIntoPhotoArea(
        outputFile: File,
        pageImageUrls: List<String>,
        usedTopAreaPx: Int,
        marginPx: Int = marginH.toInt(),
        footerBlockPx: Int = (footerHeight + footerGap).toInt(),
        footerConfig: FooterConfig = FooterConfig()
    ): PdfResult {
        if (outputFile.exists()) outputFile.delete()

        val urls = pageImageUrls.filter { it.startsWith("http://") || it.startsWith("https://") }
        val pdf = PdfDocument()
        var pageCount = 0
        var currentPageNumber = 0

        urls.forEach { url ->
            currentPageNumber += 1
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            val page = pdf.startPage(info)
            val canvas = page.canvas

            // احسب مستطيل منطقة الصور داخل هذه الصفحة
            val photoArea = PdfGridLayout.getPhotoAreaRect(
                pageWidthPx = pageWidth,
                pageHeightPx = pageHeight,
                usedTopAreaPx = usedTopAreaPx,
                marginPx = marginPx,
                footerBlockPx = footerBlockPx
            )

            val bmp = downloadBitmap(url)
            if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                val dst = fitInsideRect(photoArea, bmp.width, bmp.height)
                canvas.drawBitmap(bmp, null, dst, null)
            } else {
                val phPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EEEEEE") }
                canvas.drawRect(photoArea, phPaint)
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#CCCCCC"); style = Paint.Style.STROKE; strokeWidth = 3f
                }
                canvas.drawRect(photoArea, border)
            }

            // خط نهاية الصفحة + التذييل
            val lineY = pageHeight - footerHeight - footerGap
            canvas.drawLine(marginH, lineY, pageWidth - marginH, lineY, linePaint)
            val baseY = pageHeight - marginV / 2f
            footerPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("${footerConfig.leftPageLabelArabic} $currentPageNumber", marginH, baseY, footerPaint)
            footerPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(footerConfig.rightText, pageWidth - marginH, baseY, footerPaint)

            pdf.finishPage(page)
            pageCount++
        }

        FileOutputStream(outputFile).use { fos -> pdf.writeTo(fos) }
        pdf.close()
        return PdfResult(outputFile, pageCount)
    }

    // ========================= أدوات مساعدة ========================= //

    private fun drawMetaKeyValues(
        canvas: Canvas,
        meta: List<Pair<String, String>>,
        left: Float,
        right: Float,
        startY: Float
    ): Float {
        var y = startY
        val lineHeight = metaPaint.textSize * 1.5f
        meta.forEach { (k, v) ->
            val line = "$k: $v"
            val p = if (isProjectNameKey(k)) metaSmallPaint else metaPaint
            canvas.drawText(line, right, y + p.textSize, p)
            y += lineHeight
        }
        return y
    }

    private fun isProjectNameKey(key: String): Boolean {
        val normalized = key.trim()
        return normalized == "اسم المشروع" || normalized.equals("Project Name", true)
    }

    private fun extractDate(meta: List<Pair<String, String>>): String? {
        val keys = listOf("تاريخ التقرير", "التاريخ", "Date", "Report Date")
        return meta.firstOrNull { it.first.trim() in keys }?.second?.takeIf { it.isNotBlank() }
    }

    private fun drawBitmapCenterCropSquare(canvas: Canvas, bmp: Bitmap, dst: RectF) {
        val side = kotlin.math.min(bmp.width, bmp.height)
        val left = (bmp.width - side) / 2
        val top = (bmp.height - side) / 2
        val square = Bitmap.createBitmap(bmp, left, top, side, side)
        val scaled = if (square.width == dst.width().toInt() && square.height == dst.height().toInt()) square
        else Bitmap.createScaledBitmap(square, dst.width().toInt(), dst.height().toInt(), true)
        canvas.drawBitmap(scaled, null, dst, null)
    }

    private fun fitWidthRect(srcW: Int, srcH: Int, targetW: Float): RectF {
        val scale = targetW / srcW
        val h = srcH * scale
        return RectF(0f, 0f, targetW, h)
    }

    private fun fitInsideRect(container: RectF, imgW: Int, imgH: Int): RectF {
        if (imgW <= 0 || imgH <= 0) return container
        val scale = kotlin.math.min(container.width() / imgW, container.height() / imgH)
        val w = kotlin.math.max(1f, imgW * scale)
        val h = kotlin.math.max(1f, imgH * scale)
        val left = container.left + (container.width() - w) / 2f
        val top = container.top + (container.height() - h) / 2f
        return RectF(left, top, left + w, top + h)
    }

    private fun downloadBitmap(url: String): Bitmap? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            doInput = true
        }
        try {
            conn.connect()
            conn.inputStream.use { input -> BitmapFactory.decodeStream(input) }
        } finally { conn.disconnect() }
    }.getOrNull()
}
