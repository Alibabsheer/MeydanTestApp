package com.example.meydantestapp.utils

import com.example.meydantestapp.Project

/**
 * Represents the immutable snapshot of a project's location that is embedded inside
 * daily reports. It always contains the human readable address plus a Maps deep-link
 * when available.
 */
data class ProjectLocationSnapshot(
    val addressText: String?,
    val googleMapsUrl: String?
)

/**
 * Centralised resolver for project location data.
 *
 * All legacy aliases are handled internally so that callers only deal with the
 * canonical [addressText] + [googleMapsUrl] pair.
 */
object ProjectLocationSnapshotResolver {

    private val LEGACY_REPORT_ADDRESS_KEYS = listOf("projectLocation", "location")

    fun fromProjectData(
        projectData: Map<String, Any?>,
        fallbackProject: Project?
    ): ProjectLocationSnapshot {
        val normalizedAddress = sequenceOf(
            ProjectLocationUtils.normalizeAddressText(projectData["addressText"] as? String),
            ProjectLocationUtils.normalizeAddressText(fallbackProject?.addressText)
        ).mapNotNull { it }
            .firstOrNull()

        val latitude = ProjectLocationUtils.normalizeLatitude(projectData["latitude"]) ?: fallbackProject?.latitude
        val longitude = ProjectLocationUtils.normalizeLongitude(projectData["longitude"]) ?: fallbackProject?.longitude

        val explicitUrl = sequenceOf(
            (projectData["googleMapsUrl"] as? String)?.trim(),
            fallbackProject?.googleMapsUrl?.trim()
        ).mapNotNull { candidate -> candidate?.takeIf { it.isNotEmpty() } }
            .firstOrNull()

        val resolvedUrl = explicitUrl
            ?: ProjectLocationUtils.buildGoogleMapsUrl(latitude, longitude)
            ?: normalizedAddress?.let { ProjectLocationUtils.buildGoogleMapsSearchUrl(it) }

        return ProjectLocationSnapshot(normalizedAddress, resolvedUrl)
    }

    fun fromReportData(reportData: Map<String, Any?>): ProjectLocationSnapshot {
        val normalizedAddress = ProjectLocationUtils.normalizeAddressText(reportData["addressText"] as? String)
            ?: LEGACY_REPORT_ADDRESS_KEYS.asSequence()
                .mapNotNull { key -> ProjectLocationUtils.normalizeAddressText(reportData[key] as? String) }
                .firstOrNull()

        val resolvedUrl = (reportData["googleMapsUrl"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        return ProjectLocationSnapshot(normalizedAddress, resolvedUrl)
    }
}
