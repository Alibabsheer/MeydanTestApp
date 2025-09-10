package com.example.meydantestapp.report

import android.graphics.RectF
import androidx.annotation.FloatRange
import com.example.meydantestapp.models.PhotoTemplate
import com.example.meydantestapp.models.SlotSpec

/**
 * PdfGridLayout
 *
 * غرض الملف:
 * 1) تحديد **مستطيل منطقة الصور** داخل صفحة A4 (المربع الأحمر الذي نضع فيه صور التقرير).
 * 2) حساب **مواضع الخانات** (الصورة + مساحة التعليق) لقالب معيّن داخل مستطيل معيّن.
 *
 * ملاحظات مهمة:
 * - جميع الحسابات بوحدة البكسل.
 * - القوالب يجب أن تكون عمودية (A4) — لكن الدوال لا تفترض هذا قسرًا، فقط تتوقع أبعاد الصفحة.
 * - لا يتم الاقتصاص هنا؛ إنما نحسب RectF للعرض لاحقًا بـ fit-inside.
 */
object PdfGridLayout {

    /* ============================ أنواع مساعدة ============================ */

    /** هوامش عامة (يسار/أعلى/يمين/أسفل) لمستطيل المحتوى */
    data class Insets(
        val left: Float = 24f,
        val top: Float = 24f,
        val right: Float = 24f,
        val bottom: Float = 24f
    )

    /** ناتج تخطيط خانة واحدة: مستطيل الصورة + مستطيل التعليق */
    data class SlotLayout(
        val slotIndex: Int,
        val imageRect: RectF,
        val captionRect: RectF
    )

    /* ============================ منطقة الصور في صفحة A4 ============================ */

    /**
     * يُعيد مستطيل منطقة الصور (المربع الأحمر) داخل صفحة PDF.
     *
     * @param pageWidthPx  عرض الصفحة بالبكسل (مثلاً 595 @72dpi أو 2480 @300dpi)
     * @param pageHeightPx ارتفاع الصفحة بالبكسل (مثلاً 842 @72dpi أو 3508 @300dpi)
     * @param usedTopAreaPx مقدار الارتفاع المُستهلك أعلى الصفحة قبل قسم الصور (شعار/عنوان/معلومات/أقسام نصية).
     *                       مرن حسب ما رسمته فعليًا في الـ PDF — إن لم تكن متأكدًا استخدم القيمة الافتراضية.
     * @param marginPx      هامش جانبي/علوي/سفلي عام.
     * @param footerBlockPx ارتفاع كتلة التذييل السفلية (رقم الصفحة/سطر صغير).
     */
    @JvmStatic
    fun getPhotoAreaRect(
        pageWidthPx: Int,
        pageHeightPx: Int,
        usedTopAreaPx: Int = 260,
        marginPx: Int = 24,
        footerBlockPx: Int = 28
    ): RectF {
        val left = marginPx.toFloat()
        val top = (marginPx + usedTopAreaPx).toFloat()
        val right = (pageWidthPx - marginPx).toFloat()
        val bottom = (pageHeightPx - marginPx - footerBlockPx).toFloat()
        return RectF(left, top, right, bottom)
    }

    /**
     * بديل عام: يُعيد مستطيل المحتوى داخل الصفحة انطلاقًا من هوامش بسيطة.
     */
    @JvmStatic
    fun getContentRectFromInsets(
        pageWidthPx: Int,
        pageHeightPx: Int,
        insets: Insets = Insets()
    ): RectF = RectF(
        insets.left,
        insets.top,
        pageWidthPx - insets.right,
        pageHeightPx - insets.bottom
    )

    /* ============================ حساب تخطيط القالب ============================ */

    /**
     * يحسب تخطيط القالب داخل مستطيل محدد (areaRect): لكل خانة مستطيل الصورة ومستطيل التعليق.
     *
     * @param template         القالب (أعمدة/صفوف/قائمة SlotSpec)
     * @param areaRect         منطقة المحتوى التي نملؤها بالخانات (عادةً ما تُستخرج بـ getPhotoAreaRect)
     * @param gutterPx         الفراغ الأفقي/العمودي بين الخانات
     * @param captionHeightPx  ارتفاع شريط التعليق أسفل كل صورة
     * @param captionSpacingPx مسافة صغيرة بين الصورة وبداية شريط التعليق
     * @param imageAspect      نسبة الصورة داخل الخانة (افتراضي 16:9)
     * @param autoScaleToFit   إن تجاوزت الصفوف ارتفاع المنطقة، نُصغِّر كل التخطيط تناسبيًا ليُلائم الارتفاع
     */
    @JvmStatic
    fun computeInArea(
        template: PhotoTemplate,
        areaRect: RectF,
        gutterPx: Float = 24f,
        captionHeightPx: Float = 48f,
        captionSpacingPx: Float = 8f,
        @FloatRange(from = 0.1, to = 10.0) imageAspect: Float = 16f / 9f,
        autoScaleToFit: Boolean = true
    ): List<SlotLayout> {
        require(template.columns > 0 && template.rows > 0) { "Invalid template grid" }

        val contentLeft = areaRect.left
        val contentTop = areaRect.top
        val contentRight = areaRect.right
        val contentBottom = areaRect.bottom
        val contentWidth = (contentRight - contentLeft).coerceAtLeast(1f)
        val contentHeight = (contentBottom - contentTop).coerceAtLeast(1f)

        // عرض العمود الأساسي
        val totalGuttersX = gutterPx * (template.columns - 1).coerceAtLeast(0)
        val baseColWidth = (contentWidth - totalGuttersX) / template.columns
        val colX = FloatArray(template.columns) { i -> contentLeft + i * (baseColWidth + gutterPx) }

        // صفوف ← عناصر (slotIndex, spec)
        val rowsMap: Map<Int, List<Pair<Int, SlotSpec>>> =
            template.slots.withIndex()
                .groupBy { it.value.row }
                .mapValues { (_, list) -> list.map { iv -> iv.index to iv.value } }

        var cursorY = contentTop
        val out = ArrayList<SlotLayout>(template.slots.size)

        for (rowIndex in 0 until template.rows) {
            val rowItems = rowsMap[rowIndex] ?: emptyList()
            if (rowItems.isEmpty()) continue

            // احسب أبعاد كل خانة في هذا الصف، وحدد أكبر ارتفاع صف
            var rowMaxHeight = 0f
            val prepared = rowItems.map { (slotIndex, spec) ->
                val col = spec.col.coerceIn(0, template.columns - 1)
                val span = spec.colSpan.coerceAtLeast(1)
                val lastCol = (col + span - 1).coerceAtMost(template.columns - 1)
                val effSpan = lastCol - col + 1

                val x = colX[col]
                val w = baseColWidth * effSpan + gutterPx * (effSpan - 1)
                val imgH = (w / imageAspect)
                val totalH = imgH + captionSpacingPx + captionHeightPx
                if (totalH > rowMaxHeight) rowMaxHeight = totalH
                Triple(slotIndex, spec, SlotTmp(x, w, imgH))
            }

            // أنشئ المستطيلات النهائية لهذا الصف عند cursorY
            prepared.forEach { (slotIndex, _, tmp) ->
                val imageRect = RectF(
                    tmp.x,
                    cursorY,
                    tmp.x + tmp.w,
                    cursorY + tmp.imgH
                )
                val captionRect = RectF(
                    tmp.x,
                    imageRect.bottom + captionSpacingPx,
                    tmp.x + tmp.w,
                    imageRect.bottom + captionSpacingPx + captionHeightPx
                )
                out.add(SlotLayout(slotIndex = slotIndex, imageRect = imageRect, captionRect = captionRect))
            }

            // انتقل للصف التالي
            cursorY += rowMaxHeight + gutterPx
        }

        // إن تجاوز الارتفاع، نُصغّر كل شيء ليلائم areaRect عموديًا
        val usedHeight = cursorY - contentTop - gutterPx // آخر زيادة كانت بعد آخر صف
        if (autoScaleToFit && usedHeight > contentHeight + 1f && out.isNotEmpty()) {
            val scale = (contentHeight / usedHeight).coerceAtMost(1f)
            if (scale < 1f) {
                out.replaceAll { s ->
                    val img = RectF(
                        contentLeft + (s.imageRect.left - contentLeft) * scale,
                        contentTop + (s.imageRect.top - contentTop) * scale,
                        contentLeft + (s.imageRect.right - contentLeft) * scale,
                        contentTop + (s.imageRect.bottom - contentTop) * scale
                    )
                    val cap = RectF(
                        contentLeft + (s.captionRect.left - contentLeft) * scale,
                        contentTop + (s.captionRect.top - contentTop) * scale,
                        contentLeft + (s.captionRect.right - contentLeft) * scale,
                        contentTop + (s.captionRect.bottom - contentTop) * scale
                    )
                    s.copy(imageRect = img, captionRect = cap)
                }
            }
        }

        // أعِد ترتيب النتائج حسب slotIndex (0..)
        return out.sortedBy { it.slotIndex }
    }

    /**
     * نسخة مريحة: تحسب التخطيط بالاعتماد على أبعاد الصفحة وهوامش عامة بدلًا من areaRect مباشرة.
     */
    @JvmStatic
    fun compute(
        template: PhotoTemplate,
        pageWidthPx: Int,
        pageHeightPx: Int,
        insets: Insets = Insets(),
        gutterPx: Float = 24f,
        captionHeightPx: Float = 48f,
        captionSpacingPx: Float = 8f,
        @FloatRange(from = 0.1, to = 10.0) imageAspect: Float = 16f / 9f,
        autoScaleToFit: Boolean = true
    ): List<SlotLayout> {
        val area = getContentRectFromInsets(pageWidthPx, pageHeightPx, insets)
        return computeInArea(
            template = template,
            areaRect = area,
            gutterPx = gutterPx,
            captionHeightPx = captionHeightPx,
            captionSpacingPx = captionSpacingPx,
            imageAspect = imageAspect,
            autoScaleToFit = autoScaleToFit
        )
    }

    /* ============================ داخلي ============================ */
    private data class SlotTmp(val x: Float, val w: Float, val imgH: Float)
}
