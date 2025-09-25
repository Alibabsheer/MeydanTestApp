package com.example.meydantestapp.report

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.meydantestapp.R
import com.example.meydantestapp.utils.ImageUtils
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    private val margin: Int = 24        // ≈ 8.5mm (قبل التحجيم)
) {

    private val basePageWidth = 595f
    private val basePageHeight = 842f
    private val pageScale: Float = min(
        pageWidth / basePageWidth,
        pageHeight / basePageHeight
    ).coerceAtLeast(1f)

    private fun dp(value: Int): Int = (value * pageScale).roundToInt()
    private fun dpF(value: Float): Float = value * pageScale
    private fun sp(value: Float): Float = value * pageScale

    /* ---------- نموذج بيانات التقرير ---------- */
    data class DailyReport(
        val organizationName: String? = null,
        val projectName: String? = null,
        val projectLocation: String? = null,
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

    private fun preferLTR(text: String): Boolean {
        for (ch in text) {
            if (ch.isLetterOrDigit()) {
                val code = ch.code
                if (code in '0'.code..'9'.code || code in 'A'.code..'Z'.code || code in 'a'.code..'z'.code
                    || ch == '+' || ch == '-' || ch == '/' || ch == ':' || ch == '#') return true
                return false
            }
        }
        return false
    }

    @SuppressLint("BidiSpoofing")
    private fun ltrIsolate(text: String): String = "⁦$text⁩" // U+2066..U+2069

    private fun createLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        rtl: Boolean,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        spacingMult: Float = 1.08f,
        spacingAdd: Float = 0f,
        includePad: Boolean = false
    ): StaticLayout {
        val dir = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setIncludePad(includePad)
            .setLineSpacing(spacingAdd, spacingMult)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setTextDirection(dir)
            .build()
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
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
            textSize = sp(9.5f)
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

        val marginPx = dp(margin)
        val contentLeft = marginPx
        val contentRight = pageWidth - marginPx
        val contentWidth = contentRight - contentLeft
        val footerBlockHeight = dp(28)

        var pageIndex = 0
        lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = 0

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
            canvas.drawText("التقرير اليومي", (pageWidth / 2f), y + titlePaint.textSize, titlePaint)
            y += (titlePaint.fontMetrics.bottom - titlePaint.fontMetrics.top).roundToInt() + dp(4)
        }

        fun finishPage() {
            val lineY = pageHeight - marginPx - footerBlockHeight + dp(4)
            canvas.drawLine(contentLeft.toFloat(), lineY.toFloat(), contentRight.toFloat(), lineY.toFloat(), footerDividerPaint)

            val baseY = pageHeight - marginPx.toFloat()
            canvas.drawText("صفحة $pageIndex", contentLeft.toFloat(), baseY, footerPaintLeft)
            canvas.drawText("تم إنشاء التقرير في تطبيق ميدان", contentRight.toFloat(), baseY, footerPaintRight)

            pdf.finishPage(page)
        }

        fun ensureSpace(required: Int) { if (y + required > bottomLimit()) { finishPage(); startPageWithHeader() } }

        fun drawSectionHeader(text: String) {
            val h = (headerPaint.fontMetrics.bottom - headerPaint.fontMetrics.top).roundToInt()
            ensureSpace(h + dp(4))
            canvas.drawText(text, contentRight.toFloat(), y + headerPaint.textSize, headerPaint)
            y += h + dp(2)
        }

        fun endSectionDivider() {
            val ly = y + dp(2)
            canvas.drawLine(contentLeft.toFloat(), ly.toFloat(), contentRight.toFloat(), ly.toFloat(), dividerPaint)
            y += dp(6)
        }

        fun drawKeyValue(label: String, valueRaw: String?) {
            if (valueRaw.isNullOrBlank()) return

            val gap = dp(6)
            val cellPad = dp(6)
            val labelColW = (contentWidth * 0.35f).toInt()
            val valueColW = contentWidth - labelColW - gap

            val labelText = "$label:"
            val v = valueRaw.trim()
            val vPrefLTR = preferLTR(v)
            val valueText = if (vPrefLTR) ltrIsolate(v) else v

            val labelLayout = createLayout(labelText, bodyPaint, labelColW - cellPad * 2, rtl = true)
            val valueLayout = createLayout(valueText, bodyPaint, valueColW - cellPad * 2, rtl = !vPrefLTR)

            val rowH = max(labelLayout.height, valueLayout.height) + cellPad * 2
            ensureSpace(rowH + dp(2))

            val labelRect = Rect(
                contentLeft + valueColW + gap, y,
                contentLeft + valueColW + gap + labelColW, y + rowH
            )
            val valueRect = Rect(
                contentLeft, y,
                contentLeft + valueColW, y + rowH
            )

            canvas.drawRect(labelRect, invisibleBorderPaint)
            canvas.drawRect(valueRect, invisibleBorderPaint)

            canvas.save()
            canvas.translate((labelRect.left + cellPad).toFloat(), (labelRect.top + cellPad).toFloat())
            labelLayout.draw(canvas)
            canvas.restore()

            canvas.save()
            canvas.translate((valueRect.left + cellPad).toFloat(), (valueRect.top + cellPad).toFloat())
            valueLayout.draw(canvas)
            canvas.restore()

            y += rowH + dp(2)
        }

        fun drawWeatherRow(tempC: String?, condition: String?) {
            val colPercents = floatArrayOf(0.22f, 0.28f, 0.22f, 0.28f)
            val cellPad = dp(6)

            val colWidths = IntArray(4)
            var used = 0
            for (i in 0 until 4) { colWidths[i] = (contentWidth * colPercents[i]).toInt(); used += colWidths[i] }
            colWidths[3] += (contentWidth - used)

            val labelTemp = "درجة الحرارة:"
            val valueTemp = (tempC?.trim()?.ifBlank { null })?.let { ltrIsolate("$it°C") } ?: "—"
            val labelCond = "حالة الطقس:"
            val valueCond = condition?.trim()?.ifBlank { "—" } ?: "—"

            val layouts = arrayOf(
                createLayout(labelTemp, bodyPaint, colWidths[0] - cellPad * 2, rtl = true),
                createLayout(valueTemp, bodyPaint, colWidths[1] - cellPad * 2, rtl = false),
                createLayout(labelCond, bodyPaint, colWidths[2] - cellPad * 2, rtl = true),
                createLayout(valueCond, bodyPaint, colWidths[3] - cellPad * 2, rtl = true)
            )

            val rowH = (layouts.maxOf { it.height }) + cellPad * 2
            ensureSpace(rowH + dp(2))

            var curLeft = contentLeft
            for (i in 0 until 4) {
                val rect = Rect(curLeft, y, curLeft + colWidths[i], y + rowH)
                canvas.drawRect(rect, invisibleBorderPaint)
                canvas.save()
                canvas.translate((rect.left + cellPad).toFloat(), (rect.top + cellPad).toFloat())
                layouts[i].draw(canvas)
                canvas.restore()
                curLeft += colWidths[i]
            }
            y += rowH + dp(2)
        }

        fun drawBulletedSection(title: String, items: List<String>?) {
            val list = items?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            drawSectionHeader(title)
            if (list.isEmpty()) { endSectionDivider(); return }
            list.forEach { item ->
                val cleaned = if (preferLTR(item)) item.replace(Regex("""([0-9A-Za-z+/:-]+)""")) { m -> ltrIsolate(m.value) } else item
                val h = drawWrapped(canvas, "• $cleaned", bodyPaint, contentLeft, y, contentWidth, rtl = true, spacingMult = 1.1f)
                ensureSpace(h)
                y += h + dp(1)
                if (y > bottomLimit() - dp(10)) { finishPage(); startPageWithHeader(); drawSectionHeader(title) }
            }
            endSectionDivider()
        }

        fun drawLabor(skilled: String?, unskilled: String?, total: String?) {
            val rows = listOfNotNull(
                skilled?.takeIf { it.isNotBlank() }?.let { "عمالة ماهرة: ${ltrIsolate(it.trim())}" },
                unskilled?.takeIf { it.isNotBlank() }?.let { "عمالة غير ماهرة: ${ltrIsolate(it.trim())}" },
                total?.takeIf { it.isNotBlank() }?.let { "الإجمالي: ${ltrIsolate(it.trim())}" }
            )
            drawSectionHeader("العمالة")
            if (rows.isEmpty()) { endSectionDivider(); return }
            rows.forEach { row ->
                val h = drawWrapped(canvas, row, bodyPaint, contentLeft, y, contentWidth, rtl = true, spacingMult = 1.05f)
                ensureSpace(h)
                y += h + dp(1)
                if (y > bottomLimit() - dp(10)) { finishPage(); startPageWithHeader(); drawSectionHeader("العمالة") }
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
        val tempToUse = data.temperatureC ?: tempFromText
        val condToUse = data.weatherCondition ?: condFromText

        drawSectionHeader("معلومات التقرير")
        drawKeyValue("اسم المؤسسة", data.organizationName)
        drawKeyValue("اسم المشروع", data.projectName)
        drawKeyValue("موقع المشروع", data.projectLocation)
        drawKeyValue("رقم التقرير", data.reportNumber)
        drawKeyValue("تاريخ التقرير", data.dateText)
        drawWeatherRow(tempToUse, condToUse)
        drawKeyValue("تم إنشاء التقرير بواسطة", data.createdBy)
        endSectionDivider()

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
}
