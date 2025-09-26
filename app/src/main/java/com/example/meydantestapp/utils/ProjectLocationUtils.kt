package com.example.meydantestapp.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Utilities for handling project location metadata.
 */
object ProjectLocationUtils {

    private val addressKeys = listOf("location", "address", "projectLocation")
    private val plusCodeKeys = listOf("plusCode", "plus_code", "pluscode")

    fun buildGoogleMapsUrl(
        latitude: Double?,
        longitude: Double?,
        plusCode: String?,
        address: String?
    ): String? {
        val lat = latitude
        val lng = longitude
        if (lat != null && lng != null) {
            return "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
        }

        val normalizedPlusCode = plusCode?.trim()?.takeIf { it.isNotEmpty() }
        if (!normalizedPlusCode.isNullOrEmpty()) {
            val encoded = URLEncoder.encode(normalizedPlusCode, StandardCharsets.UTF_8.toString())
            return "https://www.google.com/maps/search/?api=1&query=$encoded"
        }

        val normalizedAddress = address?.trim()?.takeIf { it.isNotEmpty() }
        if (!normalizedAddress.isNullOrEmpty()) {
            val encoded = URLEncoder.encode(normalizedAddress, StandardCharsets.UTF_8.toString())
            return "https://www.google.com/maps/search/?api=1&query=$encoded"
        }

        return null
    }

    fun buildGoogleMapsUrl(data: Map<String, *>): String? {
        val latitude = extractDouble(data["latitude"])
        val longitude = extractDouble(data["longitude"])
        val plusCode = plusCodeKeys.asSequence()
            .mapNotNull { (data[it] as? String)?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .firstOrNull()
        val address = addressKeys.asSequence()
            .mapNotNull { (data[it] as? String)?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .firstOrNull()

        return buildGoogleMapsUrl(latitude, longitude, plusCode, address)
    }

    private fun extractDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}
