package com.example.meydantestapp

import com.example.meydantestapp.utils.PdfBidiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBidiUtilsTest {

    @Test
    fun wrapMixed_arabicTemperature() {
        val input = "درجة الحرارة 45C"
        val output = PdfBidiUtils.wrapMixed(input).toString()
        assertTrue(output.contains("\u200E"))
        assertEquals(input, stripBidiControls(output))
    }

    @Test
    fun wrapMixed_coordinates() {
        val input = "الموقع: 24.7136, 46.6753"
        val output = PdfBidiUtils.wrapMixed(input).toString()
        assertTrue(output.contains("\u200E"))
        assertEquals(input, stripBidiControls(output))
    }

    @Test
    fun wrapMixed_plusCodeAndUrlRemainLtr() {
        val plusCode = "Plus Code: 8GQ8+42"
        val plusWrapped = PdfBidiUtils.wrapMixed(plusCode, rtlBase = PdfBidiUtils.isArabicLikely(plusCode))
        assertEquals(plusCode, plusWrapped.toString())
        assertEquals(plusCode, stripBidiControls(plusWrapped.toString()))

        val url = "https://example.com"
        val urlWrapped = PdfBidiUtils.wrapMixed(url, rtlBase = PdfBidiUtils.isArabicLikely(url))
        assertEquals(url, urlWrapped.toString())
        assertEquals(url, stripBidiControls(urlWrapped.toString()))
    }

    @Test
    fun wrapMixed_ltrBaseWithArabic_insertsRlm() {
        val input = "Meeting at 5 مساءً"
        val output = PdfBidiUtils.wrapMixed(input, rtlBase = false).toString()
        assertTrue(output.contains("\u200F"))
        assertEquals(input, stripBidiControls(output))
    }

    private fun stripBidiControls(text: String): String =
        buildString(text.length) {
            text.forEach { ch ->
                if (Character.getType(ch) != Character.FORMAT.toInt()) {
                    append(ch)
                }
            }
        }
}
