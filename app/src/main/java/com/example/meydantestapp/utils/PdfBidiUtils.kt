package com.example.meydantestapp.utils

object PdfBidiUtils {

    private val ARABIC_BLOCK = Regex("[\\u0600-\\u06FF]+")
    // LTR tokens: URLs, emails, plus-codes, and generic Latin/digit runs
    private val LTR_TOKENS = Regex("(https?://\\S+|[A-Za-z\\d@#_\\-+/.:]+)")

    private const val LRM: Char = '\u200E' // Left-to-Right Mark
    private const val RLM: Char = '\u200F' // Right-to-Left Mark

    /** Heuristic: does text likely contain Arabic letters? */
    @JvmStatic
    fun isArabicLikely(s: CharSequence): Boolean {
        for (ch in s) if (ch in '\u0600'..'\u06FF') return true
        return false
    }

    /**
     * Wrap mixed-direction text with Unicode marks so it renders in natural order
     * without Android/ICU dependencies.
     * - If rtlBase = true (Arabic paragraph): wrap LTR tokens with LRM.
     * - If rtlBase = false (Latin paragraph): wrap Arabic runs with RLM.
     */
    @JvmStatic
    fun wrapMixed(text: String, rtlBase: Boolean = true): String {
        if (text.isEmpty()) return text
        return if (rtlBase) {
            // Arabic base paragraph: ensure LTR tokens (URLs, numbers, plus-codes) don't flip
            LTR_TOKENS.replace(text) { m ->
                val start = m.range.first
                val end = m.range.last
                val before = if (start > 0) text[start - 1] else null
                val after = if (end + 1 < text.length) text[end + 1] else null
                val left = if (before == LRM) "" else LRM.toString()
                val right = if (after == LRM) "" else LRM.toString()
                left + m.value + right
            }
        } else {
            // Latin base paragraph: ensure Arabic segments keep their order
            ARABIC_BLOCK.replace(text) { m ->
                val start = m.range.first
                val end = m.range.last
                val before = if (start > 0) text[start - 1] else null
                val after = if (end + 1 < text.length) text[end + 1] else null
                val left = if (before == RLM) "" else RLM.toString()
                val right = if (after == RLM) "" else RLM.toString()
                left + m.value + right
            }
        }
    }
}
