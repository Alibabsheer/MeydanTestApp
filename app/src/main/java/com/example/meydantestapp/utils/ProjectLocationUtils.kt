package com.example.meydantestapp.utils

import kotlin.math.isFinite

/**
 * Utilities for handling project location metadata.
 */
object ProjectLocationUtils {

    /**
     * Builds a Google Maps deep-link using latitude & longitude if both are non-null.
     * Returns `null` when one of the coordinates is missing to avoid generating broken URLs.
     */
    fun buildGoogleMapsUrl(latitude: Double?, longitude: Double?): String? {
        val lat = latitude?.takeIf { it.isFinite() }
        val lng = longitude?.takeIf { it.isFinite() }
        return if (lat != null && lng != null) {
            "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
        } else {
            null
        }
    }

    /**
     * Convenience overload that extracts coordinates from a Firestore map/document snapshot.
     */
    fun buildGoogleMapsUrl(data: Map<String, *>): String? {
        val latitude = extractDouble(data["latitude"])
        val longitude = extractDouble(data["longitude"])
        return buildGoogleMapsUrl(latitude, longitude)
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
}
