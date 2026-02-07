package com.example.meydantestapp.ui.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.common.UiMessage
import com.example.meydantestapp.utils.ErrorHandler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

/**
 * BaseViewModel - ViewModel أساسي لجميع ViewModels في التطبيق
 * 
 * يوفر:
 * - معالجة موحدة للأخطاء
 * - إدارة حالة التحميل
 * - دوال مساعدة للعمليات غير المتزامنة
 */
abstract class BaseViewModel : ViewModel() {

    // حالة التحميل
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // رسائل واجهة المستخدم (أخطاء، نجاح، معلومات)
    private val _uiMessage = MutableLiveData<UiMessage?>()
    val uiMessage: LiveData<UiMessage?> = _uiMessage

    /**
     * تنفيذ عملية غير متزامنة مع معالجة الأخطاء تلقائياً
     */
    protected fun launchSafe(
        showLoading: Boolean = true,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> Unit
    ) {
        if (showLoading) {
            _isLoading.value = true
        }

        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            handleError(throwable)
            onError?.invoke(throwable)
            if (showLoading) {
                _isLoading.value = false
            }
        }

        viewModelScope.launch(exceptionHandler) {
            try {
                block()
            } catch (e: Exception) {
                handleError(e)
                onError?.invoke(e)
            } finally {
                if (showLoading) {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * تنفيذ عملية غير متزامنة مع معالجة Result
     */
    protected fun <T> launchWithResult(
        showLoading: Boolean = true,
        onSuccess: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> Result<T>
    ) {
        launchSafe(showLoading, onError) {
            val result = block()
            result.fold(
                onSuccess = { data ->
                    onSuccess(data)
                },
                onFailure = { error ->
                    handleError(error)
                    onError?.invoke(error)
                }
            )
        }
    }

    /**
     * معالجة الخطأ وتحويله إلى رسالة مفهومة
     */
    private fun handleError(error: Throwable) {
        val message = ErrorHandler.getErrorMessage(error)
        _uiMessage.value = UiMessage.RawString(message)
    }

    /**
     * عرض رسالة من strings.xml
     */
    protected fun showMessage(@StringRes resId: Int) {
        _uiMessage.value = UiMessage.StringResource(resId)
    }

    /**
     * عرض رسالة من strings.xml مع معاملات
     */
    protected fun showMessage(@StringRes resId: Int, vararg args: Any) {
        _uiMessage.value = UiMessage.StringResourceWithArgs(resId, args.toList().toTypedArray())
    }

    /**
     * عرض نص مباشر (استخدم بحذر - للحالات الاستثنائية فقط)
     */
    protected fun showRawMessage(text: String) {
        _uiMessage.value = UiMessage.RawString(text)
    }

    /**
     * مسح الرسائل
     */
    fun clearMessage() {
        _uiMessage.value = null
    }
}
