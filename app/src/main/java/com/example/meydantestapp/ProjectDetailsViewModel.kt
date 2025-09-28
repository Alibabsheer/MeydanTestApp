package com.example.meydantestapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.data.model.Project
import com.example.meydantestapp.repository.ProjectRepository
import kotlinx.coroutines.launch

class ProjectDetailsViewModel : ViewModel() {

    private val projectRepository = ProjectRepository()

    private val _projectDetails = MutableLiveData<Project?>()
    val projectDetails: LiveData<Project?> = _projectDetails

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun fetchProjectDetails(projectId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = projectRepository.getProjectById(projectId)
                if (result.isSuccess) {
                    _projectDetails.value = result.getOrThrow()
                } else {
                    _errorMessage.value = "فشل في جلب تفاصيل المشروع: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "حدث خطأ: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateProject(projectId: String, updates: Map<String, Any>) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = projectRepository.updateProject(projectId, updates)
                if (result.isSuccess) {
                    fetchProjectDetails(projectId) // Refresh details after update
                } else {
                    _errorMessage.value = "فشل في تحديث المشروع: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "حدث خطأ: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

