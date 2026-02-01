package com.example.meydantestapp.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.example.meydantestapp.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * ErrorHandler - معالج مركزي للأخطاء في التطبيق
 * 
 * يوفر طرق موحدة لمعالجة وعرض الأخطاء للمستخدم بشكل احترافي
 * 
 * الميزات:
 * - معالجة أخطاء Firebase (Auth, Firestore, Storage)
 * - معالجة أخطاء الشبكة
 * - معالجة أخطاء عامة
 * - رسائل واضحة ومفهومة للمستخدم
 * - تسجيل الأخطاء للتتبع
 */
object ErrorHandler {

    private const val TAG = "ErrorHandler"

    /**
     * معالجة الخطأ وعرض رسالة مناسبة للمستخدم
     * 
     * @param context السياق لعرض Toast
     * @param error الخطأ الذي حدث
     * @param customMessage رسالة مخصصة (اختياري)
     */
    fun handle(context: Context, error: Throwable, customMessage: String? = null) {
        val message = customMessage ?: getErrorMessage(context, error)
        showError(context, message)
        logError(error)
    }

    /**
     * معالجة الخطأ مع callback
     * 
     * @param context السياق
     * @param error الخطأ
     * @param onRetry دالة لإعادة المحاولة (اختياري)
     */
    fun handleWithRetry(
        context: Context,
        error: Throwable,
        onRetry: (() -> Unit)? = null
    ) {
        val message = getErrorMessage(context, error)
        showError(context, message)
        logError(error)
        
        // يمكن إضافة Snackbar مع زر إعادة المحاولة هنا
        onRetry?.let {
            // TODO: عرض Snackbar مع زر "إعادة المحاولة"
        }
    }

    /**
     * الحصول على رسالة خطأ مناسبة بناءً على نوع الخطأ
     */
    private fun getErrorMessage(context: Context, error: Throwable): String {
        return when (error) {
            // أخطاء الشبكة
            is UnknownHostException, is SocketTimeoutException -> {
                "لا يوجد اتصال بالإنترنت. يرجى التحقق من الاتصال."
            }
            is IOException -> {
                "حدث خطأ في الاتصال. يرجى المحاولة مرة أخرى."
            }
            is FirebaseNetworkException -> {
                "ضعف في الاتصال بالإنترنت. يرجى المحاولة مرة أخرى."
            }

            // أخطاء Firebase Auth
            is FirebaseAuthException -> {
                when (error.errorCode) {
                    "ERROR_INVALID_EMAIL" -> "البريد الإلكتروني غير صحيح."
                    "ERROR_WRONG_PASSWORD" -> "كلمة المرور غير صحيحة."
                    "ERROR_USER_NOT_FOUND" -> "المستخدم غير موجود."
                    "ERROR_USER_DISABLED" -> "تم تعطيل هذا الحساب."
                    "ERROR_TOO_MANY_REQUESTS" -> "تم تجاوز عدد المحاولات. يرجى المحاولة لاحقاً."
                    "ERROR_EMAIL_ALREADY_IN_USE" -> "البريد الإلكتروني مستخدم بالفعل."
                    "ERROR_WEAK_PASSWORD" -> "كلمة المرور ضعيفة جداً."
                    else -> "حدث خطأ في المصادقة: ${error.message}"
                }
            }

            // أخطاء Firestore
            is FirebaseFirestoreException -> {
                when (error.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        "ليس لديك صلاحية للقيام بهذا الإجراء."
                    }
                    FirebaseFirestoreException.Code.NOT_FOUND -> {
                        "البيانات المطلوبة غير موجودة."
                    }
                    FirebaseFirestoreException.Code.ALREADY_EXISTS -> {
                        "البيانات موجودة بالفعل."
                    }
                    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> {
                        "تم تجاوز حد الاستخدام. يرجى المحاولة لاحقاً."
                    }
                    FirebaseFirestoreException.Code.UNAVAILABLE -> {
                        "الخدمة غير متوفرة حالياً. يرجى المحاولة لاحقاً."
                    }
                    else -> "حدث خطأ في قاعدة البيانات: ${error.message}"
                }
            }

            // أخطاء Firebase عامة
            is FirebaseTooManyRequestsException -> {
                "تم تجاوز عدد الطلبات. يرجى المحاولة لاحقاً."
            }
            is FirebaseException -> {
                "حدث خطأ في الخدمة: ${error.message}"
            }

            // أخطاء عامة
            is IllegalArgumentException -> {
                "بيانات غير صحيحة: ${error.message}"
            }
            is IllegalStateException -> {
                "حالة غير صالحة: ${error.message}"
            }
            is NullPointerException -> {
                "بيانات مفقودة. يرجى المحاولة مرة أخرى."
            }

            // خطأ غير معروف
            else -> {
                context.getString(R.string.error_generic)
            }
        }
    }

    /**
     * عرض رسالة الخطأ للمستخدم
     */
    private fun showError(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * تسجيل الخطأ في Logcat
     */
    private fun logError(error: Throwable) {
        Log.e(TAG, "Error occurred: ${error.javaClass.simpleName}", error)
        error.printStackTrace()
    }

    /**
     * معالجة خطأ بدون عرض Toast (للاستخدام في ViewModels)
     * 
     * @param error الخطأ
     * @return رسالة الخطأ
     */
    fun getErrorMessage(error: Throwable): String {
        return when (error) {
            is UnknownHostException, is SocketTimeoutException -> {
                "لا يوجد اتصال بالإنترنت"
            }
            is FirebaseNetworkException -> {
                "ضعف في الاتصال بالإنترنت"
            }
            is FirebaseAuthException -> {
                when (error.errorCode) {
                    "ERROR_INVALID_EMAIL" -> "البريد الإلكتروني غير صحيح"
                    "ERROR_WRONG_PASSWORD" -> "كلمة المرور غير صحيحة"
                    "ERROR_USER_NOT_FOUND" -> "المستخدم غير موجود"
                    else -> "خطأ في المصادقة"
                }
            }
            is FirebaseFirestoreException -> {
                when (error.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        "ليس لديك صلاحية"
                    }
                    FirebaseFirestoreException.Code.NOT_FOUND -> {
                        "البيانات غير موجودة"
                    }
                    else -> "خطأ في قاعدة البيانات"
                }
            }
            else -> error.message ?: "حدث خطأ غير متوقع"
        }
    }

    /**
     * التحقق من نوع الخطأ
     */
    fun isNetworkError(error: Throwable): Boolean {
        return error is UnknownHostException ||
                error is SocketTimeoutException ||
                error is FirebaseNetworkException ||
                error is IOException
    }

    fun isAuthError(error: Throwable): Boolean {
        return error is FirebaseAuthException
    }

    fun isPermissionError(error: Throwable): Boolean {
        return error is FirebaseFirestoreException &&
                error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
}
