package com.example.meydantestapp

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * يمثل صورة مختارة لرفعها ضمن التقرير اليومي.
 * - uri: مسار الصورة من الاستديو/الالتقاط.
 * - caption: تعليق اختياري (على نمط واتساب) يُضاف قبل الرفع.
 * - index: رقم الترتيب أثناء الاختيار/العرض (1..10)، -1 يعني غير مُحدد.
 *
 * ملاحظة: تم تحويل الكائن إلى **Parcelable** بدل Serializable لتجنّب الكراش عند تمرير Uri
 * داخل Intent extras، ولضمان أداء أفضل على أندرويد.
 */
@Parcelize
data class SelectedImage(
    val uri: Uri,
    var caption: String = "",
    var index: Int = -1
) : Parcelable
