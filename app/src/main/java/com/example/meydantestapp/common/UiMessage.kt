package com.example.meydantestapp.common

import androidx.annotation.StringRes

/**
 * UiMessage - تمثيل موحد لرسائل واجهة المستخدم
 * 
 * يُستخدم لإرسال رسائل من ViewModels إلى Activities/Fragments
 * بدون الحاجة لتمرير Context إلى ViewModels
 */
sealed class UiMessage {
    
    /**
     * رسالة من strings.xml
     * 
     * مثال:
     * ```
     * UiMessage.StringResource(R.string.success_project_created)
     * ```
     */
    data class StringResource(@StringRes val resId: Int) : UiMessage()
    
    /**
     * رسالة من strings.xml مع معاملات
     * 
     * مثال:
     * ```
     * // strings.xml: <string name="error_with_reason">فشل: %s</string>
     * UiMessage.StringResourceWithArgs(R.string.error_with_reason, arrayOf("سبب الخطأ"))
     * ```
     */
    data class StringResourceWithArgs(
        @StringRes val resId: Int,
        val args: Array<Any>
    ) : UiMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as StringResourceWithArgs
            
            if (resId != other.resId) return false
            if (!args.contentEquals(other.args)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = resId
            result = 31 * result + args.contentHashCode()
            return result
        }
    }
    
    /**
     * نص مباشر (Raw String)
     * 
     * ⚠️ استخدم فقط في الحالات الاستثنائية مثل:
     * - رسائل من APIs خارجية
     * - رسائل ديناميكية من Firebase
     * - نصوص لا يمكن وضعها في strings.xml
     * 
     * مثال:
     * ```
     * UiMessage.RawString(apiResponse.errorMessage)
     * ```
     */
    data class RawString(val text: String) : UiMessage()
}
