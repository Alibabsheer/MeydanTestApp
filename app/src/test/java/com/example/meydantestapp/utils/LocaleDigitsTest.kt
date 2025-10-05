package com.example.meydantestapp.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleDigitsTest {

    @Test
    fun `toLatinDigits converts arabic indic numbers`() {
        assertEquals("0123456789", "٠١٢٣٤٥٦٧٨٩".toLatinDigits())
    }

    @Test
    fun `toLatinDigits leaves latin digits unchanged`() {
        assertEquals("12345", "12345".toLatinDigits())
    }

    @Test
    fun `toLatinDigits handles mixed characters`() {
        assertEquals("Invoice 204", "Invoice ٢٠٤".toLatinDigits())
    }
}
