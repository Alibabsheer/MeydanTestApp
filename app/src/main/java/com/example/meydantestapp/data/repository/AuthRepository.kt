package com.example.meydantestapp.repository

import com.example.meydantestapp.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * AuthRepository
 *
 * يعتمد نمط المستودع (Repository) لعمليات المصادقة وتخزين بيانات المؤسسة/المستخدم.
 * يلتزم بالتالي:
 *  - المؤسسة تُحفظ في organizations/{ownerUid}
 *  - المستخدم التابع يُحفظ في organizations/{orgId}/users/{uid} (مجلد داخلي)
 *  - مرآة الدخول تُحفظ في userslogin/{uid} (مجلد علوي لاستخدام شاشة الدخول والتوجيه)
 *  - التحقق من كود الانضمام يتم بعد إنشاء حساب Auth (للالتزام بقواعد Firestore التي تشترط request.auth != null)
 *    وفي حال كان الكود غير صحيح يتم حذف حساب Auth الذي تم إنشاؤه ثم إرجاع خطأ.
 */
class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /** تسجيل الدخول بالبريد وكلمة المرور */
    suspend fun loginUser(email: String, password: String): Result<String> = try {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val userId = result.user?.uid ?: throw Exception("User ID is null")
        Result.success(userId)
    } catch (e: Exception) { Result.failure(e) }

    /**
     * تسجيل مؤسسة جديدة: يُنشئ مستخدم Auth ثم يحفظ وثيقة المؤسسة تحت organizations/{ownerUid}.
     */
    suspend fun registerOrganization(
        organizationName: String,
        activityType: String,
        email: String,
        password: String
    ): Result<String> = try {
        // 1) إنشاء حساب المصادقة
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val ownerUid = authResult.user?.uid ?: throw Exception("User ID is null")

        // (اختياري) تعيين الاسم الظاهر لحساب المؤسسة
        try {
            val profile = UserProfileChangeRequest.Builder().setDisplayName(organizationName).build()
            authResult.user?.updateProfile(profile)?.await()
        } catch (_: Exception) { /* غير حرج */ }

        // 2) إنشاء كود انضمام عشوائي (أحرف كبيرة)
        val joinCode = generateJoinCode().uppercase()

        // 3) حفظ وثيقة المؤسسة
        val organizationData = hashMapOf(
            "organizationName" to organizationName,
            "activityType" to activityType,
            "email" to email,
            "joinCode" to joinCode,
            "createdAt" to FieldValue.serverTimestamp(),
            "ownerId" to ownerUid
        )
        firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(ownerUid)
            .set(organizationData)
            .await()

        Result.success(ownerUid)
    } catch (e: Exception) { Result.failure(e) }

    /**
     * تسجيل مستخدم تابع لمؤسسة موجودة.
     *
     * المراحل:
     *  1) إنشاء حساب Auth للمستخدم الجديد (ليصبح request.auth != null وفق قواعد Firestore).
     *  2) قراءة وثيقة المؤسسة والتحقق من تطابق joinCode (أحرف كبيرة).
     *     - إذا فشل التحقق: حذف حساب Auth الذي تم إنشاؤه ثم إرجاع خطأ.
     *  3) كتابة بيانات المستخدم في المجلد الداخلي organizations/{orgId}/users/{uid}.
     *  4) كتابة مرآة الدخول المبسطة في userslogin/{uid}.
     */
    suspend fun registerAffiliatedUser(
        userName: String,
        email: String,
        password: String,
        organizationId: String,
        joinCode: String
    ): Result<String> = try {
        // 1) إنشاء حساب المصادقة للمستخدم التابع
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val userId = authResult.user?.uid ?: throw Exception("User ID is null")

        // (اختياري) حفظ الاسم الظاهر لحساب المستخدم
        try {
            val profile = UserProfileChangeRequest.Builder().setDisplayName(userName).build()
            authResult.user?.updateProfile(profile)?.await()
        } catch (_: Exception) { /* غير حرج */ }

        // 2) قراءة وثيقة المؤسسة والتحقق من كود الانضمام (الآن لدينا request.auth != null)
        val orgDoc = firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(organizationId)
            .get()
            .await()
        if (!orgDoc.exists()) {
            // تنظيف: حذف حساب Auth الذي تم إنشاؤه
            safeDeleteCurrentUser()
            throw Exception("Organization not found")
        }
        val savedJoin = orgDoc.getString("joinCode")?.uppercase() ?: ""
        if (savedJoin != joinCode.uppercase()) {
            // تنظيف: حذف حساب Auth الذي تم إنشاؤه
            safeDeleteCurrentUser()
            throw Exception("Invalid join code")
        }
        val orgName = orgDoc.getString("organizationName") ?: ""

        // 3) كتابة بيانات المستخدم في المجلد الداخلي
        val nestedUserData = hashMapOf(
            "uid" to userId,
            "name" to userName,
            "email" to email,
            "organizationId" to organizationId,
            "organizationName" to orgName,
            "accountType" to "user",
            "role" to "user",
            "createdAt" to FieldValue.serverTimestamp()
        )
        firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(organizationId)
            .collection(Constants.COLLECTION_USERS)
            .document(userId)
            .set(nestedUserData)
            .await()

        // 4) كتابة مرآة الدخول في userslogin/{uid}
        val loginMirror = hashMapOf(
            "uid" to userId,
            "email" to email,
            "organizationId" to organizationId,
            "createdAt" to FieldValue.serverTimestamp()
        )
        firestore.collection(Constants.COLLECTION_USERSLOGIN)
            .document(userId)
            .set(loginMirror)
            .await()

        Result.success(userId)
    } catch (e: Exception) { Result.failure(e) }

    /**
     * استرجاع بيانات المستخدم لتحديد نوع الحساب والتوجيه:
     *  - إن وُجدت وثيقة في organizations/{uid} ⇒ حساب مؤسسة.
     *  - غير ذلك نفحص userslogin/{uid} ⇒ حساب تابع.
     */
    suspend fun getUserData(userId: String): Result<Map<String, Any>> {
        return try {
            // مؤسسة؟
            val orgDoc = firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
                .document(userId)
                .get()
                .await()
            if (orgDoc.exists()) {
                val data = orgDoc.data ?: emptyMap<String, Any>()
                val enriched = HashMap(data)
                enriched["userType"] = Constants.USER_TYPE_ORGANIZATION
                enriched["organizationId"] = userId
                return Result.success(enriched)
            }

            // تابع؟ (مرآة الدخول)
            val loginDoc = firestore.collection(Constants.COLLECTION_USERSLOGIN)
                .document(userId)
                .get()
                .await()
            if (loginDoc.exists()) {
                val orgId = loginDoc.getString("organizationId") ?: ""
                val data = hashMapOf<String, Any>(
                    "uid" to userId,
                    "organizationId" to orgId,
                    "userType" to Constants.USER_TYPE_AFFILIATED
                )
                return Result.success(data)
            }

            throw Exception("User data not found")
        } catch (e: Exception) { Result.failure(e) }
    }

    fun logout() = auth.signOut()

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    // حذف المستخدم الحالي بشكل آمن (يُستخدم عند فشل تحقق الكود بعد إنشاء الحساب)
    private suspend fun safeDeleteCurrentUser() {
        try { auth.currentUser?.delete()?.await() } catch (_: Exception) {}
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8).map { chars.random() }.joinToString("")
    }
}
