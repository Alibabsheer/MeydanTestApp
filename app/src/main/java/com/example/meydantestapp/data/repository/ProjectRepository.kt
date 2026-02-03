package com.example.meydantestapp.repository

import com.example.meydantestapp.data.model.Project
import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.FirestoreTimestampConverter
import com.example.meydantestapp.utils.ProjectLocationUtils
import com.example.meydantestapp.utils.DefaultFirestoreProvider
import com.example.meydantestapp.utils.FirestoreProvider
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlin.LazyThreadSafetyMode

/**
 * ProjectRepository
 * - هيكل Firestore المتداخل:
 *   organizations/{organizationId}/projects/{projectId}
 * - يعتمد المخطط القانوني (canonical schema) الذي تُعرّفه فئة Project.
 */
open class ProjectRepository(
    private val firestoreProvider: FirestoreProvider = DefaultFirestoreProvider
) {

    private val firestore: FirebaseFirestore by lazy(LazyThreadSafetyMode.NONE) {
        firestoreProvider.get()
    }

    private companion object {
        const val ORGANIZATIONS = "organizations"
    }

    private fun orgProjectsRef(organizationId: String) =
        firestore.collection(ORGANIZATIONS)
            .document(organizationId)
            .collection(Constants.COLLECTION_PROJECTS)

    /** إنشاء مشروع عبر خريطة بيانات */
    open suspend fun createProject(
        organizationId: String,
        projectData: Map<String, Any?>
    ): Result<String> = runCatching {
        val docRef = orgProjectsRef(organizationId).document()
        val newId = docRef.id

        val project = projectData.buildProjectForCreate(newId)
        val canonical = project.toMap().toMutableMap()

        val extras = projectData.additionalProjectFields().toMutableMap()
        extras.putIfAbsent("projectNumber", newId)

        canonical.putAll(extras)
        canonical.entries.removeIf { it.value == null }

        docRef.set(canonical).await()
        newId
    }

    /** إنشاء مشروع عبر حقول منفصلة */
    open suspend fun createProject(
        projectName: String,
        projectDescription: String,
        organizationId: String,
        location: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<String> = runCatching {
        val baseData = mapOf(
            "projectName" to projectName,
            "addressText" to location,
            "latitude" to latitude,
            "longitude" to longitude,
            "googleMapsUrl" to ProjectLocationUtils.buildGoogleMapsUrl(latitude, longitude),
            "status" to "active",
            "workType" to "",
            "startDate" to null,
            "endDate" to null,
            "projectDescription" to projectDescription,
            "organizationId" to organizationId
        )
        createProject(organizationId, baseData).getOrThrow()
    }

    /** جلب مشاريع مؤسسة */
    open suspend fun getProjectsByOrganization(organizationId: String): Result<List<Project>> =
        runCatching {
            val qs = orgProjectsRef(organizationId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get().await()
            qs.documents.map { Project.from(it) }
        }

    /** جلب مشروع بمعرّفه ضمن مؤسسة */
    open suspend fun getProjectById(organizationId: String, projectId: String): Result<Project> =
        runCatching {
            val doc = orgProjectsRef(organizationId).document(projectId).get().await()
            if (!doc.exists()) error("Project not found")
            Project.from(doc)
        }

    /** (توافق عام) جلب مشروع عبر collectionGroup */
    open suspend fun getProjectById(projectId: String): Result<Project> =
        runCatching {
            val snap = firestore.collectionGroup(Constants.COLLECTION_PROJECTS)
                .whereEqualTo(FieldPath.documentId(), projectId)
                .get().await()
            val doc = snap.documents.firstOrNull() ?: error("Project not found")
            Project.from(doc)
        }

    /** تحديث مشروع ضمن مؤسسة */
    open suspend fun updateProject(
        organizationId: String,
        projectId: String,
        updates: Map<String, Any?>
    ): Result<Unit> = runCatching {
        val docRef = orgProjectsRef(organizationId).document(projectId)
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) error("Project not found")

        val currentProject = Project.from(snapshot)
        val existingExtras = snapshot.additionalProjectFields().toMutableMap()

        val mergedProject = currentProject
            .mergeWithUpdates(updates)
            .copy(projectId = currentProject.projectId.ifBlank { projectId }, updatedAt = Timestamp.now())

        val finalData = mergedProject.toMap().toMutableMap()
        applyAdditionalProjectUpdates(existingExtras, updates)
        existingExtras.putIfAbsent("projectNumber", projectId)

        finalData.putAll(existingExtras)
        finalData.entries.removeIf { it.value == null }

        docRef.set(finalData).await()
    }

    /** (توافق عام) تحديث عبر collectionGroup */
    open suspend fun updateProject(projectId: String, updates: Map<String, Any?>): Result<Unit> =
        runCatching {
            val snap = firestore.collectionGroup(Constants.COLLECTION_PROJECTS)
                .whereEqualTo(FieldPath.documentId(), projectId)
                .get().await()
            val doc = snap.documents.firstOrNull() ?: error("Project not found")

            val currentProject = Project.from(doc)
            val existingExtras = doc.additionalProjectFields().toMutableMap()

            val mergedProject = currentProject
                .mergeWithUpdates(updates)
                .copy(projectId = currentProject.projectId.ifBlank { projectId }, updatedAt = Timestamp.now())

            val finalData = mergedProject.toMap().toMutableMap()
            applyAdditionalProjectUpdates(existingExtras, updates)
            existingExtras.putIfAbsent("projectNumber", projectId)

            finalData.putAll(existingExtras)
            finalData.entries.removeIf { it.value == null }

            doc.reference.set(finalData).await()
        }

    /** حذف مشروع ضمن مؤسسة */
    open suspend fun deleteProject(organizationId: String, projectId: String): Result<Unit> =
        runCatching {
            orgProjectsRef(organizationId).document(projectId).delete().await()
        }

    /** (توافق عام) حذف عبر collectionGroup */
    open suspend fun deleteProject(projectId: String): Result<Unit> = runCatching {
        val snap = firestore.collectionGroup(Constants.COLLECTION_PROJECTS)
            .whereEqualTo(FieldPath.documentId(), projectId)
            .get().await()
        val doc = snap.documents.firstOrNull() ?: error("Project not found")
        doc.reference.delete().await()
    }

    /** جلب مشاريع مستخدم */
    open suspend fun getProjectsForUser(userId: String): Result<List<Project>> =
        runCatching {
            val userDoc = firestore.collection(Constants.COLLECTION_USERS)
                .document(userId).get().await()
            val organizationId = userDoc.getString("organizationId")
                ?: error("User organization not found")
            getProjectsByOrganization(organizationId).getOrThrow()
        }

    // أدوات مساعدة لدمج حقول المشروع داخل وثيقة التقرير
    fun toEmbeddedReportFields(project: Map<String, Any?>): Map<String, Any?> {
        val normalizedAddress = normalizeAddress(project["addressText"])
            ?: normalizeAddress(project["projectLocation"])
            ?: normalizeAddress(project["location"])
        return mapOf(
            "projectName" to (project["projectName"] as? String)?.takeIf { it.isNotBlank() },
            "projectNumber" to project["projectNumber"],
            "addressText" to normalizedAddress,
            "latitude" to project["latitude"],
            "longitude" to project["longitude"],
            "plusCode" to project["plusCode"],
            "googleMapsUrl" to project["googleMapsUrl"]
        ).filterValues { it != null }
    }
}

private val LEGACY_PROJECT_KEYS = setOf(
    "name",
    "location",
    "projectLocation"
)

private val CANONICAL_PROJECT_KEYS = setOf(
    "projectId",
    "projectName",
    "latitude",
    "longitude",
    "addressText",
    "plusCode",
    "googleMapsUrl",
    "workType",
    "contractValue",
    "startDate",
    "endDate",
    "status",
    "createdAt",
    "updatedAt"
)

private fun Map<String, Any?>.buildProjectForCreate(projectId: String): Project {
    val projectName = (this["projectName"] as? String)?.trim().orEmpty()
    val normalizedAddress = normalizeAddress(this["addressText"])
        ?: normalizeAddress(this["projectLocation"])
        ?: normalizeAddress(this["location"])
    val latitude = ProjectLocationUtils.normalizeLatitude(this["latitude"])
    val longitude = ProjectLocationUtils.normalizeLongitude(this["longitude"])
    val plusCode = ProjectLocationUtils.normalizePlusCode(this["plusCode"] as? String)
    val googleMapsUrl = (this["googleMapsUrl"] as? String)?.takeIf { it.isNotBlank() }
        ?: ProjectLocationUtils.buildGoogleMapsUrl(latitude, longitude)
    val workType = (this["workType"] as? String)?.takeIf { it.isNotBlank() } ?: "Lump Sum"
    val contractValue = this["contractValue"].toDoubleOrNull()
    val startTimestamp = FirestoreTimestampConverter.fromAny(this["startDate"])
    val endTimestamp = FirestoreTimestampConverter.fromAny(this["endDate"])
    val status = (this["status"] as? String)?.takeIf { it.isNotBlank() } ?: "active"

    return Project(
        projectId = projectId,
        projectName = projectName,
        latitude = latitude,
        longitude = longitude,
        addressText = normalizedAddress,
        plusCode = plusCode,
        googleMapsUrl = googleMapsUrl,
        workType = workType,
        contractValue = contractValue,
        startDate = startTimestamp,
        endDate = endTimestamp,
        status = status,
        createdAt = Timestamp.now(),
        updatedAt = Timestamp.now()
    )
}

private fun Project.mergeWithUpdates(updates: Map<String, Any?>): Project {
    val updatedProjectName = (updates["projectName"] as? String)?.trim()
        ?.takeIf { it.isNotEmpty() } ?: projectName
    val hasAddressUpdate = updates.containsKey("addressText")
        || updates.containsKey("projectLocation")
        || updates.containsKey("location")
    val updatedAddress = normalizeAddress(updates["addressText"])
        ?: normalizeAddress(updates["projectLocation"])
        ?: normalizeAddress(updates["location"])
    val resolvedAddressText = when {
        hasAddressUpdate -> updatedAddress
        else -> addressText
    }
    val hasLatitudeUpdate = updates.containsKey("latitude")
    val resolvedLatitude = if (hasLatitudeUpdate) {
        ProjectLocationUtils.normalizeLatitude(updates["latitude"])
    } else {
        latitude
    }
    val hasLongitudeUpdate = updates.containsKey("longitude")
    val resolvedLongitude = if (hasLongitudeUpdate) {
        ProjectLocationUtils.normalizeLongitude(updates["longitude"])
    } else {
        longitude
    }
    val hasPlusCodeUpdate = updates.containsKey("plusCode")
    val resolvedPlusCode = if (hasPlusCodeUpdate) {
        ProjectLocationUtils.normalizePlusCode(updates["plusCode"] as? String)
    } else {
        plusCode
    }
    val hasWorkTypeUpdate = updates.containsKey("workType")
    val resolvedWorkType = if (hasWorkTypeUpdate) {
        (updates["workType"] as? String)?.takeIf { it.isNotBlank() } ?: workType
    } else {
        workType
    }
    val hasContractValueUpdate = updates.containsKey("contractValue")
    val resolvedContractValue = if (hasContractValueUpdate) {
        updates["contractValue"].toDoubleOrNull()
    } else {
        contractValue
    }
    val hasStartDateUpdate = updates.containsKey("startDate")
    val resolvedStartDate = if (hasStartDateUpdate) {
        FirestoreTimestampConverter.fromAny(updates["startDate"]) ?: startDate
    } else {
        startDate
    }
    val hasEndDateUpdate = updates.containsKey("endDate")
    val resolvedEndDate = if (hasEndDateUpdate) {
        FirestoreTimestampConverter.fromAny(updates["endDate"]) ?: endDate
    } else {
        endDate
    }
    val hasStatusUpdate = updates.containsKey("status")
    val resolvedStatus = if (hasStatusUpdate) {
        (updates["status"] as? String)?.takeIf { it.isNotBlank() } ?: status
    } else {
        status
    }
    val hasMapsUpdate = updates.containsKey("googleMapsUrl")
    val resolvedMapsUrl = when {
        hasMapsUpdate -> (updates["googleMapsUrl"] as? String)?.takeIf { it.isNotBlank() }
        else -> ProjectLocationUtils.buildGoogleMapsUrl(resolvedLatitude, resolvedLongitude)
    }
    val hasCreatedAtUpdate = updates.containsKey("createdAt")
    val resolvedCreatedAt = if (hasCreatedAtUpdate) {
        updates["createdAt"] as? Timestamp ?: createdAt
    } else {
        createdAt
    }

    return copy(
        projectName = updatedProjectName,
        latitude = resolvedLatitude,
        longitude = resolvedLongitude,
        addressText = resolvedAddressText,
        plusCode = resolvedPlusCode,
        googleMapsUrl = resolvedMapsUrl,
        workType = resolvedWorkType,
        contractValue = resolvedContractValue,
        startDate = resolvedStartDate,
        endDate = resolvedEndDate,
        status = resolvedStatus,
        createdAt = resolvedCreatedAt
    )
}

private fun Map<String, Any?>.additionalProjectFields(): Map<String, Any?> =
    filterKeys { it !in CANONICAL_PROJECT_KEYS && it !in LEGACY_PROJECT_KEYS }

private fun applyAdditionalProjectUpdates(
    extras: MutableMap<String, Any?>,
    updates: Map<String, Any?>
) {
    updates.additionalProjectFields().forEach { (key, value) ->
        if (value == null) {
            extras.remove(key)
        } else {
            extras[key] = value
        }
    }
}

private fun normalizeAddress(value: Any?): String? =
    ProjectLocationUtils.normalizeAddressText((value as? String)?.takeIf { it.isNotBlank() })

private fun Any?.toDoubleOrNull(): Double? = when (this) {
    is Number -> this.toDouble()
    is String -> this.toDoubleOrNull()
    else -> null
}

private fun com.google.firebase.firestore.DocumentSnapshot.additionalProjectFields(): Map<String, Any?> =
    (data ?: emptyMap<String, Any?>()).additionalProjectFields()

