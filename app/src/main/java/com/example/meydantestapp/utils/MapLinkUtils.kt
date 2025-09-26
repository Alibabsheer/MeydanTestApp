package com.example.meydantestapp.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object MapLinkUtils {
    private val LAT_LNG_REGEX = Regex("(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)")
    private val PLUS_CODE_REGEX = Regex("([23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3})(?:\\s+.+)?", RegexOption.IGNORE_CASE)

    fun buildGoogleMapsQuery(locationRaw: String?): String? {
        val trimmed = locationRaw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }

        LAT_LNG_REGEX.find(trimmed)?.let { match ->
            val lat = match.groupValues.getOrNull(1) ?: return@let null
            val lng = match.groupValues.getOrNull(2) ?: return@let null
            return "https://maps.google.com/?q=$lat,$lng"
        }

        val plusCode = PLUS_CODE_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.uppercase(Locale.US)
        if (!plusCode.isNullOrEmpty()) {
            return "https://maps.google.com/?q=$plusCode"
        }

        val sanitizedInput = trimmed.replace("\n", " ").replace("\r", " ").replace("""\s+""".toRegex(), " ")
        val encoded = runCatching {
            URLEncoder.encode(sanitizedInput, StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return null
        return "https://maps.google.com/?q=$encoded"
    }
}
