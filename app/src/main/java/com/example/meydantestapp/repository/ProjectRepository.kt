package com.example.meydantestapp.repository

import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.MapLinkUtils
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

    private fun anyToDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }

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

        val latValue = anyToDouble(clean["lat"]) ?: anyToDouble(clean["latitude"])
        val lngValue = anyToDouble(clean["lng"]) ?: anyToDouble(clean["longitude"])
        val plusCodeValue = (clean["plus_code"] ?: clean["plusCode"])?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val addressValue = (clean["address_text"] ?: clean["addressText"])?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val localityValue = (clean["locality_hint"] ?: clean["localityHint"])?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val locationRaw = (clean["projectLocation"] ?: clean["location"])?.toString()
        val formattedLocation = MapLinkUtils.formatDisplayLabel(
            MapLinkUtils.ProjectLocationInfo(
                latitude = latValue,
                longitude = lngValue,
                plusCode = plusCodeValue,
                addressText = addressValue,
                localityHint = localityValue,
                displayLabel = locationRaw
            )
        ) ?: locationRaw?.trim()?.takeIf { it.isNotEmpty() }

        if (formattedLocation != null) {
            clean["location"] = formattedLocation
            clean["projectLocation"] = formattedLocation
        }

        if (latValue != null) {
            clean["lat"] = latValue
            clean["latitude"] = latValue
        }
        if (lngValue != null) {
            clean["lng"] = lngValue
            clean["longitude"] = lngValue
        }

        if (plusCodeValue != null) {
            clean["plus_code"] = plusCodeValue
            clean["plusCode"] = plusCodeValue
        }

        if (addressValue != null) {
            clean["address_text"] = addressValue
            clean["addressText"] = addressValue
        }

        if (localityValue != null) {
            clean["locality_hint"] = localityValue
            clean["localityHint"] = localityValue
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
        longitude: Double? = null,
        plusCode: String? = null,
        addressText: String? = null,
        localityHint: String? = null
    ): Result<String> = runCatching {
        val docRef = orgProjectsRef(organizationId).document()
        val newId = docRef.id
        val sanitizedAddress = addressText?.trim()?.takeIf { it.isNotEmpty() }
        val sanitizedPlusCode = plusCode?.trim()?.takeIf { it.isNotEmpty() }
        val sanitizedLocality = localityHint?.trim()?.takeIf { it.isNotEmpty() }
        val formattedLocation = MapLinkUtils.formatDisplayLabel(
            MapLinkUtils.ProjectLocationInfo(
                latitude = latitude,
                longitude = longitude,
                plusCode = sanitizedPlusCode,
                addressText = sanitizedAddress,
                localityHint = sanitizedLocality,
                displayLabel = location
            )
        ) ?: location?.trim()?.takeIf { it.isNotEmpty() }
        val data = mutableMapOf<String, Any?>(
            "projectName" to projectName,
            "name" to projectName,
            "projectDescription" to projectDescription,
            "organizationId" to organizationId,
            "location" to formattedLocation,
            "projectLocation" to formattedLocation,
            "latitude" to latitude,
            "longitude" to longitude,
            "lat" to latitude,
            "lng" to longitude,
            "plusCode" to sanitizedPlusCode,
            "plus_code" to sanitizedPlusCode,
            "addressText" to sanitizedAddress,
            "address_text" to sanitizedAddress,
            "localityHint" to sanitizedLocality,
            "locality_hint" to sanitizedLocality,
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
        val latValue = anyToDouble(project["lat"]) ?: anyToDouble(project["latitude"])
        val lngValue = anyToDouble(project["lng"]) ?: anyToDouble(project["longitude"])
        val plusCodeValue = (project["plus_code"] ?: project["plusCode"])?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val addressValue = (project["address_text"] ?: project["addressText"])?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val localityValue = (project["locality_hint"] ?: project["localityHint"])?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val locationRaw = (project["projectLocation"] ?: project["location"])?.toString()
        val formattedLocation = MapLinkUtils.formatDisplayLabel(
            MapLinkUtils.ProjectLocationInfo(
                latitude = latValue,
                longitude = lngValue,
                plusCode = plusCodeValue,
                addressText = addressValue,
                localityHint = localityValue,
                displayLabel = locationRaw
            )
        ) ?: locationRaw?.trim()?.takeIf { it.isNotEmpty() }

        return buildMap {
            put("projectName", pn)
            put("projectNumber", project["projectNumber"])
            if (formattedLocation != null) {
                put("location", formattedLocation)
                put("projectLocation", formattedLocation)
            }
            if (latValue != null) {
                put("latitude", latValue)
                put("lat", latValue)
            }
            if (lngValue != null) {
                put("longitude", lngValue)
                put("lng", lngValue)
            }
            if (plusCodeValue != null) {
                put("plus_code", plusCodeValue)
                put("plusCode", plusCodeValue)
            }
            if (addressValue != null) {
                put("address_text", addressValue)
                put("addressText", addressValue)
            }
            if (localityValue != null) {
                put("locality_hint", localityValue)
                put("localityHint", localityValue)
            }
        }
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
