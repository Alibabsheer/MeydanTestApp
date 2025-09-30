package com.example.meydantestapp.utils

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

fun Timestamp?.toDisplayDateString(): String {
    val ts = this ?: return ""
    val formatter = SimpleDateFormat(Constants.DATE_FORMAT_DISPLAY, Locale.getDefault()).apply {
        isLenient = false
    }
    return formatter.format(ts.toDate())
}
