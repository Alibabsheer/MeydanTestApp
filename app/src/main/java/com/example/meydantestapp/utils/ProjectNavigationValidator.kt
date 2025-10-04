package com.example.meydantestapp.utils

/**
 * Ensures that navigation flows pass around valid project identifiers.
 */
object ProjectNavigationValidator {

    /**
     * Returns the trimmed project id when it is non-null and non-blank; otherwise `null`.
     */
    fun sanitize(projectId: String?): String? = projectId?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Throws [IllegalArgumentException] when the identifier is missing.
     */
    fun require(projectId: String?): String = sanitize(projectId)
        ?: throw IllegalArgumentException("Project identifier is required for navigation")
}
