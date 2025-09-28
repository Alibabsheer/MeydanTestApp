package com.example.meydantestapp.utils

import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import java.util.Locale

object PdfBidiUtils {
    private val ARABIC = Regex("[\\u0600-\\u06FF]")
    private val rtlFormatter = BidiFormatter.getInstance(Locale("ar"))
    private val ltrFormatter = BidiFormatter.getInstance(Locale.ENGLISH)

    fun isArabicLikely(s: CharSequence): Boolean = ARABIC.containsMatchIn(s)

    @JvmStatic
    fun wrapMixed(text: String, rtlBase: Boolean = true): CharSequence {
        if (text.isEmpty()) return text

        val formatter = if (rtlBase) rtlFormatter else ltrFormatter
        val heuristic = if (rtlBase) {
            TextDirectionHeuristicsCompat.RTL
        } else {
            TextDirectionHeuristicsCompat.LTR
        }

        return formatter.unicodeWrap(text, heuristic)
    }
}
