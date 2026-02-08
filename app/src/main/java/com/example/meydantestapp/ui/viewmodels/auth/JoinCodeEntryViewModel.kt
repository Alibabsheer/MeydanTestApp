package com.example.meydantestapp.ui.viewmodels.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.example.meydantestapp.ui.viewmodels.BaseViewModel

/**
 * ViewModel يتحقق من كود الانضمام عبر Cloud Function: verifyJoinCode (us-central1)
 * ويعيد توجيه المستخدم إلى شاشة RegisterAffiliatedUserActivity عند النجاح.
 *
 * لا نستخدم Event wrapper جديد؛ بدلاً من ذلك نمرّر كائن ملاحة مرة واحدة
 * ثم نعيده إلى null بعد الاستهلاك من الـ Activity.
 */
class JoinCodeEntryViewModel : BaseViewModel() {

    // لعرض/إخفاء مؤشّر التحميل إن رغبت
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    // نفس الاسم الذي تتوقعه الـ Activity
    private val _showToast = MutableLiveData<String>()
    val showToast: LiveData<String> get() = _showToast

    // نفس الاسم الذي تتوقعه الـ Activity
    private val _navigateToRegisterAffiliatedUser = MutableLiveData<NavData?>()
    val navigateToRegisterAffiliatedUser: LiveData<NavData?> get() = _navigateToRegisterAffiliatedUser

    // دالة بنفس الاسم الذي تستدعيه الـ Activity
    fun checkJoinCode(rawCode: String) {
        // ✅ تعديل: تحويل الكود إلى أحرف كبيرة
        val joinCode = rawCode.trim().uppercase()
        if (joinCode.isEmpty()) {
            _showToast.postValue("يرجى إدخال كود الانضمام.")
            return
        }

        _isLoading.postValue(true)

        // region مطابقة لنشر الدالة
        val functions = Firebase.functions("us-central1")

        val payload = hashMapOf("joinCode" to joinCode)
        functions
            .getHttpsCallable("verifyJoinCode")
            .call(payload)
            .addOnSuccessListener { result ->
                _isLoading.postValue(false)
                val data = result.data as? Map<*, *>

                // ✅ التعديل هنا: نتحقق من وجود حقل organizationId مباشرةً
                val orgId = data?.get("organizationId") as? String
                val orgName = data?.get("organizationName") as? String
                if (orgId != null && orgName != null) {
                    // ندفع حدث الملاحة؛ ستلتقطه الـ Activity وتنتقل للشاشة التالية
                    _navigateToRegisterAffiliatedUser.postValue(
                        NavData(
                            organizationId = orgId,
                            organizationName = orgName,
                            joinCode = joinCode
                        )
                    )
                } else {
                    // إذا لم يتم العثور على الأيدي والاسم، نعرض الخطأ
                    _showToast.postValue("الكود غير صحيح أو منتهي الصلاحية.")
                }
            }
            .addOnFailureListener { e ->
                _isLoading.postValue(false)
                _showToast.postValue("فشل التحقق من الكود: ${e.message ?: "خطأ غير معروف"}")
            }
    }

    /** تناديها الـ Activity بعد تنفيذ الملاحة حتى لا يتكرر الحدث. */
    fun clearNavigation() {
        _navigateToRegisterAffiliatedUser.postValue(null)
    }

    /** بيانات الملاحة للشاشة التالية. */
    data class NavData(
        val organizationId: String,
        val organizationName: String,
        val joinCode: String
    )
}
