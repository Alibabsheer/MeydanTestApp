package com.example.meydantestapp.report

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportPdfBuilderTest {

    @Test
    fun formatFooter_singlePageWithinTotal() {
        assertEquals("Page 1 of 3", ReportPdfBuilder.formatFooter(1, 3))
    }

    @Test
    fun formatFooter_lastPageMatchesTotal() {
        assertEquals("Page 3 of 3", ReportPdfBuilder.formatFooter(3, 3))
    }
}
