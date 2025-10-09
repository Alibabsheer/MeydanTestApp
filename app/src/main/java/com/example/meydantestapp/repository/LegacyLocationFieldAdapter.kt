package com.example.meydantestapp.repository

import com.example.meydantestapp.utils.ProjectLocationUtils

/**
 * Centralized adapter for legacy location fields. New code should consume
 * canonical fields (`addressText`, `googleMapsUrl`) only. This adapter exists
 * solely to read very old documents that might still carry `projectLocation`
 * or `location` fields and convert them into the canonical representation.
 */
internal object LegacyLocationFieldAdapter {

    fun resolveAddress(data: Map<String, Any?>): String? {
        ProjectLocationUtils.normalizeAddressText(data["addressText"] as? String)?.let { return it }
        LEGACY_ADDRESS_KEYS.forEach { key ->
            val candidate = data[key] as? String ?: return@forEach
            ProjectLocationUtils.normalizeAddressText(candidate)?.let { return it }
        }
        return null
    }

    private val LEGACY_ADDRESS_KEYS = listOf("projectLocation", "location")
}
