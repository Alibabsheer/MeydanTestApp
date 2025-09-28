package com.example.meydantestapp.utils

import androidx.core.text.BidiFormatter
import androidx.core.text.TextDirectionHeuristicsCompat
import java.util.Locale

object PdfBidiUtils {
    private val arabicLocale = Locale("ar")

    fun wrapMixed(text: String, rtlBase: Boolean = true): CharSequence {
        if (text.isEmpty()) return text
        val locale = if (rtlBase) arabicLocale else Locale.ENGLISH
        val heuristic = if (rtlBase) TextDirectionHeuristicsCompat.RTL else TextDirectionHeuristicsCompat.LTR
        val formatter = BidiFormatter.getInstance(locale)
        return formatter.unicodeWrap(text, heuristic)
    }

    fun isArabicLikely(s: CharSequence): Boolean {
        var rtl = 0
        var ltr = 0
        for (index in 0 until s.length) {
            val ch = s[index]
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> rtl++

                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                Character.DIRECTIONALITY_EUROPEAN_NUMBER,
                Character.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR,
                Character.DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR -> ltr++

                Character.DIRECTIONALITY_ARABIC_NUMBER -> {
                    if (rtl > 0) rtl++ else ltr++
                }
            }
        }
        if (rtl == 0) return false
        if (ltr == 0) return true
        return rtl >= ltr
    }
}
