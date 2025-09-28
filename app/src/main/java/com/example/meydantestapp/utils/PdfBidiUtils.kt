package com.example.meydantestapp.utils

object PdfBidiUtils {
    private val ARABIC = Regex("[\\u0600-\\u06FF]")
    private val LTR_SEGMENT = Regex("([A-Za-z0-9+./:_#?=\\-]+)")
    private const val LRM = '\\u200E'
    private const val RLM = '\\u200F'

    fun isArabicLikely(s: CharSequence): Boolean = ARABIC.containsMatchIn(s)

    @JvmStatic
    fun wrapMixed(text: String, rtlBase: Boolean = true): CharSequence {
        if (text.isEmpty()) return text
        return if (rtlBase) {
            text.replace(LTR_SEGMENT) { matchResult ->
                "$LRM${matchResult.value}$LRM"
            }
        } else {
            text.replace(ARABIC) { matchResult ->
                "$RLM${matchResult.value}$RLM"
            }
        }
    }
}
