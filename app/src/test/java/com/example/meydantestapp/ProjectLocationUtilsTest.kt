package com.example.meydantestapp

import com.example.meydantestapp.utils.ProjectLocationUtils
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectLocationUtilsTest {

    @Test
    fun buildGoogleMapsUrl_requiresCoordinates() {
        val data = mapOf(
            "addressText" to "الرياض",
            "location" to "الرياض"
        )

        val url = ProjectLocationUtils.buildGoogleMapsUrl(data)

        assertNull("URL should be null when coordinates are absent", url)
    }

    @Test
    fun buildGoogleMapsUrl_withCoordinates() {
        val url = ProjectLocationUtils.buildGoogleMapsUrl(24.7136, 46.6753)

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=24.7136,46.6753",
            url
        )
    }
}
