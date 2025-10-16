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

    data class SectionTitle(
        val level: Int,
        @StringRes val titleRes: Int
    ) : ReportItem()

    data class BodyText(val text: String) : ReportItem()

    data class Photo(val uri: Uri) : ReportItem()
}
