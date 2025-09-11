package com.example.meydantestapp.repository

import android.net.Uri
import com.example.meydantestapp.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * DailyReportRepository (Hierarchical)
 * التخزين/الجلب عبر:
 * organizations/{organizationId}/projects/{projectId}/dailyReports/{reportId}
 *
 * مسار الصور (قديم للتوافق):
 * daily_reports/{reportId}/photos/photo_XXX.jpg
 *
 * مسار صفحات الشبكات (جديد):
 * daily_reports/{reportId}/pages/page_XXX.webp
 *
 * حقول Firestore المحتملة:
 * - photos: [String] روابط الصور المفردة (توافق)
 * - sitepages: [String] روابط صور الصفحات المركّبة (اختياري)
 * - sitepagesmeta: [ { templateId, pageIndex, slots: [ {originalUrl, caption, slotIndex} ] } ]
 */
class DailyReportRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ===== مسارات Firestore =====
    private fun projectDocRef(organizationId: String, projectId: String) =
        firestore.collection(Constants.COLLECTION_ORGANIZATIONS).document(organizationId)
            .collection(Constants.COLLECTION_PROJECTS).document(projectId)

    private fun dailyReportsCol(organizationId: String, projectId: String) =
        projectDocRef(organizationId, projectId)
            .collection(Constants.SUBCOLLECTION_DAILY_REPORTS)

    // ============================
    // منطق تحديد نوع الحساب + الاسم
    // ============================
    private data class CreatorInfo(
        val uid: String,
        val isOrganization: Boolean,
        val displayName: String
    )

    /**
     * يعتبر الحساب مؤسسة إذا وُجدت وثيقة في organizations/{uid}.
     * وإلا فهو مستخدم تابع: نقرأ organizationId من userslogin/{uid} ثم وثيقته في organizations/{orgId}/users/{uid}
     * ملاحظة: لا تُستدعى داخل Transaction.
     */
    private suspend fun getCreatorInfo(): CreatorInfo {
        val uid = auth.currentUser?.uid ?: error("User not logged in")

        // 1) هل هو حساب مؤسسة؟
        val orgDoc = firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(uid)
            .get()
            .await()
        if (orgDoc.exists()) {
            return CreatorInfo(
                uid = uid,
                isOrganization = true,
                displayName = "الحساب الرئيسي"
            )
        }

        // 2) حساب تابع: جلب orgId من userslogin/{uid}
        val loginDoc = firestore.collection(Constants.COLLECTION_USERSLOGIN)
            .document(uid)
            .get()
            .await()
        val orgId = loginDoc.getString("organizationId")

        // ثم محاولة جلب الاسم من organizations/{orgId}/users/{uid}
        val displayName = if (!orgId.isNullOrBlank()) {
            val nestedUser = firestore.collection(Constants.COLLECTION_ORGANIZATIONS)
                .document(orgId)
                .collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get()
                .await()
            nestedUser.getString("fullName")
                ?: nestedUser.getString("name")
                ?: auth.currentUser?.displayName
                ?: "مستخدم"
        } else {
            auth.currentUser?.displayName ?: "مستخدم"
        }

        return CreatorInfo(uid = uid, isOrganization = false, displayName = displayName)
    }

    /**
     * يثري بيانات التقرير بحقول المُنشئ + يفرض ضبط نوع/قيمة درجة الحرارة.
     * - دائماً createdBy = uid الحالي إذا لم تكن موجودة.
     * - إن كان الحساب مؤسسة: يفرض createdByName = "الحساب الرئيسي".
     * - إن كان الحساب تابع: لا يغيّر إن كانت موجودة؛ وإلا يملؤها باسم المستخدم.
     * - يفرض الحقل "temperature" كـ Int عبر التقريب.
     */
    private suspend fun enrichAndSanitize(base: Map<String, Any>): Map<String, Any> {
        val info = getCreatorInfo()
        val data = base.toMutableMap()

        if (!data.containsKey("createdBy")) data["createdBy"] = info.uid
        if (info.isOrganization) {
            data["createdByName"] = "الحساب الرئيسي"
        } else if (!data.containsKey("createdByName")) {
            data["createdByName"] = info.displayName
        }

        sanitizeTemperature(data)
        return data
    }

    /** يحول أي قيمة إلى Int بالتقريب إن أمكن */
    private fun anyToIntRounded(value: Any?): Int? = when (value) {
        null -> null
        is Int -> value
        is Long -> value.toInt()
        is Float -> value.roundToInt()
        is Double -> value.roundToInt()
        is Number -> value.toDouble().roundToInt()
        is String -> value.trim().let { s ->
            val filtered = s.filter { ch -> ch.isDigit() || ch == '.' || ch == '+' || ch == '-' }
            filtered.toDoubleOrNull()?.roundToInt()
        }
        else -> null
    }

    /** يضبط الحقل "temperature" ليُخزَّن كـ Int (إن كان موجودًا) */
    private fun sanitizeTemperature(map: MutableMap<String, Any>) {
        val raw = map["temperature"]
        val intVal = anyToIntRounded(raw)
        if (intVal != null) map["temperature"] = intVal
    }

    // ============================
    // إنشاء/تحديث التقارير
    // ============================

    /** إنشاء تقرير بمعرّف يولده Firestore (هرمي) */
    suspend fun createDailyReport(
        organizationId: String,
        projectId: String,
        reportData: Map<String, Any>
    ): Result<String> = runCatching {
        val finalData = enrichAndSanitize(reportData)
        val docRef = dailyReportsCol(organizationId, projectId).add(finalData).await()
        docRef.id
    }

    /** إنشاء/تحديث تقرير بمعرّف محدد (بدون ترقيم) — للتوافق */
    suspend fun createDailyReportWithId(
        organizationId: String,
        projectId: String,
        reportId: String,
        reportData: Map<String, Any>
    ): Result<Unit> = runCatching {
        val finalData = enrichAndSanitize(reportData)
        dailyReportsCol(organizationId, projectId)
            .document(reportId)
            .set(finalData, SetOptions.merge())
            .await()
    }

    /**
     * إنشاء/تحديث تقرير بمعرّف محدد مع توليد رقم تسلسلي reportNumber و reportIndex.
     * يقبل ضمن baseReportData أي حقول إضافية (sitepages, sitepagesmeta, photos...).
     */
    suspend fun createDailyReportAutoNumbered(
        organizationId: String,
        projectId: String,
        reportId: String,
        baseReportData: Map<String, Any>
    ): Result<Unit> = runCatching {
        val enriched = enrichAndSanitize(baseReportData) // لا تُستدعى داخل الترانزاكشن

        firestore.runTransaction { tx ->
            val projRef = projectDocRef(organizationId, projectId)
            val projSnap = tx.get(projRef)
            val currentSeq = (projSnap.getLong(Constants.FIELD_DAILY_REPORT_SEQ) ?: 0L)
            val nextSeq = currentSeq + 1L

            // تحديث عدّاد المشروع
            tx.set(
                projRef,
                mapOf(Constants.FIELD_DAILY_REPORT_SEQ to nextSeq),
                SetOptions.merge()
            )

            // تجهيز بيانات التقرير مع الرقم التسلسلي
            val reportRef = dailyReportsCol(organizationId, projectId).document(reportId)
            val data = enriched.toMutableMap().apply {
                this[Constants.FIELD_REPORT_INDEX] = nextSeq
                this[Constants.FIELD_REPORT_NUMBER] = "DailyReport-$nextSeq"
            }
            tx.set(reportRef, data, SetOptions.merge())
            null
        }.await()
    }

    /** منع تكرار تقرير لنفس التاريخ داخل Subcollection (date محفوظ كـ Long ms) */
    suspend fun existsReportForDate(
        organizationId: String,
        projectId: String,
        dateMillis: Long
    ): Result<Boolean> = runCatching {
        val snap = dailyReportsCol(organizationId, projectId)
            .whereEqualTo("date", dateMillis)
            .limit(1)
            .get().await()
        !snap.isEmpty
    }

    // ============================
    // رفع الصور القديمة (توافق)
    // ============================

    /** رفع صور التقرير إلى daily_reports/{reportId}/photos (بدون إبلاغ تقدّم) — بأسلوب متسامح مع الأعطال. */
    suspend fun uploadPhotosToFirebaseStorage(
        reportId: String,
        uris: List<Uri>
    ): Result<List<String>> = runCatching {
        if (uris.isEmpty()) return@runCatching emptyList<String>()
        val baseRef = storage.reference.child("daily_reports/$reportId/photos")
        val urls = mutableListOf<String>()
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()

        uris.forEachIndexed { index, uri ->
            try {
                val name = String.format(Locale.US, "photo_%03d.jpg", index + 1)
                val ref = baseRef.child(name)
                ref.putFile(uri, metadata).await()
                urls += ref.downloadUrl.await().toString()
            } catch (_: Exception) {
                // تخطّي الصورة المعطوبة ومتابعة الرفع للبقية
            }
        }
        urls
    }

    /** رفع صور التقرير مع إبلاغ تقدّم إجمالي 0..100 — بأسلوب متسامح مع الأعطال لكل صورة. */
    suspend fun uploadPhotosToFirebaseStorage(
        reportId: String,
        uris: List<Uri>,
        onProgress: ((Int) -> Unit)?
    ): Result<List<String>> = runCatching {
        if (uris.isEmpty()) return@runCatching emptyList<String>()

        val baseRef = storage.reference.child("daily_reports/$reportId/photos")
        val total = uris.size
        val urls = mutableListOf<String>()
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        onProgress?.invoke(0)

        uris.forEachIndexed { index, uri ->
            try {
                val name = String.format(Locale.US, "photo_%03d.jpg", index + 1)
                val ref = baseRef.child(name)
                val task = ref.putFile(uri, metadata)

                task.addOnProgressListener { snap ->
                    val perFile = if (snap.totalByteCount > 0) {
                        snap.bytesTransferred.toDouble() / snap.totalByteCount
                    } else 0.0
                    val overall = (((index) + perFile) / total * 100.0).toInt().coerceIn(0, 99)
                    onProgress?.invoke(overall)
                }

                task.await()
                val url = ref.downloadUrl.await().toString()
                urls += url
                val overallAfter = (((index + 1).toDouble() / total) * 100.0).toInt().coerceIn(0, 100)
                onProgress?.invoke(overallAfter)
            } catch (_: Exception) {
                // تخطّي هذه الصورة ومتابعة الباقي
                val overallAfter = (((index + 1).toDouble() / total) * 100.0).toInt().coerceIn(0, 100)
                onProgress?.invoke(overallAfter)
            }
        }

        onProgress?.invoke(100)
        urls
    }

    // ============================
    // رفع صفحات الشبكات (جديدة)
    // ============================

    /**
     * يرفع صور صفحات الشبكات (WebP) إلى: daily_reports/{reportId}/pages/page_XXX.webp
     * ويُرجع روابط التنزيل بالترتيب نفسه.
     */
    suspend fun uploadGridPages(
        reportId: String,
        pageUris: List<Uri>,
        onProgress: ((Int) -> Unit)? = null
    ): Result<List<String>> = runCatching {
        if (pageUris.isEmpty()) return@runCatching emptyList<String>()
        val baseRef = storage.reference.child("daily_reports/$reportId/pages")
        val urls = MutableList(pageUris.size) { "" }
        val metadata = StorageMetadata.Builder().setContentType("image/webp").build()
        onProgress?.invoke(0)

        pageUris.forEachIndexed { index, uri ->
            try {
                val name = String.format(Locale.US, "page_%03d.webp", index + 1)
                val ref = baseRef.child(name)
                val task = ref.putFile(uri, metadata)

                task.addOnProgressListener { snap ->
                    val perFile = if (snap.totalByteCount > 0) {
                        (100.0 * snap.bytesTransferred / snap.totalByteCount)
                    } else 0.0
                    val completedBefore = index
                    val total = pageUris.size
                    val overall = (((completedBefore * 100.0) + perFile) / total).toInt().coerceIn(0, 99)
                    onProgress?.invoke(overall)
                }

                task.await()
                urls[index] = ref.downloadUrl.await().toString()
                val overallAfter = (((index + 1) * 100.0) / pageUris.size).toInt().coerceIn(0, 100)
                onProgress?.invoke(overallAfter)
            } catch (_: Exception) {
                // تخطّي الصفحة المعطوبة واستمر
                val overallAfter = (((index + 1) * 100.0) / pageUris.size).toInt().coerceIn(0, 100)
                onProgress?.invoke(overallAfter)
            }
        }
        onProgress?.invoke(100)
        urls.filter { it.isNotBlank() }
    }

    /** يكتب/يُحدّث حقول sitepages و sitepagesmeta في وثيقة التقرير. */
    suspend fun writeSitePages(
        organizationId: String,
        projectId: String,
        reportId: String,
        pageUrls: List<String>,
        pagesMeta: List<Map<String, Any>>
    ): Result<Unit> = runCatching {
        val updates = mapOf(
            "sitepages" to pageUrls,
            "sitepagesmeta" to pagesMeta
        )
        dailyReportsCol(organizationId, projectId)
            .document(reportId)
            .set(updates, SetOptions.merge())
            .await()
    }

    // ============================
    // استعلامات التقارير
    // ============================

    /** جلب تقارير مشروع واحد (هرمي) مرتبة بالأحدث */
    suspend fun getDailyReportsByProject(
        organizationId: String,
        projectId: String
    ): Result<List<Map<String, Any>>> = runCatching {
        val qs = dailyReportsCol(organizationId, projectId)
            .orderBy("date", Query.Direction.DESCENDING)
            .get().await()
        qs.documents.map { it.data!!.toMutableMap().apply { put("id", it.id) } }
    }

    /** جلب تقارير منظمة عبر collectionGroup (هرمي) */
    suspend fun getDailyReportsByOrganization(
        organizationId: String
    ): Result<List<Map<String, Any>>> = runCatching {
        val qs = firestore.collectionGroup(Constants.SUBCOLLECTION_DAILY_REPORTS)
            .whereEqualTo("organizationId", organizationId)
            .orderBy("date", Query.Direction.DESCENDING)
            .get().await()
        qs.documents.map { it.data!!.toMutableMap().apply { put("id", it.id) } }
    }

    /** جلب تقرير مفرد (هرمي) */
    suspend fun getDailyReportById(
        organizationId: String,
        projectId: String,
        reportId: String
    ): Result<Map<String, Any>> = runCatching {
        val doc = dailyReportsCol(organizationId, projectId).document(reportId).get().await()
        if (!doc.exists()) error("Report not found")
        val data = doc.data ?: error("Report data is null")
        data["id"] = doc.id
        data
    }

    /** تحديث تقرير (هرمي) */
    suspend fun updateDailyReport(
        organizationId: String,
        projectId: String,
        reportId: String,
        updates: Map<String, Any>
    ): Result<Unit> = runCatching {
        val sanitized = enrichAndSanitize(updates)
        dailyReportsCol(organizationId, projectId)
            .document(reportId).set(sanitized, SetOptions.merge()).await()
    }

    /** حذف تقرير (هرمي) */
    suspend fun deleteDailyReport(
        organizationId: String,
        projectId: String,
        reportId: String
    ): Result<Unit> = runCatching {
        dailyReportsCol(organizationId, projectId)
            .document(reportId).delete().await()
    }

    // ============================
    // توابع مساعدة للتواريخ
    // ============================

    /** تحويل ISO yyyy-MM-dd إلى millis عند بداية اليوم (UTC) */
    private fun isoToStartOfDayMillis(iso: String): Long {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return df.parse(iso)?.time ?: 0L
    }

    /** تحويل ISO yyyy-MM-dd إلى millis عند نهاية اليوم (UTC) */
    private fun isoToEndOfDayMillis(iso: String): Long {
        val start = isoToStartOfDayMillis(iso)
        return if (start == 0L) 0L else (start + 24L * 60 * 60 * 1000 - 1)
    }

    /** نطاق بالتاريخ (ISO) داخل مشروع (يُحوّل إلى Long) */
    suspend fun getReportsByDateRangeIso(
        organizationId: String,
        projectId: String,
        startDateIso: String,
        endDateIso: String
    ): Result<List<Map<String, Any>>> = runCatching {
        val startMs = isoToStartOfDayMillis(startDateIso)
        val endMs = isoToEndOfDayMillis(endDateIso)
        val qs = dailyReportsCol(organizationId, projectId)
            .whereGreaterThanOrEqualTo("date", startMs)
            .whereLessThanOrEqualTo("date", endMs)
            .orderBy("date", Query.Direction.DESCENDING)
            .get().await()
        qs.documents.map { it.data!!.toMutableMap().apply { put("id", it.id) } }
    }

    /** أرشفة ضمن نطاق (ISO) داخل مشروع (يُحوّل إلى Long) */
    suspend fun archiveDailyReportsInRangeIso(
        organizationId: String,
        projectId: String,
        startDateIso: String,
        endDateIso: String
    ): Result<Int> = runCatching {
        val startMs = isoToStartOfDayMillis(startDateIso)
        val endMs = isoToEndOfDayMillis(endDateIso)
        val snap = dailyReportsCol(organizationId, projectId)
            .whereEqualTo("isArchived", false)
            .whereGreaterThanOrEqualTo("date", startMs)
            .whereLessThanOrEqualTo("date", endMs)
            .get().await()
        if (snap.isEmpty) return@runCatching 0
        val batch = firestore.batch()
        snap.documents.forEach { batch.update(it.reference, mapOf("isArchived" to true)) }
        batch.commit().await()
        snap.size()
    }

    /** تقارير مستخدم حسب createdBy عبر collectionGroup (هرمي) */
    suspend fun getReportsForUser(userId: String): Result<List<Map<String, Any>>> = runCatching {
        val qs = firestore.collectionGroup(Constants.SUBCOLLECTION_DAILY_REPORTS)
            .whereEqualTo("createdBy", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
        qs.documents.map { it.data!!.toMutableMap().apply { put("id", it.id) } }
    }

    companion object {
        /** يحوّل رابط gs:// إلى https إن لزم */
        suspend fun toHttpsUrlIfNeeded(storage: FirebaseStorage, value: String): String {
            return if (value.startsWith("gs://")) {
                val ref = storage.getReferenceFromUrl(value)
                ref.downloadUrl.await().toString()
            } else {
                value
            }
        }

        /** يطبع قائمة روابط الصفحات لتصبح قابلة للعرض */
        suspend fun normalizePageUrls(values: List<String>?): List<String> {
            if (values.isNullOrEmpty()) return emptyList()
            val storage = FirebaseStorage.getInstance()
            return coroutineScope {
                values.map { v -> async { toHttpsUrlIfNeeded(storage, v) } }.awaitAll()
            }
        }
    }
}
