package com.example.meydantestapp.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    // رسائل الأخطاء
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // رسائل النجاح
    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

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
        _errorMessage.value = message
    }

    /**
     * عرض رسالة نجاح
     */
    protected fun showSuccess(message: String) {
        _successMessage.value = message
    }

    /**
     * عرض رسالة خطأ
     */
    protected fun showError(message: String) {
        _errorMessage.value = message
    }

    /**
     * مسح رسائل الأخطاء والنجاح
     */
    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }
}
