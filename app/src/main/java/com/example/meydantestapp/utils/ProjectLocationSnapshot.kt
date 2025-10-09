package com.example.meydantestapp.utils

/**
 * Canonical snapshot for project location information used across project + daily report flows.
 */
data class ProjectLocationSnapshot(
    val addressText: String?,
    val googleMapsUrl: String?
) {
    val hasLink: Boolean
        get() = !googleMapsUrl.isNullOrBlank()
}

object ProjectLocationSnapshotFactory {

    fun fromProjectData(data: Map<String, Any?>): ProjectLocationSnapshot {
        val address = resolveAddress(
            data["addressText"],
            data["projectLocation"],
            data["location"]
        )
        val url = ProjectLocationUtils.normalizeGoogleMapsUrl(data["googleMapsUrl"] as? String)
        return ProjectLocationSnapshot(address, url)
    }

    fun fromProjectDataForReport(data: Map<String, Any?>): ProjectLocationSnapshot {
        val base = fromProjectData(data)
        val latitude = ProjectLocationUtils.normalizeLatitude(data["latitude"])
        val longitude = ProjectLocationUtils.normalizeLongitude(data["longitude"])
        return snapshotForReport(base.addressText, base.googleMapsUrl, latitude, longitude)
    }

    fun fromDailyReportData(data: Map<String, Any?>): ProjectLocationSnapshot {
        val address = resolveAddress(
            data["addressText"],
            data["projectLocation"],
            data["location"]
        )
        val url = ProjectLocationUtils.normalizeGoogleMapsUrl(data["googleMapsUrl"] as? String)
        return ProjectLocationSnapshot(address, url)
    }

    fun snapshotForReport(
        addressText: String?,
        googleMapsUrl: String?,
        latitude: Double?,
        longitude: Double?
    ): ProjectLocationSnapshot {
        val normalizedAddress = ProjectLocationUtils.normalizeAddressText(addressText)
        val normalizedUrl = ProjectLocationUtils.normalizeGoogleMapsUrl(googleMapsUrl)
        val resolvedUrl = normalizedUrl
            ?: ProjectLocationUtils.buildGoogleMapsUrl(latitude, longitude)
            ?: ProjectLocationUtils.buildGoogleMapsSearchUrl(normalizedAddress)
        return ProjectLocationSnapshot(normalizedAddress, resolvedUrl)
    }

    private fun resolveAddress(vararg candidates: Any?): String? {
        candidates.forEach { candidate ->
            val normalized = ProjectLocationUtils.normalizeAddressText(candidate as? String)
            if (normalized != null) return normalized
        }
        return null
    }
}
