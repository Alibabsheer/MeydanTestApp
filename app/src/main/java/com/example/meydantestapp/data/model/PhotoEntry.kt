package com.example.meydantestapp.models

import android.graphics.RectF
import androidx.annotation.Keep

/**
 * PhotoEntry
 * تمثيل خانة صورة داخل صفحة شبكة معيّنة من تقرير يومي.
 *
 * ملاحظات حسب الخطة الاستثنائية:
 * - القوالب محفوظة كصور عمودية بنسبة A4. أي قص (crop) — إن وُجد — يتم فقط قبل الحفظ.
 * - بعد الحفظ/الرفع **لا نقوم بأي قص** عند العرض/التصدير، بل Fit-Inside في مساحة الصور.
 * - الحقل [cropRect] اختياري للمستقبل (قص قبل الحفظ فقط)، ولا يُحفَظ ضمن sitepagesmeta.
 */
@Keep
data class PhotoEntry(
    val templateId: TemplateId,
    val pageIndex: Int,
    val slotIndex: Int,
    val originalUrl: String? = null, // رابط الصورة بعد الرفع (إن وُجد)
    val localUri: String? = null,    // مسار محلي قبل الرفع (إن وُجد)
    val caption: String? = null,     // تعليق أسفل الخانة
    val cropRect: RectF? = null      // اختياري: مستطيل قص مُطَبَّع 0..1 (للاستخدام قبل الحفظ فقط)
) {
    fun hasRemote(): Boolean = !originalUrl.isNullOrBlank()
    fun hasLocal(): Boolean = !localUri.isNullOrBlank()

    /** خريطة للحفظ في Firestore ضمن sitepagesmeta.slots[] */
    fun toMap(): Map<String, Any?> = mapOf(
        Keys.TEMPLATE_ID to templateId.name,
        Keys.PAGE_INDEX to pageIndex,
        Keys.SLOT_INDEX to slotIndex,
        Keys.ORIGINAL_URL to originalUrl,
        Keys.CAPTION to (caption ?: "")
        // ملاحظة: لا نحفظ cropRect ضمن الميتا وفق السياسة الحالية (لا قص بعد الحفظ)
    )

    fun withCaption(text: String?): PhotoEntry = copy(
        caption = text?.trim()?.take(MAX_CAPTION_CHARS)?.takeIf { it.isNotBlank() }
    )

    companion object {
        const val MAX_CAPTION_CHARS = 120

        object Keys {
            const val TEMPLATE_ID = "templateId"
            const val PAGE_INDEX = "pageIndex"
            const val SLOT_INDEX = "slotIndex"
            const val ORIGINAL_URL = "originalUrl"
            const val CAPTION = "caption"
        }

        /** إنشاء PhotoEntry من خريطة Firestore (توافق خلفي آمن). */
        fun fromMap(map: Map<String, Any?>): PhotoEntry {
            val template = runCatching {
                TemplateId.valueOf((map[Keys.TEMPLATE_ID] as? String).orEmpty())
            }.getOrElse { TemplateId.E4 }

            val page = (map[Keys.PAGE_INDEX] as? Number)?.toInt() ?: 0
            val slot = (map[Keys.SLOT_INDEX] as? Number)?.toInt() ?: 0
            val url = map[Keys.ORIGINAL_URL] as? String
            val cap = (map[Keys.CAPTION] as? String)?.trim()
                ?.take(MAX_CAPTION_CHARS)
                ?.takeIf { it.isNotBlank() }

            return PhotoEntry(
                templateId = template,
                pageIndex = page,
                slotIndex = slot,
                originalUrl = url,
                localUri = null,
                caption = cap,
                cropRect = null // لا نقرأ cropRect من الماب وفق السياسة الحالية
            )
        }
    }
}
