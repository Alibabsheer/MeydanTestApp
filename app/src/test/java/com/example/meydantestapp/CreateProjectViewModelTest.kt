package com.example.meydantestapp

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CreateProjectViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `createProject with valid data saves timestamps`() {
        val repository = mock<com.example.meydantestapp.repository.ProjectRepository>()
        val auth = mock<FirebaseAuth>()
        val user = mock<FirebaseUser>()
        whenever(auth.currentUser).thenReturn(user)
        whenever(user.uid).thenReturn("org-123")

        runBlocking {
            whenever(repository.createProject(any(), any<Map<String, Any?>>())).thenReturn(
                Result.success("project-001")
            )
        }

        val viewModel = CreateProjectViewModel(repository, auth)

        viewModel.createProject(
            projectName = "Test Project",
            addressText = "Riyadh",
            latitude = 24.7136,
            longitude = 46.6753,
            startDateStr = "15/04/2025",
            endDateStr = "20/04/2025",
            workType = "جدول كميات",
            quantitiesTableData = listOf(QuantityItem()),
            lumpSumTableData = null,
            calculatedContractValue = 1500.0,
            plusCode = "7H7Q+4C"
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val projectCaptor = argumentCaptor<Map<String, Any?>>()
        verify(repository).createProject(any(), projectCaptor.capture())
        val payload = projectCaptor.firstValue
        val start = payload["startDate"]
        val end = payload["endDate"]
        assertTrue(start is Timestamp)
        assertTrue(end is Timestamp)
        assertEquals((start as Timestamp).seconds <= (end as Timestamp).seconds, true)
        assertEquals("project-001", viewModel.createSuccess.getOrAwaitValue())
        assertNull(viewModel.errorMessage.getOrAwaitValue())
    }

    @Test
    fun `createProject with invalid dates emits error`() {
        val repository = mock<com.example.meydantestapp.repository.ProjectRepository>()
        val auth = mock<FirebaseAuth>()
        val user = mock<FirebaseUser>()
        whenever(auth.currentUser).thenReturn(user)
        whenever(user.uid).thenReturn("org-123")

        val viewModel = CreateProjectViewModel(repository, auth)

        viewModel.createProject(
            projectName = "Test Project",
            addressText = "Riyadh",
            latitude = null,
            longitude = null,
            startDateStr = "invalid",
            endDateStr = "also invalid",
            workType = "جدول كميات",
            quantitiesTableData = listOf(QuantityItem()),
            lumpSumTableData = null,
            calculatedContractValue = 1500.0,
            plusCode = null
        )

        verifyNoInteractions(repository)
        assertEquals("تأكد من إدخال تواريخ صحيحة.", viewModel.errorMessage.getOrAwaitValue())
    }
}
