package com.example.meydantestapp.utils

import android.util.Log
import com.example.meydantestapp.Project
import com.google.firebase.firestore.DocumentSnapshot

fun DocumentSnapshot.toProjectSafe(
    precomputedStart: FirestoreTimestampConverter.ConversionResult? = null,
    precomputedEnd: FirestoreTimestampConverter.ConversionResult? = null
): Project? {
    val data = data ?: emptyMap<String, Any?>()
    val startConversion = precomputedStart ?: FirestoreTimestampConverter.fromAny(data["startDate"])
    val endConversion = precomputedEnd ?: FirestoreTimestampConverter.fromAny(data["endDate"])

    val project = runCatching { toObject(Project::class.java) }
        .onFailure { error ->
            Log.w("ProjectSnapshot", "toObject(Project) failed for ${id}: ${error.message}")
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

    startConversion.resolvedTimestamp?.let { project.startDate = it }
    endConversion.resolvedTimestamp?.let { project.endDate = it }

    migrateTimestampIfNeeded("startDate", startConversion)
    migrateTimestampIfNeeded("endDate", endConversion)

    return project
}

private fun Any?.toDoubleOrNull(): Double? = when (this) {
    null -> null
    is Number -> this.toDouble()
    is String -> this.toDoubleOrNull()
    else -> null
}
