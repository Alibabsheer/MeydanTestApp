package com.example.meydantestapp

import com.example.meydantestapp.utils.PdfBidiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBidiUtilsTest {

    @Test
    fun wrapMixed_arabicTemperature() {
        val input = "درجة الحرارة 45C"
        val wrapped = PdfBidiUtils.wrapMixed(input)
        val output = wrapped.toString()
        assertTrue(containsBidiControl(output))
        assertEquals(input, stripBidiControls(output))
    }

    @Test
    fun wrapMixed_coordinates() {
        val input = "الموقع: 24.7136, 46.6753"
        val wrapped = PdfBidiUtils.wrapMixed(input)
        val output = wrapped.toString()
        assertTrue(containsBidiControl(output))
        assertEquals(input, stripBidiControls(output))
    }

    @Test
    fun wrapMixed_plusCodeAndUrlRemainLtr() {
        val plusCode = "Plus Code: 8GQ8+42"
        val plusWrapped = PdfBidiUtils.wrapMixed(plusCode, rtlBase = PdfBidiUtils.isArabicLikely(plusCode))
        assertEquals(plusCode, stripBidiControls(plusWrapped.toString()))
        assertFalse(containsRtlEmbedding(plusWrapped.toString()))

        val url = "https://example.com"
        val urlWrapped = PdfBidiUtils.wrapMixed(url, rtlBase = PdfBidiUtils.isArabicLikely(url))
        assertEquals(url, stripBidiControls(urlWrapped.toString()))
        assertFalse(containsRtlEmbedding(urlWrapped.toString()))
    }

    private fun stripBidiControls(text: String): String =
        buildString(text.length) {
            text.forEach { ch ->
                if (Character.getType(ch) != Character.FORMAT.toInt()) {
                    append(ch)
                }
            }
        }

    private fun containsBidiControl(text: String): Boolean =
        text.any { Character.getType(it) == Character.FORMAT.toInt() }

    private fun containsRtlEmbedding(text: String): Boolean =
        text.any { it == '\u202B' || it == '\u202E' }
}
