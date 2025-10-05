package com.example.meydantestapp.utils

import com.example.meydantestapp.utils.ProjectDateUtils.toUtcLocalDate
import com.google.firebase.Timestamp
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

fun Timestamp?.toDisplayDateString(): String {
    val ts = this ?: return ""
    val locale = Locale.getDefault()
    val formatter = DateTimeFormatter
        .ofPattern(Constants.DATE_FORMAT_DISPLAY, locale)
        .withDecimalStyle(DecimalStyle.of(locale))
    return formatter.format(ts.toUtcLocalDate())
}
