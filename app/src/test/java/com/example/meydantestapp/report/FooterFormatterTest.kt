package com.example.meydantestapp.report

import org.junit.Assert.assertEquals
import org.junit.Test

class FooterFormatterTest {
    @Test
    fun formatFooter_examples() {
        assertEquals("Page 1 of 3", formatFooter(1, 3))
        assertEquals("Page 3 of 3", formatFooter(3, 3))
        assertEquals("Page 1 of 1", formatFooter(1, 1))
    }
}
