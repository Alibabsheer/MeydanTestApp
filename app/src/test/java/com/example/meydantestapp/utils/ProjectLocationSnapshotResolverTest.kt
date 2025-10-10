package com.example.meydantestapp.utils

import com.example.meydantestapp.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectLocationSnapshotResolverTest {

    @Test
    fun fromProjectData_prefersExplicitFields() {
        val snapshot = ProjectLocationSnapshotResolver.fromProjectData(
            mapOf(
                "addressText" to "حي النخيل",
                "googleMapsUrl" to "https://maps.google.com/?q=nakhil"
            ),
            Project(addressText = "fallback", googleMapsUrl = "https://maps.google.com/?q=fallback")
        )

        assertEquals("حي النخيل", snapshot.addressText)
        assertEquals("https://maps.google.com/?q=nakhil", snapshot.googleMapsUrl)
    }

    @Test
    fun fromProjectData_derivesUrlFromCoordinates() {
        val snapshot = ProjectLocationSnapshotResolver.fromProjectData(
            mapOf(
                "addressText" to "Riyadh",
                "latitude" to 24.7136,
                "longitude" to 46.6753
            ),
            null
        )

        assertEquals("Riyadh", snapshot.addressText)
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=24.7136,46.6753",
            snapshot.googleMapsUrl
        )
    }

    @Test
    fun fromProjectData_buildsSearchUrlWhenMissingCoordinates() {
        val snapshot = ProjectLocationSnapshotResolver.fromProjectData(
            mapOf("addressText" to "طريق الملك فهد، الرياض"),
            null
        )

        assertEquals("طريق الملك فهد، الرياض", snapshot.addressText)
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=%D8%B7%D8%B1%D9%8A%D9%82+%D8%A7%D9%84%D9%85%D9%84%D9%83+%D9%81%D9%87%D8%AF%D8%8C+%D8%A7%D9%84%D8%B1%D9%8A%D8%A7%D8%B6",
            snapshot.googleMapsUrl
        )
    }

    @Test
    fun fromReportData_resolvesLegacyAddress() {
        val snapshot = ProjectLocationSnapshotResolver.fromReportData(
            mapOf("projectLocation" to "المدينة المنورة")
        )

        assertEquals("المدينة المنورة", snapshot.addressText)
        assertNull(snapshot.googleMapsUrl)
    }
}
