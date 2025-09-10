package com.example.meydantestapp.repository

import com.example.meydantestapp.utils.Constants
import com.google.firebase.firestore.FieldPath
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

        val clean = projectData.filterValues { it != null }.toMutableMap()
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
        val data = mutableMapOf<String, Any?>(
            "projectName" to projectName,
            "name" to projectName,
            "projectDescription" to projectDescription,
            "organizationId" to organizationId,
            "location" to location,
            "latitude" to latitude,
            "longitude" to longitude,
            "createdAt" to System.currentTimeMillis(),
            "status" to "active",
            "id" to newId,
            "projectId" to newId,
            "projectNumber" to newId
        ).filterValues { it != null }
        docRef.set(data).await()
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
        return mapOf(
            "projectName" to pn,
            "projectNumber" to project["projectNumber"],
            "location" to project["location"],
            "latitude" to project["latitude"],
            "longitude" to project["longitude"]
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
