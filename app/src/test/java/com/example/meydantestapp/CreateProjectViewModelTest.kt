package com.example.meydantestapp

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.meydantestapp.LumpSumItem
import com.example.meydantestapp.repository.ProjectRepository
import com.example.meydantestapp.utils.AuthProvider
import com.example.meydantestapp.utils.FirestoreProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateProjectViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private class FakeAuthProvider(private val uid: String?) : AuthProvider {
        override fun currentUserId(): String? = uid
    }

    private object NoopFirestoreProvider : FirestoreProvider {
        override fun get(): FirebaseFirestore = error("Firestore should not be accessed in unit tests")
    }

    private class CapturingRepository : ProjectRepository(NoopFirestoreProvider) {
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
    fun `createProject persists normalized timestamps`() = runTest(testDispatcher) {
        val repository = CapturingRepository()
        val viewModel = CreateProjectViewModel(repository, FakeAuthProvider("org-1"))

        viewModel.createProject(
            projectName = "Test",
            addressText = "Address",
            latitude = 24.0,
            longitude = 46.0,
            startDate = LocalDate.of(2025, 9, 30),
            endDate = LocalDate.of(2025, 10, 1),
            workType = "مقطوعية",
            quantitiesTableData = null,
            lumpSumTableData = listOf(LumpSumItem(itemNumber = "1", totalValue = 10.0)),
            calculatedContractValue = 10.0,
            plusCode = null,
            ownerName = " أحمد  ",
            contractorName = "",
            consultantName = "  مكتب الاستشارات "
        )

        advanceUntilIdle()

        val payload = repository.lastPayload ?: error("payload not captured")
        val startEpochDay = payload["startDateEpochDay"] as? Long
        val endEpochDay = payload["endDateEpochDay"] as? Long

        requireNotNull(startEpochDay)
        requireNotNull(endEpochDay)

        assertEquals(LocalDate.of(2025, 9, 30).toEpochDay(), startEpochDay)
        assertEquals(LocalDate.of(2025, 10, 1).toEpochDay(), endEpochDay)
        assertEquals("أحمد", payload["ownerName"])
        assertNull(payload["contractorName"])
        assertEquals("مكتب الاستشارات", payload["consultantName"])
        assertEquals("project-123", viewModel.createSuccess.getOrAwaitValue())
    }

    @Test
    fun `createProject surfaces error when auth missing`() = runTest(testDispatcher) {
        val repository = CapturingRepository()
        val viewModel = CreateProjectViewModel(repository, FakeAuthProvider(null))

        viewModel.createProject(
            projectName = "Test",
            addressText = "Address",
            latitude = 24.0,
            longitude = 46.0,
            startDate = LocalDate.of(2025, 9, 30),
            endDate = LocalDate.of(2025, 10, 1),
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

    @Test
    fun `createProject rejects missing end date`() = runTest(testDispatcher) {
        val repository = CapturingRepository()
        val viewModel = CreateProjectViewModel(repository, FakeAuthProvider("org-1"))

        viewModel.createProject(
            projectName = "Test",
            addressText = "Address",
            latitude = 24.0,
            longitude = 46.0,
            startDate = LocalDate.of(2025, 9, 30),
            endDate = null,
            workType = "مقطوعية",
            quantitiesTableData = null,
            lumpSumTableData = listOf(LumpSumItem(itemNumber = "1", totalValue = 10.0)),
            calculatedContractValue = 10.0,
            plusCode = null
        )

        val error = viewModel.errorMessage.getOrAwaitValue()
        assertEquals("تأكد من إدخال تواريخ صحيحة.", error)
        assertNull(repository.lastPayload)
    }

    @Test
    fun `createProject rejects end date before start`() = runTest(testDispatcher) {
        val repository = CapturingRepository()
        val viewModel = CreateProjectViewModel(repository, FakeAuthProvider("org-1"))

        viewModel.createProject(
            projectName = "Test",
            addressText = "Address",
            latitude = 24.0,
            longitude = 46.0,
            startDate = LocalDate.of(2025, 10, 2),
            endDate = LocalDate.of(2025, 10, 1),
            workType = "مقطوعية",
            quantitiesTableData = null,
            lumpSumTableData = listOf(LumpSumItem(itemNumber = "1", totalValue = 10.0)),
            calculatedContractValue = 10.0,
            plusCode = null
        )

        val error = viewModel.errorMessage.getOrAwaitValue()
        assertEquals("تاريخ الانتهاء يجب أن يكون بعد تاريخ البدء.", error)
        assertNull(repository.lastPayload)
    }
}
