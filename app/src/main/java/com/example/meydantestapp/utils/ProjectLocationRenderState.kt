package com.example.meydantestapp.utils

/**
 * Pure render-state helper for displaying project location hyperlinks in the UI.
 */
data class ProjectLocationRenderState(
    val addressText: String?,
    val googleMapsUrl: String?
) {
    val isLink: Boolean = !googleMapsUrl.isNullOrBlank()
}

fun computeLocationRenderState(
    addressText: String?,
    googleMapsUrl: String?
): ProjectLocationRenderState {
    val text = ProjectLocationUtils.normalizeAddressText(addressText)
    val url = ProjectLocationUtils.normalizeGoogleMapsUrl(googleMapsUrl)
    return ProjectLocationRenderState(text, url)
}
