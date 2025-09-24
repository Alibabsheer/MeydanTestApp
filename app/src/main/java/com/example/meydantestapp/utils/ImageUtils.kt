package com.example.meydantestapp.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.exifinterface.media.ExifInterface
import com.example.meydantestapp.R
import com.example.meydantestapp.models.PhotoEntry
import com.example.meydantestapp.models.PhotoTemplate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ImageUtils – تجهيز/تركيب الصور للاستخدام داخل التقارير.
 *
 * الجديد (مهم لهدف القوالب العمودية بدون قص عند العرض):
 * - composeA4PortraitPage(..): يركّب صفحة بنسبة A4 عمودية (افتراضياً 1480×2100px)
 *   اعتماداً على القالب والخانات، مع **Fit-Inside** لكل صورة (بدون قص)،
 *   ويُضيف شريط تعليق أسفل كل خانة.
 * - compressToCacheWebp(..): حفظ WebP في cache وإرجاع Uri صالح للرفع.
 *
 * تحسينات أخرى:
 * - فكّ ترميز آمن مع تصحيح EXIF ودعم file://.
 * - جودة JPEG افتراضية 92، وWEBP افتراضية 90.
 */
object ImageUtils {

    private const val DEFAULT_MAX_DIM = 2560
    private const val DEFAULT_JPEG_QUALITY = 92
    private const val DEFAULT_WEBP_QUALITY = 90

    // ================ مواضع صندوق التراكب ================ //
    enum class OverlayGravity { TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT }

    // ================ فكّ الصورة مع تصحيح EXIF ================ //
    /**
     * فكّ Bitmap من Uri مع تصحيح EXIF وتقليل العيّنة لتقليل الذاكرة.
     * @param maxDim أكبر بُعد مسموح بعد فكّ الترميز.
     */
    fun decodeBitmap(resolver: ContentResolver, uri: Uri, maxDim: Int = DEFAULT_MAX_DIM): Bitmap? {
        return runCatching {
            // بعض المزودين لا يُرجعون نوع الميم؛ لا نجعل الفحص صارمًا.
            val type = runCatching { resolver.getType(uri) }.getOrNull()
            if (type != null && !type.startsWith("image")) return@runCatching null

            fun open(u: Uri): InputStream? {
                return if (u.scheme == "file") FileInputStream(u.path ?: return null) else resolver.openInputStream(u)
            }

            val orientation = open(uri)?.use { readExifOrientation(it) } ?: ExifInterface.ORIENTATION_NORMAL

            // قراءة الأبعاد فقط
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            val w0 = bounds.outWidth
            val h0 = bounds.outHeight
            if (w0 <= 0 || h0 <= 0) return@runCatching null

            val safeMaxDim = maxDim.coerceIn(256, DEFAULT_MAX_DIM)

            // inSampleSize أقرب قوة للاثنين
            var inSample = 1
            while (w0 / inSample > safeMaxDim || h0 / inSample > safeMaxDim) inSample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = inSample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }

            val decoded = try {
                open(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            } catch (_: OutOfMemoryError) {
                val retryOpts = BitmapFactory.Options().apply {
                    inSampleSize = (inSample * 2).coerceAtLeast(2)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
                open(uri)?.use { BitmapFactory.decodeStream(it, null, retryOpts) }
            } ?: return@runCatching null

            val oriented = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> decoded.rotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> decoded.rotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> decoded.rotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> decoded.flip(horizontal = true, vertical = false)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> decoded.flip(horizontal = false, vertical = true)
                else -> decoded
            }
            if (oriented !== decoded) {
                decoded.recycle()
            }

            if (oriented.width <= safeMaxDim && oriented.height <= safeMaxDim) {
                oriented
            } else {
                val maxSide = max(oriented.width, oriented.height).toFloat()
                val scale = safeMaxDim / maxSide
                val targetW = (oriented.width * scale).roundToInt().coerceAtLeast(1)
                val targetH = (oriented.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(oriented, targetW, targetH, true).also {
                    if (it !== oriented) oriented.recycle()
                }
            }
        }.getOrNull()
    }

    // ================ تراكب نصّي صغير جدًا (سطرين) ================ //
    /** يرسم تراكباً نصّياً (اسم المشروع + التاريخ) بخط صغير جدًا في ركن الصورة. */
    fun drawTwoLineOverlay(
        src: Bitmap,
        line1: String?,
        line2: String?,
        gravity: OverlayGravity = OverlayGravity.BOTTOM_RIGHT,
        titleScale: Float = 0.014f,
        subtitleScale: Float = 0.010f,
        maxBoxWidthRatio: Float = 0.88f,
        marginRatio: Float = 0.010f,
        cornerRatio: Float = 0.014f,
        bgAlpha: Int = 130,
        typeface: android.graphics.Typeface? = null,
        scaleMul: Float = 1.0f,
        minTitlePx: Float = 8f,
        minSubPx: Float = 7f
    ): Bitmap {
        val title = line1?.takeIf { it.isNotBlank() } ?: return src
        val subtitle = line2?.takeIf { it.isNotBlank() }

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)

        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val pad = (w * marginRatio * scaleMul).coerceAtLeast(6f)
        val radius = (w * cornerRatio * scaleMul).coerceAtLeast(6f)

        val titlePaintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (w * titleScale * scaleMul).coerceAtLeast(minTitlePx)
            textAlign = Paint.Align.RIGHT
            typeface?.let { this.typeface = it }
        }
        val titlePaintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = (textSize / 9f).coerceAtLeast(1.0f)
            textSize = titlePaintFill.textSize
            textAlign = Paint.Align.RIGHT
            typeface?.let { this.typeface = it }
        }
        val subPaintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (w * subtitleScale * scaleMul).coerceAtLeast(minSubPx)
            textAlign = Paint.Align.RIGHT
            typeface?.let { this.typeface = it }
        }
        val subPaintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = (textSize / 9f).coerceAtLeast(0.8f)
            textSize = subPaintFill.textSize
            textAlign = Paint.Align.RIGHT
            typeface?.let { this.typeface = it }
        }

        val maxTextWidth = w * maxBoxWidthRatio - 2 * pad
        fitPaintToWidth(titlePaintFill, title, maxTextWidth, minSize = minTitlePx)
        titlePaintStroke.textSize = titlePaintFill.textSize
        val subText = subtitle
        if (subText != null) {
            fitPaintToWidth(subPaintFill, subText, maxTextWidth, minSize = minSubPx)
            subPaintStroke.textSize = subPaintFill.textSize
        }

        fun Paint.lineHeight(): Float { val fm = fontMetrics; return fm.descent - fm.ascent }

        val titleW = titlePaintFill.measureText(title)
        val titleH = titlePaintFill.lineHeight()
        val subW = if (subText != null) subPaintFill.measureText(subText) else 0f
        val subH = if (subText != null) subPaintFill.lineHeight() else 0f
        val lineSpacing = max(2.5f, w * 0.006f * scaleMul)

        val boxW = max(titleW, subW) + 2 * pad
        val boxH = titleH + (if (subText != null) lineSpacing + subH else 0f) + 2 * pad

        val (left, top) = when (gravity) {
            OverlayGravity.TOP_LEFT -> pad to pad
            OverlayGravity.TOP_CENTER -> (w / 2f - boxW / 2f) to pad
            OverlayGravity.TOP_RIGHT -> (w - pad - boxW) to pad
            OverlayGravity.CENTER -> (w / 2f - boxW / 2f) to (h / 2f - boxH / 2f)
            OverlayGravity.BOTTOM_LEFT -> pad to (h - pad - boxH)
            OverlayGravity.BOTTOM_CENTER -> (w / 2f - boxW / 2f) to (h - pad - boxH)
            OverlayGravity.BOTTOM_RIGHT -> (w - pad - boxW) to (h - pad - boxH)
        }

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(bgAlpha, 0, 0, 0) }
        c.drawRoundRect(RectF(left, top, left + boxW, top + boxH), radius, radius, bg)

        val baseY = top + pad - titlePaintFill.fontMetrics.ascent
        val rightX = left + boxW - pad

        c.drawText(title, rightX, baseY, titlePaintStroke)
        c.drawText(title, rightX, baseY, titlePaintFill)
        if (subText != null) {
            val y2 = baseY + titlePaintFill.lineHeight() + lineSpacing
            c.drawText(subText, rightX, y2, subPaintStroke)
            c.drawText(subText, rightX, y2, subPaintFill)
        }
        return out
    }

    /** خط التطبيق الرسمي (إن وُجد). */
    fun appTypeface(context: Context): android.graphics.Typeface? {
        return try {
            ResourcesCompat.getFont(context, R.font.main_font) ?: ResourcesCompat.getFont(context, R.font.rb)
        } catch (_: Exception) { null }
    }

    // ================ تجهيز أفقي موحّد (بدون تمويه) ================ //
    /** يُحضّر الصورة داخل إطار أفقي ثابت (افتراضياً 1280x720) بدون تشويه للمحتوى. */
    fun prepareHorizontalWithOverlay(
        src: Bitmap,
        targetW: Int = 1280,
        targetH: Int = 720,
        projectName: String?,
        reportDate: String?,
        typeface: android.graphics.Typeface? = null,
        gravity: OverlayGravity = OverlayGravity.BOTTOM_RIGHT,
        maxPortraitCropFraction: Float = 0.15f,
        overlayScaleMul: Float = 1.0f
    ): Bitmap {
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // 1) خلفية موحّدة اللون (أبيض)
        canvas.drawColor(Color.WHITE)

        // 2) الصورة الرئيسية (Letterbox + قص بسيط للعمودية)
        val srcW = src.width
        val srcH = src.height
        val targetRatio = targetW.toFloat() / targetH

        val cropRect = if (srcW >= srcH) {
            Rect(0, 0, srcW, srcH)
        } else {
            val heightNeeded = (srcW / targetRatio)
            val possibleExcess = (srcH - heightNeeded).coerceAtLeast(0f)
            val maxCrop = (srcH * maxPortraitCropFraction)
            val cropTotal = min(possibleExcess, maxCrop)
            val newH = (srcH - cropTotal).toInt()
            val top = ((srcH - newH) / 2f).toInt()
            Rect(0, top, srcW, top + newH)
        }

        val cropW = cropRect.width()
        val cropH = cropRect.height()
        val scale = min(targetW.toFloat() / cropW, targetH.toFloat() / cropH)
        val destW = (cropW * scale).toInt()
        val destH = (cropH * scale).toInt()
        val destLeft = (targetW - destW) / 2
        val destTop = (targetH - destH) / 2
        val destRect = Rect(destLeft, destTop, destLeft + destW, destTop + destH)

        canvas.drawBitmap(src, cropRect, destRect, paint)

        // 3) التراكب النصّي الصغير (اختياري)
        val withOverlay = drawTwoLineOverlay(
            out,
            line1 = projectName,
            line2 = reportDate,
            gravity = gravity,
            typeface = typeface,
            scaleMul = overlayScaleMul
        )
        return withOverlay
    }

    /** نسخة مريحة تجلب Typeface من موارد التطبيق. */
    fun prepareHorizontalWithOverlay(
        context: Context,
        src: Bitmap,
        targetW: Int = 1280,
        targetH: Int = 720,
        projectName: String?,
        reportDate: String?,
        gravity: OverlayGravity = OverlayGravity.BOTTOM_RIGHT,
        maxPortraitCropFraction: Float = 0.15f,
        overlayScaleMul: Float = 1.0f
    ): Bitmap {
        val tf = appTypeface(context)
        return prepareHorizontalWithOverlay(
            src, targetW, targetH, projectName, reportDate, tf, gravity, maxPortraitCropFraction, overlayScaleMul
        )
    }

    // ================ وضع إضافي: Fit-Inside بدون قص ================ //
    fun prepareFitInside(
        context: Context,
        src: Bitmap,
        targetW: Int,
        targetH: Int,
        projectName: String?,
        reportDate: String?,
        backgroundColor: Int = Color.WHITE,
        gravity: OverlayGravity = OverlayGravity.BOTTOM_RIGHT,
        overlayScaleMul: Float = 0.8f
    ): Bitmap {
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(backgroundColor)

        val s = min(targetW.toFloat() / src.width.toFloat(), targetH.toFloat() / src.height.toFloat())
        val dw = (src.width * s).toInt().coerceAtLeast(1)
        val dh = (src.height * s).toInt().coerceAtLeast(1)
        val lx = (targetW - dw) / 2f
        val ty = (targetH - dh) / 2f
        val scaled = Bitmap.createScaledBitmap(src, dw, dh, true)
        c.drawBitmap(scaled, lx, ty, null)

        return drawTwoLineOverlay(
            out,
            line1 = projectName,
            line2 = reportDate,
            gravity = gravity,
            typeface = appTypeface(context),
            scaleMul = overlayScaleMul
        )
    }

    // ================ تركيب صفحة A4 عمودية (Fit-Inside) ================ //
    /**
     * يُركّب صفحة كاملة بنسبة A4 عمودية (بدون قص للصور داخل الخانات).
     * @param widthPx العرض بالبكسل (الارتفاع يُحسب بنسبة A4 تلقائياً). افتراضياً 1480px.
     */
    fun composeA4PortraitPage(
        context: Context,
        entries: List<PhotoEntry?>,
        template: PhotoTemplate,
        widthPx: Int = 1480,
        backgroundColor: Int = Color.WHITE,
        outerMarginPx: Int = max(24, (widthPx * 0.04f).toInt()),
        gutterPx: Int = max(14, (widthPx * 0.022f).toInt()),
        captionBandRatio: Float = 0.17f,
        cornerRadiusPx: Float = max(10f, widthPx * 0.012f),
        showHeader: Boolean = false,
        headerProject: String? = null,
        headerDate: String? = null,
        pageW: Int,
        pageH: Int,
        projectName: String?,
        reportDate: String,
        headerEnabled: Boolean
    ): Bitmap {
        val heightPx = (widthPx * 1.41421356f).toInt() // نسبة A / √2
        val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(backgroundColor)

        val tf = appTypeface(context)

        // ترويسة اختيارية صغيرة
        var topOffset = outerMarginPx
        if (showHeader && (!headerProject.isNullOrBlank() || !headerDate.isNullOrBlank())) {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = max(18f, widthPx * 0.032f)
                textAlign = Paint.Align.LEFT
                tf?.let { typeface = it }
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = max(14f, widthPx * 0.024f)
                textAlign = Paint.Align.LEFT
                tf?.let { typeface = it }
            }
            val x = outerMarginPx.toFloat()
            var y = outerMarginPx.toFloat() - titlePaint.fontMetrics.ascent
            headerProject?.takeIf { it.isNotBlank() }?.let {
                c.drawText(it, x, y, titlePaint)
                y += (titlePaint.fontMetrics.descent - titlePaint.fontMetrics.ascent) * 0.9f
            }
            headerDate?.takeIf { it.isNotBlank() }?.let {
                c.drawText(it, x, y, subPaint)
            }
            topOffset = (y + outerMarginPx * 0.25f).toInt()
        }

        // حساب الشبكة
        val columns = max(1, template.columns)
        val total = max(1, template.slots.size)
        val rows = ceil(total / columns.toFloat()).toInt()

        val contentLeft = outerMarginPx
        val contentTop = topOffset
        val contentRight = widthPx - outerMarginPx
        val contentBottom = heightPx - outerMarginPx
        val contentW = contentRight - contentLeft
        val contentH = contentBottom - contentTop

        val usableW = contentW - gutterPx * (columns - 1)
        val usableH = contentH - gutterPx * (rows - 1)
        val cellW = usableW / columns
        val cellH = usableH / rows

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = max(1f, widthPx * 0.0015f)
        }
        val placeholderStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            style = Paint.Style.STROKE
            strokeWidth = max(2f, widthPx * 0.0022f)
            pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
        }
        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (i in 0 until total) {
            val r = i / columns
            val col = i % columns

            val l = contentLeft + col * (cellW + gutterPx)
            val t = contentTop + r * (cellH + gutterPx)
            val rect = RectF(l.toFloat(), t.toFloat(), (l + cellW).toFloat(), (t + cellH).toFloat())

            // مستطيل الصورة + شريط التعليق
            val captionH = (rect.height() * captionBandRatio).coerceAtMost(80f)
            val imgRect = RectF(rect.left + 6f, rect.top + 6f, rect.right - 6f, rect.bottom - captionH - 6f)
            val capRect = RectF(rect.left + 8f, rect.bottom - captionH, rect.right - 8f, rect.bottom - 6f)

            // إطار لطيف للخانة
            c.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, border)

            val entry = entries.getOrNull(i)
            val srcUriStr = entry?.localUri ?: entry?.originalUrl
            val caption = entry?.caption?.takeIf { it.isNotBlank() }

            if (!srcUriStr.isNullOrBlank()) {
                val srcUri = runCatching { Uri.parse(srcUriStr) }.getOrNull()
                val decodeMaxDim = estimateDecodeMaxDim(imgRect)
                val bmp = srcUri?.let { decodeBitmap(context.contentResolver, it, maxDim = decodeMaxDim) }
                if (bmp != null) {
                    // Fit-Inside داخل imgRect (بدون قص)
                    val dstRect = fitInsideRect(bmp.width, bmp.height, imgRect)
                    c.save()
                    c.clipRect(imgRect)
                    c.drawBitmap(bmp, null, dstRect, imgPaint)
                    c.restore()
                    bmp.recycle()
                } else {
                    drawPlaceholder(c, imgRect, placeholderStroke)
                }
            } else {
                drawPlaceholder(c, imgRect, placeholderStroke)
            }

            drawCaptionBand(c, capRect, caption, tf, widthPx)
        }

        return out
    }

    // ================ تركيبات أفقية سابقة (للتوافق) ================ //
    /**
     * يُركّب صفحة كاملة حسب القالب (أفقي افتراضي 1920×1080) باستخدام CenterCrop للصور.
     * ملاحظة: بقيت للتوافق؛ الوضع الموصى به لملفات التقرير هو composeA4PortraitPage.
     */
    fun composeGridPage(
        context: Context,
        entries: List<PhotoEntry?>,
        template: PhotoTemplate,
        targetW: Int = 1920,
        targetH: Int = 1080,
        projectName: String? = null,
        reportDate: String? = null,
        gutterPx: Int = max(8, (targetW * 0.012f).toInt()),
        outerMarginPx: Int = max(12, (targetW * 0.02f).toInt()),
        captionBandRatio: Float = 0.18f,
        cellCornerRadiusPx: Float = max(8f, targetW * 0.006f),
        drawPageHeader: Boolean = true
    ): Bitmap {
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)

        val tf = appTypeface(context)

        val columns = max(1, template.columns)
        val totalSlots = max(1, template.slots.size)
        val rows = ceil(totalSlots / columns.toFloat()).toInt()

        val usableW = targetW - (outerMarginPx * 2) - (gutterPx * (columns - 1))
        val usableH = targetH - (outerMarginPx * 2) - (gutterPx * (rows - 1))
        val cellW = usableW / columns
        val cellH = usableH / rows

        val cellBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val cellBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = max(1f, targetW * 0.0015f)
        }
        val placeholderStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            style = Paint.Style.STROKE
            strokeWidth = max(2f, targetW * 0.002f)
            pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
        }
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        if (drawPageHeader && (!projectName.isNullOrBlank() || !reportDate.isNullOrBlank())) {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = max(18f, targetW * 0.022f)
                textAlign = Paint.Align.LEFT
                tf?.let { typeface = it }
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = max(14f, targetW * 0.016f)
                textAlign = Paint.Align.LEFT
                tf?.let { typeface = it }
            }
            val startX = outerMarginPx.toFloat()
            var y = outerMarginPx.toFloat() - titlePaint.fontMetrics.ascent
            projectName?.takeIf { it.isNotBlank() }?.let {
                c.drawText(it, startX, y, titlePaint)
                y += (titlePaint.fontMetrics.descent - titlePaint.fontMetrics.ascent) * 0.9f
            }
            reportDate?.takeIf { it.isNotBlank() }?.let {
                c.drawText(it, startX, y, subPaint)
            }
        }

        for (i in 0 until totalSlots) {
            val r = i / columns
            val col = i % columns

            val left = outerMarginPx + col * (cellW + gutterPx)
            val top = outerMarginPx + r * (cellH + gutterPx)
            val rect = RectF(left.toFloat(), top.toFloat(), (left + cellW).toFloat(), (top + cellH).toFloat())

            c.drawRoundRect(rect, cellCornerRadiusPx, cellCornerRadiusPx, cellBg)
            c.drawRoundRect(rect, cellCornerRadiusPx, cellCornerRadiusPx, cellBorder)

            val captionH = (rect.height() * captionBandRatio).coerceAtMost(72f)
            val imageRect = RectF(rect.left + 6f, rect.top + 6f, rect.right - 6f, rect.bottom - captionH - 6f)
            val captionRect = RectF(rect.left + 8f, rect.bottom - captionH, rect.right - 8f, rect.bottom - 6f)

            val entry = entries.getOrNull(i)
            val srcUriStr = entry?.localUri ?: entry?.originalUrl
            val caption = entry?.caption?.takeIf { it.isNotBlank() }

            if (!srcUriStr.isNullOrBlank()) {
                val srcUri = runCatching { Uri.parse(srcUriStr) }.getOrNull()
                val decodeMaxDim = estimateDecodeMaxDim(imageRect, maxDim = DEFAULT_MAX_DIM)
                val bmp = srcUri?.let { decodeBitmap(context.contentResolver, it, maxDim = decodeMaxDim) }
                if (bmp != null) {
                    // CenterCrop داخل imageRect
                    val srcRect = Rect(0, 0, bmp.width, bmp.height)
                    val dstRect = centerCropRect(bmp.width, bmp.height, imageRect)
                    c.save()
                    c.clipRect(imageRect)
                    c.drawBitmap(bmp, srcRect, dstRect, imagePaint)
                    c.restore()
                    bmp.recycle()
                } else {
                    drawPlaceholder(c, imageRect, placeholderStroke)
                }
            } else {
                drawPlaceholder(c, imageRect, placeholderStroke)
            }

            drawCaptionBand(c, captionRect, caption, tf, targetW)
        }

        return out
    }

    // ================ الحفظ والمشاركة ================ //
    /** حفظ الصورة إلى الاستديو (MediaStore) داخل Pictures/Meydan بجودة مرتفعة. */
    fun saveToGallery(context: Context, bitmap: Bitmap, filename: String? = null, quality: Int = DEFAULT_JPEG_QUALITY): Uri? {
        return try {
            val name = filename ?: "Meydan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Meydan")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { os ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(80, 100), os)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

    /** ضغط إلى JPEG داخل cache وإرجاع Uri عبر FileProvider (authority مصحَّح). */
    fun compressToCacheJpeg(context: Context, bitmap: Bitmap, quality: Int = DEFAULT_JPEG_QUALITY): Uri {
        val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
        val outFile = File(imagesDir, "IMG_${'$'}{System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(80, 100), fos)
        }
        return FileProvider.getUriForFile(context, "${'$'}{context.packageName}.fileprovider", outFile)
    }

    /** ضغط إلى WebP داخل cache وإرجاع Uri عبر FileProvider. */
    fun compressToCacheWebp(context: Context, bitmap: Bitmap, quality: Int = DEFAULT_WEBP_QUALITY): Uri {
        val imagesDir = File(context.cacheDir, "pages").apply { mkdirs() }
        val outFile = File(imagesDir, "PAGE_${'$'}{System.currentTimeMillis()}.webp")
        FileOutputStream(outFile).use { fos ->
            val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            bitmap.compress(fmt, quality.coerceIn(75, 100), fos)
        }
        return FileProvider.getUriForFile(context, "${'$'}{context.packageName}.fileprovider", outFile)
    }

    // ================ Helpers ================ //

    private fun readExifOrientation(inputStream: InputStream): Int {
        return try {
            val exif = ExifInterface(inputStream)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun fitPaintToWidth(paint: Paint, text: String, maxWidth: Float, minSize: Float) {
        var size = paint.textSize
        while (size > minSize && paint.measureText(text) > maxWidth) {
            size *= 0.96f
            paint.textSize = size
        }
    }

    private fun Bitmap.rotate(deg: Float): Bitmap {
        val m = Matrix().apply { postRotate(deg) }
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    }

    private fun Bitmap.flip(horizontal: Boolean, vertical: Boolean): Bitmap {
        val sx = if (horizontal) -1f else 1f
        val sy = if (vertical) -1f else 1f
        val m = Matrix().apply { postScale(sx, sy, width / 2f, height / 2f) }
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    }

    private fun centerCropRect(srcW: Int, srcH: Int, dst: RectF): Rect {
        val dstW = dst.width()
        val dstH = dst.height()
        val scale = max(dstW / srcW, dstH / srcH)
        val outW = (srcW * scale).toInt()
        val outH = (srcH * scale).toInt()
        val left = ((dstW - outW) / 2f + dst.left).toInt()
        val top = ((dstH - outH) / 2f + dst.top).toInt()
        return Rect(left, top, left + outW, top + outH)
    }

    private fun fitInsideRect(srcW: Int, srcH: Int, dst: RectF): RectF {
        val s = min(dst.width() / srcW, dst.height() / srcH)
        val dw = srcW * s
        val dh = srcH * s
        val left = dst.centerX() - dw / 2f
        val top = dst.centerY() - dh / 2f
        return RectF(left, top, left + dw, top + dh)
    }

    private fun estimateDecodeMaxDim(rect: RectF, maxDim: Int = DEFAULT_MAX_DIM): Int {
        if (rect.width() <= 0f || rect.height() <= 0f) return maxDim
        val longest = max(rect.width(), rect.height())
        val padded = (longest * 1.2f).roundToInt()
        return padded.coerceIn(256, maxDim)
    }

    private fun drawPlaceholder(c: Canvas, rect: RectF, stroke: Paint) {
        val r = RectF(rect)
        c.drawRoundRect(r, 12f, 12f, stroke)
        // أيقونة كاميرا بسيطة
        val w = r.width()
        val h = r.height()
        val cx = r.centerX()
        val cy = r.centerY()
        val p = Paint(stroke).apply { pathEffect = null }
        val path = Path()
        val bodyW = w * 0.28f
        val bodyH = h * 0.22f
        path.addRoundRect(RectF(cx - bodyW/2, cy - bodyH/2, cx + bodyW/2, cy + bodyH/2), 8f, 8f, Path.Direction.CW)
        c.drawPath(path, p)
        c.drawCircle(cx, cy, min(w, h) * 0.07f, p)
    }

    private fun drawCaptionBand(c: Canvas, rect: RectF, text: String?, tf: android.graphics.Typeface?, pageW: Int) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 250, 250, 250) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = max(1f, pageW * 0.0012f)
        }
        c.drawRoundRect(rect, 8f, 8f, bg)
        c.drawRoundRect(rect, 8f, 8f, stroke)

        if (text.isNullOrBlank()) return
        val pad = max(8f, pageW * 0.008f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = max(16f, pageW * 0.016f)
            textAlign = Paint.Align.LEFT
            tf?.let { typeface = it }
        }
        // قص النص على سطرين كحد أقصى
        val availableW = rect.width() - 2 * pad
        var remaining = text.trim()
        var y = rect.top + pad - paint.fontMetrics.ascent
        repeat(2) {
            if (remaining.isEmpty()) return
            val count = paint.breakText(remaining, true, availableW, null)
            val line = remaining.substring(0, count)
            c.drawText(line, rect.left + pad, y, paint)
            remaining = remaining.substring(count).trimStart()
            y += (paint.fontMetrics.descent - paint.fontMetrics.ascent) * 1.05f
        }
    }
}
