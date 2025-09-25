package com.example.meydantestapp.repository

import java.util.Locale

/**
 * توصيف أهداف رفع الصور الخاصة بالتقارير اليومية.
 */
enum class DailyReportUploadSpec(
    val pathSegment: String,
    val contentType: String,
    private val namePattern: String
) {
    LEGACY_PHOTO("photos", "image/jpeg", "photo_%03d.jpg"),
    GRID_PAGE("pages", "image/webp", "page_%03d.webp");

    fun fileName(index: Int): String = String.format(Locale.US, namePattern, index + 1)
}
