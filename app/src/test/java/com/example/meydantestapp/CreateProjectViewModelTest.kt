package com.example.meydantestapp

import com.example.meydantestapp.LumpSumItem
import com.example.meydantestapp.repository.ProjectRepository
import com.example.meydantestapp.utils.AuthProvider
import com.google.firebase.Timestamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateProjectViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAuthProvider(private val uid: String?) : AuthProvider {
        override fun currentUserId(): String? = uid
    }

    private class CapturingRepository : ProjectRepository() {
        var lastOrganizationId: String? = null
        var lastPayload: Map<String, Any?>? = null

        override suspend fun createProject(
            organizationId: String,
            projectData: Map<String, Any?>
        ): Result<String> {
            lastOrganizationId = organizationId
            lastPayload = projectData
            return Result.success("project-123")
        }
    }

    @Test
    fun `createProject persists normalized timestamps`() = runTest {
        val repository = CapturingRepository()
        val viewModel = CreateProjectViewModel(repository, FakeAuthProvider("org-1"))

        viewModel.createProject(
            projectName = "Test",
            addressText = "Address",
            latitude = 24.0,
            longitude = 46.0,
            startDateStr = "30/09/2025",
            endDateStr = "01/10/2025",
            workType = "مقطوعية",
            quantitiesTableData = null,
            lumpSumTableData = listOf(LumpSumItem(itemNumber = "1", totalValue = 10.0)),
            calculatedContractValue = 10.0,
            plusCode = null
        )

        advanceUntilIdle()

        val payload = repository.lastPayload ?: error("payload not captured")
        val startTimestamp = payload["startDate"] as? Timestamp
        val endTimestamp = payload["endDate"] as? Timestamp

        requireNotNull(startTimestamp)
        requireNotNull(endTimestamp)

        val diffSeconds = endTimestamp.seconds - startTimestamp.seconds
        // ensure ordering is preserved and both timestamps exist
        assertEquals(86400L, diffSeconds)
        assertEquals("project-123", viewModel.createSuccess.getOrAwaitValue())
    }

    @Test
    fun `createProject surfaces error when auth missing`() = runTest {
        val repository = CapturingRepository()
        val viewModel = CreateProjectViewModel(repository, FakeAuthProvider(null))

        viewModel.createProject(
            projectName = "Test",
            addressText = "Address",
            latitude = 24.0,
            longitude = 46.0,
            startDateStr = "30/09/2025",
            endDateStr = "01/10/2025",
            workType = "مقطوعية",
            quantitiesTableData = null,
            lumpSumTableData = listOf(LumpSumItem(itemNumber = "1", totalValue = 10.0)),
            calculatedContractValue = 10.0,
            plusCode = null
        )

        val error = viewModel.errorMessage.getOrAwaitValue()
        assertEquals("خطأ: المستخدم غير مسجل الدخول.", error)
        assertNull(repository.lastPayload)
    }
}
