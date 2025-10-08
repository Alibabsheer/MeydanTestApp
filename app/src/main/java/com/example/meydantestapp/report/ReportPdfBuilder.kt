package com.example.meydantestapp.report

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.meydantestapp.R
import com.example.meydantestapp.utils.ImageUtils
import com.example.meydantestapp.utils.PdfBidiUtils
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val REPORT_INFO_PLACEHOLDER = "—"

/**
 * ReportPdfBuilder – توليد PDF بقياس A4 (عمودي) بالرسم اليدوي عبر Canvas.
 *
 * ✅ حسب الخطة الاستثنائية:
 * 1) إن كانت `sitepages` موجودة: تُعرَض داخل **قسم صور التقرير اليومي** (المساحة المخصصة)
 *    باستخدام Fit-Inside **بدون قص**، صفحة PDF لكل صورة صفحة.
 * 2) إن لم توجد `sitepages`: نرجع لمسار الصور القديمة (شبكة 16:9).
 * 3) دعم RTL وخط عربي موحّد.
 */
class ReportPdfBuilder(
    private val context: Context,
    private val pageWidth: Int = 2480,   // A4 @ 300dpi
    private val pageHeight: Int = 3508,
    private val pageMarginMm: Float = 22f,
    private val fieldHorizontalPaddingPt: Float = 5f,
    private val fieldVerticalPaddingPt: Float = 3.5f,
    private val fieldLineSpacingPt: Float = 6f
) {

    private val basePageWidth = 595f
    private val basePageHeight = 842f
    private val pageScale: Float = min(
        pageWidth / basePageWidth,
        pageHeight / basePageHeight
    ).coerceAtLeast(1f)

    private val pointsPerMillimeter = 72f / 25.4f

    private fun dp(value: Int): Int = (value * pageScale).roundToInt()
    private fun dpF(value: Float): Float = value * pageScale
    private fun sp(value: Float): Float = value * pageScale
    private fun mmToPoints(valueMm: Float): Float = valueMm * pointsPerMillimeter
    private fun pxFromMm(valueMm: Float): Int = dpF(mmToPoints(valueMm)).roundToInt()
    private fun pxFromPt(valuePt: Float): Int = dpF(valuePt).roundToInt()

    /* ---------- نموذج بيانات التقرير ---------- */
    data class DailyReport(
        val organizationName: String? = null,
        val projectName: String? = null,
        val ownerName: String? = null,
        val contractorName: String? = null,
        val consultantName: String? = null,
        val projectLocation: String? = null,
        val projectLocationGoogleMapsUrl: String? = null,
        val reportNumber: String? = null,
        val dateText: String? = null,

        // الطقس (حقول منفصلة أو نص قديم)
        val temperatureC: String? = null,
        val weatherCondition: String? = null,
        val weatherText: String? = null,

        val createdBy: String? = null,

        val dailyActivities: List<String>? = null,
        val skilledLabor: String? = null,
        val unskilledLabor: String? = null,
        val totalLabor: String? = null,
        val resourcesUsed: List<String>? = null,
        val challenges: List<String>? = null,
        val notes: List<String>? = null,

        // صور قديمة
        val photoUrls: List<String>? = null,
        val site_photos: List<String>? = null, // توافق قديم

        // الصفحات المركّبة الجاهزة (عمودية) — الاسم الجديد بدون شرطات
        val sitepages: List<String>? = null
    )

    /* ---------- تنزيل صورة عبر HTTP ---------- */
    private fun downloadBmp(url: String): Bitmap? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            doInput = true
            setRequestProperty("Accept", "image/avif,image/webp,image/*;q=0.8")
        }
        try {
            conn.connect()
            conn.inputStream.use { input ->
                val bytes = input.readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val targetMax = max(pageWidth, pageHeight)
                val maxDim = max(2048, targetMax)
                var sample = 1
                while ((bounds.outWidth / sample) > maxDim || (bounds.outHeight / sample) > maxDim) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    ?: return@use null
                val cap = (targetMax * 1.1f).roundToInt()
                val needsScale = decoded.width > cap || decoded.height > cap
                if (!needsScale) {
                    decoded
                } else {
                    val scale = min(cap.toFloat() / decoded.width.toFloat(), cap.toFloat() / decoded.height.toFloat())
                    val w = max(1f, decoded.width * scale).roundToInt()
                    val h = max(1f, decoded.height * scale).roundToInt()
                    Bitmap.createScaledBitmap(decoded, w, h, true).also {
                        if (it !== decoded) {
                            decoded.recycle()
                        }
                    }
                }
            }
        } finally { conn.disconnect() }
    }.getOrNull()

    private fun isHttpUrl(s: String?): Boolean =
        !s.isNullOrBlank() && (s.startsWith("http://") || s.startsWith("https://"))

    /* ---------- أدوات نص RTL ---------- */
    private fun arabicPaint(base: TextPaint.() -> Unit): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            val tf = ImageUtils.appTypeface(context)
                ?: runCatching { ResourcesCompat.getFont(context, R.font.rb) }.getOrNull()
                ?: Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            typeface = tf
            textLocale = Locale("ar")
            isLinearText = true
            setHinting(Paint.HINTING_ON)
            base()
        }

    private fun normalizeArabicCommaSpacing(input: String): String {
        if (input.isEmpty()) return input
        var text = input.replace(',', '،')
        text = text.replace(Regex("\\s+،"), "،")
        if (!text.contains('،')) return text
        val sb = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch == '،') {
                sb.append('،')
                index++
                while (index < text.length && text[index].isWhitespace()) {
                    index++
                }
                if (index < text.length) {
                    sb.append(' ')
                }
            } else {
                sb.append(ch)
                index++
            }
        }
        return sb.toString()
    }

    private fun createLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        rtl: Boolean,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        spacingMult: Float = 1.08f,
        spacingAdd: Float = 0f,
        includePad: Boolean = false,
        maxLines: Int = Int.MAX_VALUE
    ): StaticLayout {
        val dir = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setIncludePad(includePad)
            .setLineSpacing(spacingAdd, spacingMult)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setTextDirection(dir)
            .setMaxLines(maxLines)
            .build()
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: CharSequence,
        paint: TextPaint,
        left: Int,
        top: Int,
        width: Int,
        rtl: Boolean,
        spacingMult: Float = 1.08f
    ): Int {
        val layout = createLayout(text, paint, width, rtl, spacingMult = spacingMult)
        canvas.save()
        canvas.translate(left.toFloat(), top.toFloat())
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    private fun parseWeatherFromCombined(text: String?): Pair<String?, String?> {
        if (text.isNullOrBlank()) return null to null
        val tempRegex = Regex("درجة\\s*الحرارة\\s*[:：]?\\s*([0-9]+)")
        val condRegex = Regex("حالة\\s*الطقس\\س*[:：]?\\s*([\\u0621-\\u064A\\sA-Za-z]+)")
        val temp = tempRegex.find(text)?.groupValues?.getOrNull(1)
        val cond = condRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        return temp to cond
    }

    private fun fitInsideRect(container: Rect, imgW: Int, imgH: Int): Rect {
        if (imgW <= 0 || imgH <= 0) return container
        val scale = min(container.width().toFloat() / imgW, container.height().toFloat() / imgH)
        val w = max(1f, imgW * scale).toInt()
        val h = max(1f, imgH * scale).toInt()
        val left = container.left + (container.width() - w) / 2
        val top = container.top + (container.height() - h) / 2
        return Rect(left, top, left + w, top + h)
    }

    private fun prepareBitmapForRect(bitmap: Bitmap, target: Rect): Bitmap {
        val targetW = target.width().coerceAtLeast(1)
        val targetH = target.height().coerceAtLeast(1)
        if (bitmap.width <= targetW && bitmap.height <= targetH) return bitmap
        val scale = min(targetW.toFloat() / bitmap.width.toFloat(), targetH.toFloat() / bitmap.height.toFloat())
        val w = max(1f, bitmap.width * scale).roundToInt()
        val h = max(1f, bitmap.height * scale).roundToInt()
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /* ---------- إنشاء PDF ---------- */
    fun buildPdf(data: DailyReport, logo: Bitmap?, outFile: File): File {
        val pdf = PdfDocument()

        /* ========== تهيئة ألوان وخطوط ========== */
        val maroon = ContextCompat.getColor(context, R.color.brand_red_light_theme)
        val black = ContextCompat.getColor(context, R.color.black)
        val white = Color.WHITE
        val hyperlinkBlue = Color.parseColor("#0B57D0")

        val titlePaint = arabicPaint {
            color = maroon
            textSize = sp(13f)
            typeface = Typeface.create(typeface, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = arabicPaint {
            color = maroon
            textSize = sp(11f)
            typeface = Typeface.create(typeface, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val bodyPaint = arabicPaint {
            color = black
            textSize = sp(10.5f)
            textAlign = Paint.Align.RIGHT
        }
        val footerPaintLeft = arabicPaint {
            color = black
            textSize = sp(8f)
            textAlign = Paint.Align.LEFT
        }
        val footerPaintRight = arabicPaint {
            color = black
            textSize = sp(8f)
            textAlign = Paint.Align.RIGHT
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = dpF(1f)
        }
        val footerDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = dpF(1.5f)
        }
        val invisibleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            style = Paint.Style.STROKE
            strokeWidth = dpF(1f)
        }

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

        val defaultLogo = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.default_logo) }.getOrNull()
        val headerLogo: Bitmap = logo ?: defaultLogo ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        val marginPx = pxFromMm(pageMarginMm)
        val contentLeft = marginPx
        val contentRight = pageWidth - marginPx
        val contentWidth = contentRight - contentLeft
        val footerBlockHeight = dp(28)

        val fieldHorizontalPadding = fieldHorizontalPaddingPt.let { pxFromPt(it).coerceAtLeast(1) }
        val fieldVerticalPadding = fieldVerticalPaddingPt.let { pxFromPt(it).coerceAtLeast(1) }
        val fieldLineSpacing = fieldLineSpacingPt.let { pxFromPt(it).coerceAtLeast(1) }
        val fieldLineSpacingAdd = dpF(fieldLineSpacingPt)

        var pageIndex = 0
        lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = 0
        var currentSectionTitle: String? = null

        fun bottomLimit() = pageHeight - marginPx - footerBlockHeight

        fun startPageWithHeader() {
            pageIndex += 1
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
            page = pdf.startPage(pageInfo)
            canvas = page.canvas
            y = marginPx

            val headerH = dp(90)
            val dstRect = Rect(0, y, pageWidth, y + headerH)
            canvas.drawColor(Color.WHITE)
            val scale = min(
                dstRect.width().toFloat() / headerLogo.width.toFloat(),
                dstRect.height().toFloat() / headerLogo.height.toFloat()
            )
            val drawW = (headerLogo.width * scale).toInt().coerceAtLeast(1)
            val drawH = (headerLogo.height * scale).toInt().coerceAtLeast(1)
            val left = dstRect.left + (dstRect.width() - drawW) / 2
            val top = dstRect.top + (dstRect.height() - drawH) / 2
            val drawBmp = Bitmap.createScaledBitmap(headerLogo, drawW, drawH, true)
            canvas.drawBitmap(drawBmp, left.toFloat(), top.toFloat(), bitmapPaint)

            y += headerH + dp(6)
            val wrappedTitle = PdfBidiUtils.wrapMixed("التقرير اليومي", rtlBase = true).toString()
            canvas.drawText(wrappedTitle, (pageWidth / 2f), y + titlePaint.textSize, titlePaint)
            y += (titlePaint.fontMetrics.bottom - titlePaint.fontMetrics.top).roundToInt() + dp(4)
        }

        fun finishPage() {
            val lineY = pageHeight - marginPx - footerBlockHeight + dp(4)
            canvas.drawLine(contentLeft.toFloat(), lineY.toFloat(), contentRight.toFloat(), lineY.toFloat(), footerDividerPaint)

            val baseY = pageHeight - marginPx.toFloat()
            val pageLabel = PdfBidiUtils.wrapMixed("صفحة $pageIndex", rtlBase = true).toString()
            val footerLabel = PdfBidiUtils.wrapMixed("تم إنشاء التقرير في تطبيق ميدان", rtlBase = true).toString()
            canvas.drawText(pageLabel, contentLeft.toFloat(), baseY, footerPaintLeft)
            canvas.drawText(footerLabel, contentRight.toFloat(), baseY, footerPaintRight)

            pdf.finishPage(page)
        }

        fun ensureSpace(required: Int): Boolean {
            return if (y + required > bottomLimit()) {
                finishPage()
                startPageWithHeader()
                true
            } else {
                false
            }
        }

        fun drawSectionHeader(text: String) {
            currentSectionTitle = text
            val h = (headerPaint.fontMetrics.bottom - headerPaint.fontMetrics.top).roundToInt()
            if (ensureSpace(h + dp(4))) {
                // إذا انتقلنا لصفحة جديدة، نضمن تكرار العنوان الحالي قبل رسم المحتوى التالي.
                currentSectionTitle = text
            }
            val wrappedHeader = PdfBidiUtils.wrapMixed(text, rtlBase = true).toString()
            canvas.drawText(wrappedHeader, contentRight.toFloat(), y + headerPaint.textSize, headerPaint)
            y += h + dp(2)
        }

        fun endSectionDivider() {
            val ly = y + dp(2)
            canvas.drawLine(contentLeft.toFloat(), ly.toFloat(), contentRight.toFloat(), ly.toFloat(), dividerPaint)
            y += dp(6)
            currentSectionTitle = null
        }

        fun layoutFits(layout: StaticLayout, maxWidth: Int): Boolean {
            for (line in 0 until layout.lineCount) {
                val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
                if (lineWidth > maxWidth + 0.5f) return false
            }
            return true
        }

        data class AutoSizedLayout(val layout: StaticLayout)

        fun autoSizeText(
            text: CharSequence,
            basePaint: TextPaint,
            width: Int,
            rtl: Boolean,
            maxLines: Int,
            align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
        ): AutoSizedLayout {
            val minSizePx = sp(9.5f)
            val maxSizePx = min(sp(16f), basePaint.textSize)
            val stepPx = sp(1f).coerceAtLeast(1f)
            var size = maxSizePx
            while (size >= minSizePx) {
                val paint = TextPaint(basePaint)
                paint.textSize = size
                val layout = createLayout(
                    text = text,
                    paint = paint,
                    width = width,
                    rtl = rtl,
                    align = align,
                    spacingMult = 1f,
                    spacingAdd = fieldLineSpacingAdd,
                    maxLines = maxLines
                )
                if (layout.lineCount <= maxLines && layoutFits(layout, width)) {
                    return AutoSizedLayout(layout)
                }
                size -= stepPx
            }
            val paint = TextPaint(basePaint)
            paint.textSize = minSizePx
            val fallback = createLayout(
                text = text,
                paint = paint,
                width = width,
                rtl = rtl,
                align = align,
                spacingMult = 1f,
                spacingAdd = fieldLineSpacingAdd,
                maxLines = maxLines
            )
            return AutoSizedLayout(fallback)
        }

        fun drawKeyValue(label: String, valueRaw: String?, linkUrlRaw: String? = null) {
            val horizontalGap = dp(10)
            val horizontalPadding = fieldHorizontalPadding
            val verticalPadding = fieldVerticalPadding
            val minValueWidth = dp(120)

            val sanitized = valueRaw?.trim()?.takeIf { it.isNotEmpty() }
            val normalizedValue = sanitized?.let { value ->
                if (PdfBidiUtils.isArabicLikely(value)) normalizeArabicCommaSpacing(value) else value
            }
            val displayValue = normalizedValue ?: sanitized ?: REPORT_INFO_PLACEHOLDER
            val wrappedLabel = PdfBidiUtils.wrapMixed(label, rtlBase = true)
            val wrappedValue = PdfBidiUtils.wrapMixed(displayValue, rtlBase = true)

            val linkUrl = if (sanitized != null) {
                linkUrlRaw?.trim()?.takeIf { it.isNotEmpty() && isHttpUrl(it) }
            } else {
                null
            }

            val minLabelWidth = (contentWidth * 0.35f).roundToInt()
            val maxLabelWidth = (contentWidth * 0.45f).roundToInt()
            val idealLabelWidth = (contentWidth * 0.4f).roundToInt()
            val maxLabelAllowed = (contentWidth - horizontalGap - minValueWidth).coerceAtLeast(minLabelWidth)

            var labelWidth = idealLabelWidth.coerceIn(minLabelWidth, maxLabelWidth)
            labelWidth = labelWidth.coerceAtMost(maxLabelAllowed)

            var valueWidth = contentWidth - labelWidth - horizontalGap
            if (valueWidth < minValueWidth) {
                valueWidth = minValueWidth.coerceAtMost(contentWidth - horizontalGap)
                labelWidth = (contentWidth - horizontalGap - valueWidth).coerceIn(minLabelWidth, maxLabelWidth)
                valueWidth = contentWidth - labelWidth - horizontalGap
            }
            if (valueWidth <= 0) {
                valueWidth = max(1, contentWidth - horizontalGap - labelWidth)
            }

            val labelAreaWidth = max(1, labelWidth - horizontalPadding * 2)
            val valueAreaWidth = max(1, valueWidth - horizontalPadding * 2)

            val labelPaint = TextPaint(bodyPaint).apply {
                typeface = Typeface.create(typeface, Typeface.BOLD)
                textSize = sp(11f)
            }
            val valuePaintBase = TextPaint(bodyPaint).apply {
                if (linkUrl != null) {
                    color = hyperlinkBlue
                    isUnderlineText = true
                }
            }

            val labelLayout = autoSizeText(
                wrappedLabel,
                labelPaint,
                labelAreaWidth,
                rtl = true,
                maxLines = 2,
                align = Layout.Alignment.ALIGN_CENTER
            ).layout
            val valueLayout = autoSizeText(
                wrappedValue,
                valuePaintBase,
                valueAreaWidth,
                rtl = true,
                maxLines = 4,
                align = Layout.Alignment.ALIGN_CENTER
            ).layout

            val rowHeight = max(labelLayout.height, valueLayout.height) + verticalPadding * 2
            val requiredHeight = rowHeight + fieldLineSpacing
            var attempts = 0
            while (ensureSpace(requiredHeight)) {
                val title = currentSectionTitle
                if (title != null && attempts < 3) {
                    drawSectionHeader(title)
                    attempts++
                } else {
                    break
                }
            }

            val valueLeft = contentLeft
            val labelLeft = contentRight - labelWidth
            val availableHeight = rowHeight - verticalPadding * 2
            val labelTop = y + verticalPadding + ((availableHeight - labelLayout.height) / 2f).coerceAtLeast(0f)
            val valueTop = y + verticalPadding + ((availableHeight - valueLayout.height) / 2f).coerceAtLeast(0f)

            canvas.save()
            canvas.translate((labelLeft + horizontalPadding).toFloat(), labelTop)
            labelLayout.draw(canvas)
            canvas.restore()

            canvas.save()
            canvas.translate((valueLeft + horizontalPadding).toFloat(), valueTop)
            valueLayout.draw(canvas)
            canvas.restore()

            if (linkUrl != null && valueLayout.height > 0) {
                val leftF = (valueLeft + horizontalPadding).toFloat()
                val topF = valueTop
                val rightLimit = leftF + valueAreaWidth.toFloat()
                val rightF = min(leftF + valueLayout.width.toFloat(), rightLimit)
                val bottomF = topF + valueLayout.height.toFloat()
                val rect = RectF(leftF, topF, rightF, bottomF)
                if (rect.intersect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())) {
                    PdfLinkAnnotationSupport.addLink(page, rect, linkUrl)
                }
            }

            y += rowHeight + fieldLineSpacing
        }

        fun drawReportInfoTable(entries: List<ReportInfoEntry>) {
            if (entries.isEmpty()) {
                endSectionDivider()
                return
            }

            val horizontalPadding = fieldHorizontalPadding
            val verticalPadding = fieldVerticalPadding

            val minLabelWidth = (contentWidth * 0.35f).roundToInt()
            val maxLabelWidth = (contentWidth * 0.4f).roundToInt()
            val minValueWidth = (contentWidth * 0.6f).roundToInt()

            var labelWidth = (contentWidth * 0.38f).roundToInt().coerceIn(minLabelWidth, maxLabelWidth)
            var valueWidth = contentWidth - labelWidth
            if (valueWidth < minValueWidth) {
                valueWidth = minValueWidth.coerceAtMost(contentWidth - minLabelWidth)
                labelWidth = (contentWidth - valueWidth).coerceIn(minLabelWidth, maxLabelWidth)
                valueWidth = contentWidth - labelWidth
            }
            if (valueWidth <= 0) {
                valueWidth = max(1, contentWidth - labelWidth)
            }

            val labelAreaWidth = max(1, labelWidth - horizontalPadding * 2)
            val valueAreaWidth = max(1, valueWidth - horizontalPadding * 2)

            val labelPaint = TextPaint(bodyPaint).apply {
                typeface = Typeface.create(typeface, Typeface.BOLD)
            }
            val valuePaintBase = TextPaint(bodyPaint)

            val tableBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D0D0D0")
                strokeWidth = dpF(0.8f)
                style = Paint.Style.STROKE
            }
            val tableDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E4E4E4")
                strokeWidth = dpF(0.8f)
            }
            val alternateRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FAFAFA")
                style = Paint.Style.FILL
            }

            val labelLeft = contentRight - labelWidth
            val valueLeft = contentLeft
            val columnDividerX = labelLeft

            var firstRowOnPage = true

            entries.forEachIndexed { index, entry ->
                val trimmedValue = entry.value.trim()
                val isPlaceholder = trimmedValue == REPORT_INFO_PLACEHOLDER
                val normalizedValue = if (!isPlaceholder && PdfBidiUtils.isArabicLikely(trimmedValue)) {
                    normalizeArabicCommaSpacing(trimmedValue)
                } else {
                    trimmedValue
                }
                val wrappedLabel = PdfBidiUtils.wrapMixed(entry.label, rtlBase = true)
                val wrappedValue = PdfBidiUtils.wrapMixed(normalizedValue, rtlBase = true)

                val linkUrl = if (!isPlaceholder) {
                    entry.linkUrl?.trim()?.takeIf { it.isNotEmpty() && isHttpUrl(it) }
                } else {
                    null
                }

                val valuePaint = TextPaint(valuePaintBase).apply {
                    if (linkUrl != null) {
                        color = hyperlinkBlue
                        isUnderlineText = true
                    }
                }

                val labelLayout = autoSizeText(
                    wrappedLabel,
                    labelPaint,
                    labelAreaWidth,
                    rtl = true,
                    maxLines = 2,
                    align = Layout.Alignment.ALIGN_CENTER
                ).layout
                val valueLayout = autoSizeText(
                    wrappedValue,
                    valuePaint,
                    valueAreaWidth,
                    rtl = true,
                    maxLines = 6,
                    align = Layout.Alignment.ALIGN_CENTER
                ).layout

                val rowHeight = max(labelLayout.height, valueLayout.height) + verticalPadding * 2
                val requiredHeight = rowHeight + dp(1)
                var attempts = 0
                while (ensureSpace(requiredHeight)) {
                    val title = currentSectionTitle
                    if (title != null && attempts < 3) {
                        drawSectionHeader(title)
                        attempts++
                    } else {
                        break
                    }
                    firstRowOnPage = true
                }

                val rowTop = y
                val rowBottom = rowTop + rowHeight

                if (index % 2 == 1) {
                    canvas.drawRect(
                        valueLeft.toFloat(),
                        rowTop.toFloat(),
                        contentRight.toFloat(),
                        rowBottom.toFloat(),
                        alternateRowPaint
                    )
                }

                val leftF = valueLeft.toFloat()
                val rightF = contentRight.toFloat()
                val topF = rowTop.toFloat()
                val bottomF = rowBottom.toFloat()

                if (firstRowOnPage) {
                    canvas.drawLine(leftF, topF, rightF, topF, tableBorderPaint)
                    firstRowOnPage = false
                }

                canvas.drawLine(leftF, bottomF, rightF, bottomF, tableBorderPaint)
                canvas.drawLine(leftF, topF, leftF, bottomF, tableBorderPaint)
                canvas.drawLine(rightF, topF, rightF, bottomF, tableBorderPaint)
                canvas.drawLine(columnDividerX.toFloat(), topF, columnDividerX.toFloat(), bottomF, tableDividerPaint)

                val availableHeight = rowHeight - verticalPadding * 2
                val labelTop = rowTop + verticalPadding + ((availableHeight - labelLayout.height) / 2f).coerceAtLeast(0f)
                val valueTop = rowTop + verticalPadding + ((availableHeight - valueLayout.height) / 2f).coerceAtLeast(0f)

                canvas.save()
                canvas.translate((labelLeft + horizontalPadding).toFloat(), labelTop)
                labelLayout.draw(canvas)
                canvas.restore()

                canvas.save()
                canvas.translate((valueLeft + horizontalPadding).toFloat(), valueTop)
                valueLayout.draw(canvas)
                canvas.restore()

                if (linkUrl != null && valueLayout.height > 0) {
                    val leftLink = (valueLeft + horizontalPadding).toFloat()
                    val rightLimit = leftLink + valueAreaWidth.toFloat()
                    val rightLink = min(leftLink + valueLayout.width.toFloat(), rightLimit)
                    val rect = RectF(
                        leftLink,
                        valueTop,
                        rightLink,
                        valueTop + valueLayout.height
                    )
                    if (rect.intersect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())) {
                        PdfLinkAnnotationSupport.addLink(page, rect, linkUrl)
                    }
                }

                y = rowBottom
            }

            y += fieldLineSpacing
            endSectionDivider()
        }

        fun drawBulletedSection(title: String, items: List<String>?) {
            val list = items?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            drawSectionHeader(title)
            if (list.isEmpty()) { endSectionDivider(); return }

            val bulletIndent = dp(14)
            val bulletGap = dp(8)
            val bulletRadius = dpF(2.6f)
            val bulletRadiusInt = bulletRadius.roundToInt()
            val itemSpacing = fieldLineSpacing
            val bulletPaint = Paint(bodyPaint).apply { style = Paint.Style.FILL }

            list.forEach { rawItem ->
                val trimmed = rawItem.trim()
                val itemIsRtl = PdfBidiUtils.isArabicLikely(trimmed)
                val wrappedItem = if (itemIsRtl) {
                    val withComma = normalizeArabicCommaSpacing(trimmed)
                    PdfBidiUtils.wrapMixed(withComma, rtlBase = true)
                } else {
                    PdfBidiUtils.wrapMixed(trimmed, rtlBase = false)
                }

                val rtl = itemIsRtl
                val textLeft: Int
                val layoutWidth: Int
                val bulletCenterX: Float
                if (!itemIsRtl) {
                    bulletCenterX = (contentLeft + bulletIndent).toFloat()
                    textLeft = contentLeft + bulletIndent + bulletRadiusInt + bulletGap
                    layoutWidth = (contentRight - textLeft).coerceAtLeast(1)
                } else {
                    bulletCenterX = (contentRight - bulletIndent).toFloat()
                    val textRight = contentRight - (bulletIndent + bulletRadiusInt + bulletGap)
                    layoutWidth = (textRight - contentLeft).coerceAtLeast(1)
                    textLeft = contentLeft
                }

                val layout = createLayout(
                    wrappedItem,
                    bodyPaint,
                    layoutWidth,
                    rtl = rtl,
                    spacingMult = 1f,
                    spacingAdd = fieldLineSpacingAdd
                )

                while (ensureSpace(layout.height + itemSpacing)) {
                    drawSectionHeader(title)
                }

                val firstLineTop = layout.getLineTop(0)
                val firstLineBottom = layout.getLineBottom(0)
                val firstLineHeight = (firstLineBottom - firstLineTop).coerceAtLeast(1)
                val bulletCenterY = y + firstLineHeight / 2f

                canvas.drawCircle(bulletCenterX, bulletCenterY, bulletRadius, bulletPaint)

                canvas.save()
                canvas.translate(textLeft.toFloat(), y.toFloat())
                layout.draw(canvas)
                canvas.restore()

                y += layout.height + itemSpacing
            }
            endSectionDivider()
        }

        fun drawLabor(skilled: String?, unskilled: String?, total: String?) {
            val items = listOf(
                "عمالة ماهرة" to skilled,
                "عمالة غير ماهرة" to unskilled,
                "الإجمالي" to total
            )
            drawSectionHeader("العمالة")
            var rendered = false
            items.forEach { (label, value) ->
                val cleaned = value?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                drawKeyValue(label, cleaned)
                rendered = true
            }
            if (!rendered) {
                endSectionDivider()
                return
            }
            endSectionDivider()
        }

        // شبكة 16:9 – fallback فقط عند عدم وجود sitepages
        fun drawLegacyPhotos(urls: List<String>) {
            val all = urls.filter { isHttpUrl(it) }
            if (all.isEmpty()) return

            drawSectionHeader("صور التقرير اليومي")

            var index = 0
            while (index < all.size) {
                val remaining = all.size - index
                val rowsSpec = when (min(remaining, 9)) {
                    1 -> intArrayOf(1)
                    2 -> intArrayOf(1, 1)
                    3 -> intArrayOf(1, 1, 1)
                    4 -> intArrayOf(2, 2)
                    5 -> intArrayOf(2, 2, 1)
                    6 -> intArrayOf(2, 2, 2)
                    7 -> intArrayOf(3, 2, 2)
                    8 -> intArrayOf(3, 3, 2)
                    else -> intArrayOf(3, 3, 3)
                }
                val weights = rowsSpec.map { (rowsSpec.maxOrNull() ?: 2).toFloat() / it }.toFloatArray()

                val availableHeight = bottomLimit() - y
                if (availableHeight < dp(120)) { finishPage(); startPageWithHeader(); drawSectionHeader("صور التقرير اليومي") }

                val vGap = dp(8)
                val hGap = dp(8)
                val totalWeights = weights.sum()
                val totalVSpacing = vGap * (rowsSpec.size - 1)
                val unitH = (bottomLimit() - y - totalVSpacing) / totalWeights
                val rowHeights = weights.map { (unitH * it).toInt() }
                val startY = y

                var rowTop = startY
                for ((rowIdx, cols) in rowsSpec.withIndex()) {
                    val rowHeight = rowHeights[rowIdx]
                    val maxCols = cols
                    val totalColsSpacing = hGap * (maxCols - 1)
                    val cellWidthByCols = (contentWidth - totalColsSpacing) / maxCols
                    val maxCellWidthByHeight = (rowHeight * 16) / 9
                    val cellW = min(cellWidthByCols, maxCellWidthByHeight)
                    val cellH = (cellW * 9) / 16
                    val rowTotalWidth = maxCols * cellW + totalColsSpacing
                    val startX = contentLeft + (contentWidth - rowTotalWidth) / 2

                    for (c in 0 until cols) {
                        if (index >= all.size) break
                        val leftX = startX + c * (cellW + hGap)
                        val url = all[index]
                        val bmp = downloadBmp(url)

                        val dst = Rect(leftX, rowTop, leftX + cellW, rowTop + cellH)
                        if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                            // قص 16:9
                            val bw = bmp.width
                            val bh = bmp.height
                            val targetAspect = 16f / 9f
                            val bmpAspect = bw.toFloat() / bh
                            val src = if (bmpAspect > targetAspect) {
                                val newW = (bh * targetAspect).toInt()
                                val left = (bw - newW) / 2
                                Rect(left, 0, left + newW, bh)
                            } else {
                                val newH = (bw / targetAspect).toInt()
                                val top = (bh - newH) / 2
                                Rect(0, top, bw, top + newH)
                            }
                            canvas.drawBitmap(bmp, src, dst, bitmapPaint)
                        } else {
                            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.parseColor("#CCCCCC")
                                style = Paint.Style.STROKE
                                strokeWidth = dpF(2f)
                            }
                            canvas.drawRect(dst, p)
                        }
                        index++
                    }
                    rowTop += rowHeight + vGap
                }

                if (index < all.size) { finishPage(); startPageWithHeader(); drawSectionHeader("صور التقرير اليومي") } else { y = rowTop }
            }
        }

        // عرض الصفحات المركّبة (Fit-Inside داخل مساحة الصور)
        fun drawSitePagesSection(urls: List<String>) {
            urls.forEach { url ->
                startPageWithHeader()
                drawSectionHeader("صور التقرير اليومي")

                // مساحة الصور من الموضع الحالي حتى ما قبل التذييل
                val area = Rect(contentLeft, y, contentRight, bottomLimit())
                val bmp = downloadBmp(url)
                if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                    val dst = fitInsideRect(area, bmp.width, bmp.height)
                    val prepared = prepareBitmapForRect(bmp, dst)
                    canvas.drawBitmap(prepared, null, dst, bitmapPaint)
                } else {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#CCCCCC")
                        style = Paint.Style.STROKE
                        strokeWidth = dpF(3f)
                    }
                    canvas.drawRect(area, p)
                }

                finishPage()
            }
        }

        /* ========== بناء المستند ========== */
        // 1) صفحة المعلومات/النصوص
        startPageWithHeader()

        val (tempFromText, condFromText) = parseWeatherFromCombined(data.weatherText)
        val enrichedData = data.copy(
            temperatureC = data.temperatureC ?: tempFromText,
            weatherCondition = data.weatherCondition ?: condFromText
        )

        drawSectionHeader("معلومات التقرير")
        drawReportInfoTable(buildReportInfoEntries(enrichedData))

        drawBulletedSection("نشاطات المشروع", data.dailyActivities)
        drawLabor(data.skilledLabor, data.unskilledLabor, data.totalLabor)
        drawBulletedSection("الآلات والمعدات", data.resourcesUsed)
        drawBulletedSection("العوائق والتحديات", data.challenges)
        drawBulletedSection("الملاحظات", data.notes)

        finishPage()

        // 2) قسم الصور
        val sitePages = data.sitepages?.filter { isHttpUrl(it) }.orEmpty()
        if (sitePages.isNotEmpty()) {
            // ✅ استخدام الصفحات المركّبة داخل مساحة الصور (بدون قص)
            drawSitePagesSection(sitePages)
        } else {
            // 🔁 رجوع للسلوك القديم
            val legacyCombined = ((data.photoUrls ?: emptyList()) + (data.site_photos ?: emptyList()))
                .filter { isHttpUrl(it) }
            if (legacyCombined.isNotEmpty()) {
                startPageWithHeader()
                drawLegacyPhotos(legacyCombined)
                finishPage()
            }
        }

        // إخراج الملف
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        return outFile
    }

    private object PdfLinkAnnotationSupport {
        private var reflectionAvailable: Boolean? = null
        private var annotationConstructor: Constructor<*>? = null
        private var addAnnotationMethod: Method? = null

        fun addLink(page: PdfDocument.Page, rect: RectF, url: String) {
            if (rect.width() <= 0f || rect.height() <= 0f) return
            val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
            if (!ensureReflection()) return
            runCatching {
                val annotation = annotationConstructor?.newInstance(rect, uri) ?: return
                addAnnotationMethod?.invoke(page, annotation)
            }
        }

        private fun ensureReflection(): Boolean {
            reflectionAvailable?.let { return it }
            val available = runCatching {
                val annotationBase = Class.forName("android.graphics.pdf.PdfDocument\$Annotation")
                val linkAnnotation = Class.forName("android.graphics.pdf.PdfDocument\$LinkAnnotation")
                annotationConstructor = linkAnnotation.getConstructor(RectF::class.java, Uri::class.java)
                addAnnotationMethod = PdfDocument.Page::class.java.getMethod("addAnnotation", annotationBase)
                true
            }.getOrDefault(false)
            if (!available) {
                annotationConstructor = null
                addAnnotationMethod = null
            }
            reflectionAvailable = available
            return available
        }
    }
}

internal data class ReportInfoEntry(
    val label: String,
    val value: String,
    val linkUrl: String? = null
)

internal fun buildReportInfoEntries(data: ReportPdfBuilder.DailyReport): List<ReportInfoEntry> {
    fun String?.normalized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    fun entry(label: String, raw: String?, link: String? = null): ReportInfoEntry {
        val normalized = raw.normalized()
        val value = normalized ?: REPORT_INFO_PLACEHOLDER
        val url = if (normalized != null) link?.trim()?.takeIf { it.isNotEmpty() } else null
        return ReportInfoEntry(label, value, url)
    }

    return listOf(
        entry("اسم المشروع", data.projectName),
        entry("مالك المشروع", data.ownerName),
        entry("مقاول المشروع", data.contractorName),
        entry("الاستشاري", data.consultantName),
        entry("رقم التقرير", data.reportNumber),
        entry("التاريخ", data.dateText),
        entry("درجة الحرارة", data.temperatureC),
        entry("حالة الطقس", data.weatherCondition),
        entry("موقع المشروع", data.projectLocation, data.projectLocationGoogleMapsUrl),
        entry("تم إنشاء التقرير بواسطة", data.createdBy)
    )
}
