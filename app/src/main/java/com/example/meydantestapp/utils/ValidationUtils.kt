package com.example.meydantestapp.utils

import android.util.Patterns

object ValidationUtils {
    
    fun isValidEmail(email: String): Boolean {
        return email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    fun isValidPassword(password: String): Boolean {
        return password.length >= Constants.MIN_PASSWORD_LENGTH
    }
    
    fun doPasswordsMatch(password: String, confirmPassword: String): Boolean {
        return password == confirmPassword
    }
    
    fun isValidOrganizationName(name: String): Boolean {
        return name.isNotEmpty() && name.length <= Constants.MAX_ORGANIZATION_NAME_LENGTH
    }
    
    fun isValidProjectName(name: String): Boolean {
        return name.isNotEmpty() && name.length <= Constants.MAX_PROJECT_NAME_LENGTH
    }
    
    fun isValidJoinCode(code: String): Boolean {
        return code.isNotEmpty() && code.length >= 6
    }
    
    fun getEmailErrorMessage(email: String): String? {
        return when {
            email.isEmpty() -> "البريد الإلكتروني مطلوب"
            !isValidEmail(email) -> "البريد الإلكتروني غير صحيح"
            else -> null
        }
    }
    
    fun getPasswordErrorMessage(password: String): String? {
        return when {
            password.isEmpty() -> "كلمة المرور مطلوبة"
            !isValidPassword(password) -> "كلمة المرور يجب أن تكون ${Constants.MIN_PASSWORD_LENGTH} أحرف على الأقل"
            else -> null
        }
    }
    
    fun getConfirmPasswordErrorMessage(password: String, confirmPassword: String): String? {
        return when {
            confirmPassword.isEmpty() -> "تأكيد كلمة المرور مطلوب"
            !doPasswordsMatch(password, confirmPassword) -> "كلمات المرور غير متطابقة"
            else -> null
        }
    }
    
    fun getOrganizationNameErrorMessage(name: String): String? {
        return when {
            name.isEmpty() -> "اسم المؤسسة مطلوب"
            name.length > Constants.MAX_ORGANIZATION_NAME_LENGTH -> "اسم المؤسسة طويل جداً"
            else -> null
        }
    }
    
    fun getProjectNameErrorMessage(name: String): String? {
        return when {
            name.isEmpty() -> "اسم المشروع مطلوب"
            name.length > Constants.MAX_PROJECT_NAME_LENGTH -> "اسم المشروع طويل جداً"
            else -> null
        }
    }
}

