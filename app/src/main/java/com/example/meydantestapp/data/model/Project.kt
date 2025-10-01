package com.example.meydantestapp.data.model

import com.example.meydantestapp.utils.AppLogger
import com.example.meydantestapp.utils.FirestoreTimestampConverter
import com.example.meydantestapp.utils.migrateTimestampIfNeeded
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Project(
    val projectId: String,
    val projectName: String,
    val projectLocation: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val addressText: String? = null,
    val plusCode: String? = null,
    val googleMapsUrl: String? = null,
    val workType: String,
    val contractValue: Double? = null,
    val startDate: Timestamp? = null,
    val endDate: Timestamp? = null,
    val status: String = "active",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "projectId" to projectId,
        "projectName" to projectName,
        "projectLocation" to projectLocation,
        "latitude" to latitude,
        "longitude" to longitude,
        "addressText" to addressText,
        "plusCode" to plusCode,
        "googleMapsUrl" to googleMapsUrl,
        "workType" to workType,
        "contractValue" to contractValue,
        "startDate" to startDate,
        "endDate" to endDate,
        "status" to status,
        "createdAt" to (createdAt ?: Timestamp.now()),
        "updatedAt" to (updatedAt ?: Timestamp.now())
    )

    companion object {
        fun from(doc: DocumentSnapshot): Project {
            val data = doc.data ?: emptyMap<String, Any?>()
            val startRaw = data["startDate"]
            val endRaw = data["endDate"]
            val startTs = FirestoreTimestampConverter.fromAny(startRaw)
            val endTs = FirestoreTimestampConverter.fromAny(endRaw)

            doc.migrateTimestampIfNeeded("startDate", startRaw, startTs)
            doc.migrateTimestampIfNeeded("endDate", endRaw, endTs)

            AppLogger.d(
                "ProjectModel",
                "from(doc=${doc.id}) start=${startTs?.seconds} end=${endTs?.seconds}"
            )

            return Project(
                projectId = doc.getString("projectId") ?: doc.id,
                projectName = doc.getString("projectName")
                    ?: data["name"] as? String
                    ?: "",
                projectLocation = doc.getString("projectLocation")
                    ?: data["location"] as? String,
                latitude = doc.getDouble("latitude"),
                longitude = doc.getDouble("longitude"),
                addressText = doc.getString("addressText") ?: data["location"] as? String,
                plusCode = doc.getString("plusCode"),
                googleMapsUrl = doc.getString("googleMapsUrl"),
                workType = doc.getString("workType") ?: "Lump Sum",
                contractValue = when (val raw = data["contractValue"]) {
                    is Number -> raw.toDouble()
                    is String -> raw.toDoubleOrNull()
                    else -> null
                },
                startDate = startTs,
                endDate = endTs,
                status = doc.getString("status") ?: "active",
                createdAt = doc.getTimestamp("createdAt"),
                updatedAt = doc.getTimestamp("updatedAt"),
            )
        }
    }
}
