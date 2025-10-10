package com.example.meydantestapp.utils

/**
 * Utilities for handling project location metadata.
 */
import java.net.URLEncoder

object ProjectLocationUtils {

    /**
     * Builds a Google Maps deep-link using latitude & longitude if both are non-null.
     * Returns `null` when one of the coordinates is missing to avoid generating broken URLs.
     */
    fun buildGoogleMapsUrl(latitude: Double?, longitude: Double?): String? =
        maybeBuildMapsUrl(latitude, longitude)

    /**
     * Convenience overload that extracts coordinates from a Firestore map/document snapshot.
     */
    fun buildGoogleMapsUrl(data: Map<String, *>): String? {
        val latitude = extractDouble(data["latitude"])
        val longitude = extractDouble(data["longitude"])
        return buildGoogleMapsUrl(latitude, longitude)
    }

    fun buildGoogleMapsSearchUrl(addressText: String): String {
        val normalized = normalizeAddressText(addressText)
            ?: error("addressText must be non-blank")
        val encoded = URLEncoder.encode(normalized, Charsets.UTF_8.name())
        return "https://www.google.com/maps/search/?api=1&query=$encoded"
    }

    fun normalizeAddressText(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    fun normalizePlusCode(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    fun normalizeLatitude(value: Any?): Double? = extractDouble(value)

    fun normalizeLongitude(value: Any?): Double? = extractDouble(value)

    private fun extractDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }?.takeUnless { it.isNaN() || it.isInfinite() }

    private fun maybeBuildMapsUrl(latitude: Double?, longitude: Double?): String? {
        val lat = if (latitude.isFiniteSafe()) latitude else null
        val lng = if (longitude.isFiniteSafe()) longitude else null
        return if (lat != null && lng != null) {
            "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
        } else {
            null
        }
    }

    private fun Double?.isFiniteSafe(): Boolean = this != null && !this.isNaN() && !this.isInfinite()
}
