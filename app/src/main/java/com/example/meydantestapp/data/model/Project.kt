package com.example.meydantestapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class Project(
    val projectId: String,
    val projectName: String,
    val projectLocation: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val addressText: String? = null,
    val plusCode: String? = null,
    val googleMapsUrl: String? = null,
    val workType: String,
    val contractValue: Double? = null,
    val startDate: String,
    val endDate: String,
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
            return Project(
                projectId = doc.getString("projectId") ?: doc.id,
                projectName = doc.getString("projectName") ?: "",
                projectLocation = doc.getString("projectLocation") ?: "",
                latitude = doc.getDouble("latitude"),
                longitude = doc.getDouble("longitude"),
                addressText = doc.getString("addressText"),
                plusCode = doc.getString("plusCode"),
                googleMapsUrl = doc.getString("googleMapsUrl"),
                workType = doc.getString("workType") ?: "Lump Sum",
                contractValue = (doc.get("contractValue") as? Number)?.toDouble(),
                startDate = doc.getString("startDate") ?: "",
                endDate = doc.getString("endDate") ?: "",
                status = doc.getString("status") ?: "active",
                createdAt = doc.getTimestamp("createdAt"),
                updatedAt = doc.getTimestamp("updatedAt"),
            )
        }
    }
}
