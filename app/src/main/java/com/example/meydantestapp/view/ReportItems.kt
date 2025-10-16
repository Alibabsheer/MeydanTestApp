package com.example.meydantestapp.view

import android.net.Uri
import androidx.annotation.StringRes

sealed class ReportItem {
    data object HeaderLogo : ReportItem()

    data class InfoRow(
        @StringRes val labelRes: Int,
        val value: String,
        val linkUrl: String? = null
    ) : ReportItem()

    data class SectionTitle(val level: Int, @StringRes val titleRes: Int) : ReportItem()

    data class BodyText(val text: String) : ReportItem()

    data class Photo(val uri: Uri) : ReportItem()

    data class Workforce(val entries: List<String>) : ReportItem() {
        companion object {
            private const val SEPARATOR = "|"

            const val KEY_SKILLED: String = "skilled"
            const val KEY_UNSKILLED: String = "unskilled"
            const val KEY_TOTAL: String = "total"

            fun buildEntry(key: String, value: String): String = "$key$SEPARATOR$value"

            fun parseEntry(entry: String): Pair<String, String>? {
                val index = entry.indexOf(SEPARATOR)
                if (index <= 0 || index == entry.lastIndex) {
                    return null
                }
                val key = entry.substring(0, index)
                val value = entry.substring(index + 1)
                return key to value
            }
        }
    }

    data class SitePage(val uri: Uri, val caption: String? = null) : ReportItem()
}
