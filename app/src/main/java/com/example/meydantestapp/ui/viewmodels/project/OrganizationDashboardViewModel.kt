package com.example.meydantestapp.ui.viewmodels.project

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.data.model.Project
import com.example.meydantestapp.data.repository.AuthRepository
import com.example.meydantestapp.data.repository.ProjectRepository
import com.example.meydantestapp.utils.Constants
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.meydantestapp.ui.viewmodels.BaseViewModel

class OrganizationDashboardViewModel : BaseViewModel() {

    private val authRepository = AuthRepository()
    private val projectRepository = ProjectRepository()

    private val _organizationName = MutableLiveData<String>()
    val organizationName: LiveData<String> = _organizationName

    private val _totalProjects = MutableLiveData<Int>()
    val totalProjects: LiveData<Int> = _totalProjects

    private val _activeProjects = MutableLiveData<Int>()
    val activeProjects: LiveData<Int> = _activeProjects

    private val _affiliatedUsers = MutableLiveData<Int>()
    val affiliatedUsers: LiveData<Int> = _affiliatedUsers

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        fetchDashboardData()
    }

    fun fetchDashboardData() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId()
                if (userId == null) {
                    _errorMessage.value = "لم يتم تسجيل الدخول."
                    return@launch
                }

                val userDataResult = authRepository.getUserData(userId)
                if (userDataResult.isSuccess) {
                    val userData = userDataResult.getOrThrow()
                    _organizationName.value = userData["organizationName"] as? String ?: "مؤسسة غير معروفة"

                    val organizationId = userData["organizationId"] as? String ?: userId

                    // Fetch projects
                    val projectsResult = projectRepository.getProjectsByOrganization(organizationId)
                    if (projectsResult.isSuccess) {
                        val projects: List<Project> = projectsResult.getOrThrow()
                        _totalProjects.value = projects.size
                        _activeProjects.value = projects.count { it.status == "active" }
                    } else {
                        _errorMessage.value = "فشل في جلب المشاريع: ${projectsResult.exceptionOrNull()?.message}"
                    }

                    // Fetch affiliated users (assuming they are stored under the organization's document or a subcollection)
                    // This part might need adjustment based on actual Firestore structure for users
                    // For now, let's assume users are directly under 'users' collection with organizationId field
                    val usersSnapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection(Constants.COLLECTION_USERS)
                        .whereEqualTo("organizationId", organizationId)
                        .get().await()
                    _affiliatedUsers.value = usersSnapshot.size()

                } else {
                    _errorMessage.value = "فشل في جلب بيانات المؤسسة: ${userDataResult.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "حدث خطأ: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}


