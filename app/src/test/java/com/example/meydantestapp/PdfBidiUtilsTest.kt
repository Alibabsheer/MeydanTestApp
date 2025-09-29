package com.example.meydantestapp

import com.example.meydantestapp.utils.PdfBidiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBidiUtilsTest {

    @Test
    fun wrapMixed_arabicTemperature_insertsLrmAroundNumbers() {
        val input = "درجة الحرارة 45C"
        val output = PdfBidiUtils.wrapMixed(input)

        assertTrue("Expected LRM markers in RTL paragraph", hasLrm(output))
        assertEquals(input, visible(output))
        assertTrue(output.contains("\u200E45C\u200E"))
    }

    @Test
    fun wrapMixed_coordinates_preservesDigitOrder() {
        val input = "الموقع: 24.7136, 46.6753"
        val output = PdfBidiUtils.wrapMixed(input, rtlBase = true)

        // 1) Visible text equals input (no digit reordering)
        assertEquals(input, visible(output))

        // 2) LRM wraps ONLY the numeric tokens (no comma/space)
        assertTrue(output.contains("\u200E24.7136\u200E"))
        assertTrue(output.contains("\u200E46.6753\u200E"))

        // Ensure comma/space are NOT inside the wrapped tokens
        assertFalse(output.contains("\u200E24.7136,"))
        assertFalse(output.contains("46.6753,\u200E"))
    }

    @Test
    fun wrapMixed_plusCodeAndUrlRemainLtrWithoutMarks() {
        val plusCode = "Plus Code: 8GQ8+42"
        val plusWrapped = PdfBidiUtils.wrapMixed(plusCode, rtlBase = PdfBidiUtils.isArabicLikely(plusCode))
        assertFalse(hasBidiMarks(plusWrapped))
        assertEquals(plusCode, plusWrapped)

        val url = "https://example.com"
        val urlWrapped = PdfBidiUtils.wrapMixed(url, rtlBase = PdfBidiUtils.isArabicLikely(url))
        assertFalse(hasBidiMarks(urlWrapped))
        assertEquals(url, urlWrapped)
    }

    @Test
    fun wrapMixed_ltrBaseWithArabic_insertsRlmAroundArabic() {
        val input = "Meeting at 5 مساءً"
        val output = PdfBidiUtils.wrapMixed(input, rtlBase = false)

        assertTrue(hasRlm(output))
        assertEquals(input, visible(output))
        assertTrue(output.contains("\u200Fمساءً\u200F"))
    }

    private fun visible(s: String) = s.replace("\u200E", "").replace("\u200F", "")

    private fun hasBidiMarks(text: String): Boolean =
        text.any { Character.getType(it) == Character.FORMAT.toInt() }

    private fun hasLrm(text: String): Boolean = text.contains('\u200E')

    private fun hasRlm(text: String): Boolean = text.contains('\u200F')
}
