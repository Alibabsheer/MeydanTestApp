package com.example.meydantestapp.report

/**
 * أدوات مشتركة لتوليد عنوان موحّد للتقارير اليومية.
 * يستخلص الحقول المهمة من الميتا ويركّب عنوانًا مناسبًا يمكن إعادة استخدامه
 * في واجهة العرض واسم ملف الـ PDF.
 */
object ReportTitleUtil {
    /** يبني العنوان من projectName + reportNumber + dateText إن توفرت. */
    fun getReportTitle(meta: Map<String, Any?>): String {
        val project = (meta["projectName"] as? String)?.takeIf { it.isNotBlank() }
        val reportNum = (meta["reportNumber"] as? String)?.takeIf { it.isNotBlank() }
        val date = (meta["dateText"] as? String)?.takeIf { it.isNotBlank() }
        val parts = mutableListOf<String>()
        if (project != null) parts += project
        if (reportNum != null) parts += reportNum
        if (date != null) parts += date
        return if (parts.isNotEmpty()) parts.joinToString(" - ") else "تقرير"
    }

    /** يحوّل العنوان إلى اسم ملف آمن عبر استبدال المسافات بعلامة underscore. */
    fun fileNameFromMeta(meta: Map<String, Any?>): String {
        val title = getReportTitle(meta)

        // استبدال أي حرف غير مسموح (غير A–Z, a–z, 0–9, _ , -) بـ "_"
        return title.replace("[^A-Za-z0-9-_]".toRegex(), "_")
    }

}
