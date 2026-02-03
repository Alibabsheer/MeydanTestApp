package com.example.meydantestapp

import java.io.Serializable

// كلاس المؤسسة (توافق مع حقول Firestore الحالية + إبقاء الحقول الأصلية)
data class Organization(
    val organizationId: String = "",         // Document ID للمؤسسة (uid للمالك)
    val displayId: String = "",
    val name: String = "",
    val joinCode: String = "",
    val createdBy: String = "",
    val accountType: String = "organization",
    // حقول اختيارية للتوافق مع الحفظ في AuthRepository.registerOrganization
    val activityType: String? = null,
    val email: String? = null,
    val email_lower: String? = null,
    val ownerId: String? = null,
    val createdAt: Long? = null
) : Serializable

// كلاس المستخدم (إبقاء الحقول + إضافة حقول اختيارية للتوافق)
data class User(
    val uid: String = "",
    val displayId: String = "",
    val name: String = "",
    val email: String = "",
    val organizationId: String = "",
    val organizationName: String = "",
    val role: String = "",
    val accountType: String = "user",        // متوافق مع شاشاتك الحالية
    // حقول اختيارية للتوافق مع ما يُكتب في المستودع
    val userType: String? = null,             // قد تُملأ بـ "affiliated" في بعض المواضع
    val createdAt: com.google.firebase.Timestamp? = null,
    val phone: String? = null,
    val email_lower: String? = null
) : Serializable

// نموذج اختياري لقراءة userslogin/{uid} بسرعة عند تسجيل الدخول
// لا يؤثر على أي كود حالي، لكنه مفيد عند الحاجة

data class UsersLoginEntry(
    val uid: String = "",
    val organizationId: String = "",
    val accountType: String = "affiliated",  // يتوافق مع Constants.USER_TYPE_AFFILIATED
    val email_lower: String = "",
    val createdAt: Long = 0L,
    val lastLoginAt: Long = 0L
) : Serializable
