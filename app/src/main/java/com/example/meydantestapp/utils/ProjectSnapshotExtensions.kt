package com.example.meydantestapp.utils

import com.example.meydantestapp.Project
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

fun DocumentSnapshot.toProjectSafe(
    precomputedStart: Timestamp? = null,
    precomputedEnd: Timestamp? = null
): Project? {
    val data = data ?: emptyMap<String, Any?>()
    val startRaw = data["startDate"]
    val endRaw = data["endDate"]
    val startTimestamp = precomputedStart ?: FirestoreTimestampConverter.fromAny(startRaw)
    val endTimestamp = precomputedEnd ?: FirestoreTimestampConverter.fromAny(endRaw)

    val project = runCatching { toObject(Project::class.java) }
        .onFailure { error ->
            AppLogger.w("ProjectSnapshot", "toObject(Project) failed for ${id}: ${error.message}")
        }
        .getOrNull() ?: Project()

    project.id = id
    if (project.projectName.isNullOrBlank()) {
        project.projectName = (data["projectName"] as? String)
            ?: (data["name"] as? String)
    }
    if (project.location.isNullOrBlank()) {
        project.location = (data["projectLocation"] as? String)
            ?: (data["addressText"] as? String)
    }
    if (project.addressText.isNullOrBlank()) {
        project.addressText = data["addressText"] as? String
    }
    if (project.plusCode.isNullOrBlank()) {
        project.plusCode = data["plusCode"] as? String
    }
    if (project.googleMapsUrl.isNullOrBlank()) {
        project.googleMapsUrl = data["googleMapsUrl"] as? String
    }
    if (project.workType.isNullOrBlank()) {
        project.workType = data["workType"] as? String
    }
    if (project.latitude == null) {
        project.latitude = data["latitude"].toDoubleOrNull()
    }
    if (project.longitude == null) {
        project.longitude = data["longitude"].toDoubleOrNull()
    }
    if (project.contractValue == null) {
        project.contractValue = data["contractValue"].toDoubleOrNull()
    }

    startTimestamp?.let { project.startDate = it }
    endTimestamp?.let { project.endDate = it }

    migrateTimestampIfNeeded("startDate", startRaw, startTimestamp)
    migrateTimestampIfNeeded("endDate", endRaw, endTimestamp)

    return project
}

private fun Any?.toDoubleOrNull(): Double? = when (this) {
    null -> null
    is Number -> this.toDouble()
    is String -> this.toDoubleOrNull()
    else -> null
}
