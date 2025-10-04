package com.example.meydantestapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectNavigationValidatorTest {

    @Test
    fun `sanitize trims and accepts valid identifier`() {
        val id = ProjectNavigationValidator.sanitize("  project-42  ")
        assertEquals("project-42", id)
    }

    @Test
    fun `sanitize returns null for blank input`() {
        assertNull(ProjectNavigationValidator.sanitize("   "))
        assertNull(ProjectNavigationValidator.sanitize(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `require throws on invalid id`() {
        ProjectNavigationValidator.require(null)
    }
}
