package com.example.meydantestapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectLocationRenderStateTest {

    @Test
    fun `computeLocationRenderState returns link when url present`() {
        val state = computeLocationRenderState("  جدة ", " https://maps.google.com/?q=jeddah ")

        assertEquals("جدة", state.addressText)
        assertEquals("https://maps.google.com/?q=jeddah", state.googleMapsUrl)
        assertTrue(state.isLink)
    }

    @Test
    fun `computeLocationRenderState strips link when url missing`() {
        val state = computeLocationRenderState("المدينة", "  ")

        assertEquals("المدينة", state.addressText)
        assertNull(state.googleMapsUrl)
        assertFalse(state.isLink)
    }
}
