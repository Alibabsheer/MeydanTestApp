package com.example.meydantestapp.repository

import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.ProjectLocationUtils
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * ProjectRepository
 * - هيكل Firestore المتداخل:
 *   organizations/{organizationId}/projects/{projectId}
 * - يعتمد projectName كمفتاح الاسم (ويُنسخ إلى name للتوافق الخلفي).
 */
class ProjectRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private companion object {
        const val ORGANIZATIONS = "organizations"
    }

    // مرجع مجموعة مشاريع المؤسسة
    private fun orgProjectsRef(organizationId: String) =
        firestore.collection(ORGANIZATIONS)
            .document(organizationId)
            .collection(Constants.COLLECTION_PROJECTS)

    /** إنشاء مشروع عبر خريطة بيانات */
    suspend fun createProject(
        organizationId: String,
        projectData: Map<String, Any?>
    ): Result<String> = runCatching {
        val docRef = orgProjectsRef(organizationId).document()
        val newId = docRef.id

        val base = projectData.toMutableMap()
        val normalizedAddress = ProjectLocationUtils.normalizeAddressText(
            (base["addressText"] ?: base["location"]) as? String
        )
        val normalizedPlusCode = ProjectLocationUtils.normalizePlusCode(base["plusCode"] as? String)
        if (normalizedAddress != null) {
            base["addressText"] = normalizedAddress
            base["location"] = normalizedAddress
        } else {
            base.remove("addressText")
            base.remove("location")
        }
        if (normalizedPlusCode != null) {
            base["plusCode"] = normalizedPlusCode
        } else {
            base.remove("plusCode")
        }

        if (base["googleMapsUrl"] == null) {
            ProjectLocationUtils.buildGoogleMapsUrl(base)?.let { base["googleMapsUrl"] = it }
        }

        val clean = base.filterValues { it != null }.toMutableMap()
        val incomingProjectName = (clean["projectName"] ?: clean["name"])?.toString()
        if (!incomingProjectName.isNullOrBlank()) {
            clean["projectName"] = incomingProjectName
            clean["name"] = incomingProjectName
        }

        clean["id"] = newId
        clean["projectId"] = newId
        clean["projectNumber"] = newId
        clean.putIfAbsent("createdAt", System.currentTimeMillis())
        clean.putIfAbsent("status", "active")

        docRef.set(clean).await()
        newId
    }

    /** إنشاء مشروع عبر حقول منفصلة */
    suspend fun createProject(
        projectName: String,
        projectDescription: String,
        organizationId: String,
        location: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<String> = runCatching {
        val docRef = orgProjectsRef(organizationId).document()
        val newId = docRef.id
        val normalizedAddress = ProjectLocationUtils.normalizeAddressText(location)
        val data = mutableMapOf<String, Any?>(
            "projectName" to projectName,
            "name" to projectName,
            "projectDescription" to projectDescription,
            "organizationId" to organizationId,
            "location" to normalizedAddress,
            "addressText" to normalizedAddress,
            "latitude" to latitude,
            "longitude" to longitude,
            "createdAt" to System.currentTimeMillis(),
            "status" to "active",
            "id" to newId,
            "projectId" to newId,
            "projectNumber" to newId
        )

        if (data["googleMapsUrl"] == null) {
            ProjectLocationUtils.buildGoogleMapsUrl(data)?.let { data["googleMapsUrl"] = it }
        }

        docRef.set(data.filterValues { it != null }).await()
        newId
    }

    /** جلب مشاريع مؤسسة */
    suspend fun getProjectsByOrganization(organizationId: String): Result<List<Map<String, Any>>> =
        runCatching {
            val qs = orgProjectsRef(organizationId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get().await()
            qs.documents.map { doc ->
                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                data["id"] = doc.id
                val pn = (data["projectName"] ?: data["name"])?.toString()
                if (!pn.isNullOrBlank()) {
                    data["projectName"] = pn
                    data["name"] = pn
                }
                data
            }
        }

    /** جلب مشروع بمعرّفه ضمن مؤسسة */
    suspend fun getProjectById(organizationId: String, projectId: String): Result<Map<String, Any>> =
        runCatching {
            val doc = orgProjectsRef(organizationId).document(projectId).get().await()
            if (!doc.exists()) error("Project not found")
            val data = doc.data?.toMutableMap() ?: error("Project data is null")
            data["id"] = doc.id
            val pn = (data["projectName"] ?: data["name"])?.toString()
            if (!pn.isNullOrBlank()) {
                data["projectName"] = pn
                data["name"] = pn
            }
            data
        }

    /** (توافق عام) جلب مشروع عبر collectionGroup */
    suspend fun getProjectById(projectId: String): Result<Map<String, Any>> =
        runCatching {
            val snap = firestore.collectionGroup(Constants.COLLECTION_PROJECTS)
                .whereEqualTo(FieldPath.documentId(), projectId)
                .get().await()
            val doc = snap.documents.firstOrNull() ?: error("Project not found")
            val data = doc.data?.toMutableMap() ?: error("Project data is null")
            data["id"] = doc.id
            val pn = (data["projectName"] ?: data["name"])?.toString()
            if (!pn.isNullOrBlank()) {
                data["projectName"] = pn
                data["name"] = pn
            }
            data
        }

    /** تحديث مشروع ضمن مؤسسة */
    suspend fun updateProject(
        organizationId: String,
        projectId: String,
        updates: Map<String, Any>
    ): Result<Unit> = runCatching {
        val normalized = updates.toMutableMap().apply {
            val pn = (this["projectName"] ?: this["name"])?.toString()
            if (!pn.isNullOrBlank()) {
                this["projectName"] = pn
                this["name"] = pn
            }
            if (containsKey("addressText") || containsKey("location")) {
                val normalizedAddress = ProjectLocationUtils.normalizeAddressText(
                    (this["addressText"] ?: this["location"]) as? String
                )
                if (normalizedAddress != null) {
                    this["addressText"] = normalizedAddress
                    this["location"] = normalizedAddress
                } else {
                    this["addressText"] = FieldValue.delete()
                    this["location"] = FieldValue.delete()
                }
            }
            if (containsKey("plusCode")) {
                val normalizedPlusCode = ProjectLocationUtils.normalizePlusCode(this["plusCode"] as? String)
                if (normalizedPlusCode != null) {
                    this["plusCode"] = normalizedPlusCode
                } else {
                    this["plusCode"] = FieldValue.delete()
                }
            }
            if (shouldRecalculateMapsUrl(this)) {
                val newUrl = ProjectLocationUtils.buildGoogleMapsUrl(this)
                this["googleMapsUrl"] = newUrl ?: FieldValue.delete()
            }
        }
        orgProjectsRef(organizationId).document(projectId)
            .update(normalized as Map<String, Any>)
            .await()
    }

    /** (توافق عام) تحديث عبر collectionGroup */
    suspend fun updateProject(projectId: String, updates: Map<String, Any>): Result<Unit> =
        runCatching {
            val snap = firestore.collectionGroup(Constants.COLLECTION_PROJECTS)
                .whereEqualTo(FieldPath.documentId(), projectId)
                .get().await()
            val doc = snap.documents.firstOrNull() ?: error("Project not found")

            val normalized = updates.toMutableMap().apply {
                val pn = (this["projectName"] ?: this["name"])?.toString()
                if (!pn.isNullOrBlank()) {
                    this["projectName"] = pn
                    this["name"] = pn
                }
                if (containsKey("addressText") || containsKey("location")) {
                    val normalizedAddress = ProjectLocationUtils.normalizeAddressText(
                        (this["addressText"] ?: this["location"]) as? String
                    )
                    if (normalizedAddress != null) {
                        this["addressText"] = normalizedAddress
                        this["location"] = normalizedAddress
                    } else {
                        this["addressText"] = FieldValue.delete()
                        this["location"] = FieldValue.delete()
                    }
                }
                if (containsKey("plusCode")) {
                    val normalizedPlusCode = ProjectLocationUtils.normalizePlusCode(this["plusCode"] as? String)
                    if (normalizedPlusCode != null) {
                        this["plusCode"] = normalizedPlusCode
                    } else {
                        this["plusCode"] = FieldValue.delete()
                    }
                }
                if (shouldRecalculateMapsUrl(this)) {
                    val newUrl = ProjectLocationUtils.buildGoogleMapsUrl(this)
                    this["googleMapsUrl"] = newUrl ?: FieldValue.delete()
                }
            }
            doc.reference.update(normalized as Map<String, Any>).await()
        }

    /** حذف مشروع ضمن مؤسسة */
    suspend fun deleteProject(organizationId: String, projectId: String): Result<Unit> =
        runCatching {
            orgProjectsRef(organizationId).document(projectId).delete().await()
        }

    /** (توافق عام) حذف عبر collectionGroup */
    suspend fun deleteProject(projectId: String): Result<Unit> = runCatching {
        val snap = firestore.collectionGroup(Constants.COLLECTION_PROJECTS)
            .whereEqualTo(FieldPath.documentId(), projectId)
            .get().await()
        val doc = snap.documents.firstOrNull() ?: error("Project not found")
        doc.reference.delete().await()
    }

    /** جلب مشاريع مستخدم */
    suspend fun getProjectsForUser(userId: String): Result<List<Map<String, Any>>> =
        runCatching {
            val userDoc = firestore.collection(Constants.COLLECTION_USERS)
                .document(userId).get().await()
            val organizationId = userDoc.getString("organizationId")
                ?: error("User organization not found")
            getProjectsByOrganization(organizationId).getOrThrow()
        }

    // أدوات مساعدة لدمج حقول المشروع داخل وثيقة التقرير
    fun toEmbeddedReportFields(project: Map<String, Any?>): Map<String, Any?> {
        val pn = (project["projectName"] ?: project["name"])?.toString()
        val address = ProjectLocationUtils.normalizeAddressText(
            (project["addressText"] ?: project["location"]) as? String
        )
        return mapOf(
            "projectName" to pn,
            "projectNumber" to project["projectNumber"],
            "location" to address,
            "addressText" to address,
            "latitude" to project["latitude"],
            "longitude" to project["longitude"],
            "plusCode" to project["plusCode"],
            "googleMapsUrl" to project["googleMapsUrl"]
        )
    }

    /** جلب مشروع بالاسم لربط سريع عبر projectName */
    suspend fun getProjectByName(
        organizationId: String,
        projectName: String
    ): Result<Map<String, Any>> = runCatching {
        val snap = orgProjectsRef(organizationId)
            .whereEqualTo("projectName", projectName)
            .limit(1)
            .get().await()
        val doc = snap.documents.firstOrNull() ?: error("Project not found")
        val data = doc.data?.toMutableMap() ?: error("Project data is null")
        data["id"] = doc.id
        val pn = (data["projectName"] ?: data["name"])?.toString()
        if (!pn.isNullOrBlank()) {
            data["projectName"] = pn
            data["name"] = pn
        }
        data
    }
}

private val LOCATION_MUTATION_KEYS = setOf(
    "latitude",
    "longitude"
)

private fun shouldRecalculateMapsUrl(data: Map<String, *>): Boolean =
    data.keys.any { it in LOCATION_MUTATION_KEYS }
