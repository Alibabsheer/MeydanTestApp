package com.example.meydantestapp.ui.viewmodels.project

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.data.model.Project
import com.example.meydantestapp.data.repository.AuthRepository
import com.example.meydantestapp.data.repository.ProjectRepository
import kotlinx.coroutines.launch
import com.example.meydantestapp.ui.viewmodels.BaseViewModel

class ProjectsViewModel : BaseViewModel() {

    private val authRepository = AuthRepository()
    private val projectRepository = ProjectRepository()

    private val _projects = MutableLiveData<List<Project>>()
    val projects: LiveData<List<Project>> = _projects

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        fetchProjects()
    }

    fun fetchProjects() {
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
                    val organizationId = userData["organizationId"] as? String ?: userId

                    val result = projectRepository.getProjectsByOrganization(organizationId)
                    if (result.isSuccess) {
                        _projects.value = result.getOrThrow()
                    } else {
                        _errorMessage.value = "فشل في جلب المشاريع: ${result.exceptionOrNull()?.message}"
                    }
                } else {
                    _errorMessage.value = "فشل في جلب بيانات المستخدم: ${userDataResult.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "حدث خطأ: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteProject(projectId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = projectRepository.deleteProject(projectId)
                if (result.isSuccess) {
                    fetchProjects() // Refresh the list after deletion
                } else {
                    _errorMessage.value = "فشل في حذف المشروع: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "حدث خطأ: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

