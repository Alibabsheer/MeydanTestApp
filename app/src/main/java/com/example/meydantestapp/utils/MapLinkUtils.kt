package com.example.meydantestapp.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object MapLinkUtils {
    data class ProjectLocationInfo(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val plusCode: String? = null,
        val addressText: String? = null,
        val displayLabel: String? = null,
        val localityHint: String? = null
    )

    private const val MAPS_SEARCH_BASE = "https://www.google.com/maps/search/?api=1&query="
    private val LAT_LNG_REGEX = Regex("(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)")
    private val PLUS_CODE_REGEX = Regex("([23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3})", RegexOption.IGNORE_CASE)
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun formatDisplayLabel(info: ProjectLocationInfo): String? {
        val address = info.addressText?.let { sanitizeDisplay(it) }?.takeIf { it.isNotEmpty() }
        val plusCode = info.plusCode?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }
        val label = info.displayLabel?.let { sanitizeDisplay(it) }?.takeIf { it.isNotEmpty() }

        val addressContainsPlus = if (address != null && plusCode != null) {
            address.contains(plusCode, ignoreCase = true)
        } else {
            false
        }

        return when {
            address != null && plusCode != null && !addressContainsPlus -> "$address، $plusCode"
            address != null -> address
            plusCode != null -> {
                val locality = extractLocality(info)
                if (!locality.isNullOrEmpty()) "$plusCode ($locality)" else plusCode
            }
            else -> label
        }?.takeIf { it.isNotEmpty() }
    }

    fun buildGoogleMapsLink(info: ProjectLocationInfo): String? {
        val lat = info.latitude
        val lng = info.longitude
        if (lat != null && lng != null) {
            val latStr = formatCoordinate(lat)
            val lngStr = formatCoordinate(lng)
            val encoded = encodeQueryValue("$latStr,$lngStr")
            return MAPS_SEARCH_BASE + encoded
        }

        val plusCode = info.plusCode?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() }
        if (plusCode != null) {
            val query = buildPlusCodeQuery(plusCode, info)
            val encoded = encodeQueryValue(query)
            return MAPS_SEARCH_BASE + encoded
        }

        val address = info.addressText?.trim()?.takeIf { it.isNotEmpty() }
            ?: info.displayLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        if (address.startsWith("http://", ignoreCase = true) || address.startsWith("https://", ignoreCase = true)) {
            return address
        }
        val sanitized = sanitizeDisplay(address)
        if (sanitized.isEmpty()) return null
        return MAPS_SEARCH_BASE + encodeQueryValue(sanitized)
    }

    fun buildGoogleMapsQuery(locationRaw: String?): String? {
        val trimmed = locationRaw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }

        LAT_LNG_REGEX.find(trimmed)?.let { match ->
            val lat = match.groupValues.getOrNull(1)?.toDoubleOrNull()
            val lng = match.groupValues.getOrNull(2)?.toDoubleOrNull()
            if (lat != null && lng != null) {
                return buildGoogleMapsLink(ProjectLocationInfo(latitude = lat, longitude = lng))
            }
        }

        PLUS_CODE_REGEX.find(trimmed)?.let { match ->
            val plusCode = match.groupValues.getOrNull(1)?.uppercase(Locale.US)
            val remaining = trimmed.replace(match.value, "").trim().takeIf { it.isNotEmpty() }
            return buildGoogleMapsLink(
                ProjectLocationInfo(
                    plusCode = plusCode,
                    addressText = remaining,
                    displayLabel = trimmed
                )
            )
        }

        return buildGoogleMapsLink(ProjectLocationInfo(displayLabel = trimmed, addressText = trimmed))
    }

    private fun sanitizeDisplay(input: String): String =
        WHITESPACE_REGEX.replace(input.replace('\n', ' ').replace('\r', ' '), " ").trim()

    private fun encodeQueryValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun formatCoordinate(value: Double): String {
        val raw = String.format(Locale.US, "%.7f", value)
        return raw.trimEnd('0').trimEnd('.')
    }

    private fun extractLocality(info: ProjectLocationInfo): String? {
        info.localityHint?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val parts = mutableListOf<String>()

        fun collect(source: String?) {
            if (source.isNullOrBlank()) return
            val normalized = sanitizeDisplay(source)
            if (normalized.isEmpty()) return
            normalized.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
                parts.add(part)
            }
        }

        collect(info.addressText)
        collect(info.displayLabel)

        if (parts.isEmpty()) return null

        val filtered = parts.filterNot { candidate ->
            PLUS_CODE_REGEX.matches(candidate.replace(" ", ""))
        }
        val target = if (filtered.isNotEmpty()) filtered else parts
        val takeCount = minOf(2, target.size)
        return target.takeLast(takeCount).joinToString(" ").takeIf { it.isNotEmpty() }
    }

    private fun buildPlusCodeQuery(plusCode: String, info: ProjectLocationInfo): String {
        val locality = extractLocality(info)
        return if (locality.isNullOrEmpty()) plusCode else "$plusCode $locality"
    }
}
