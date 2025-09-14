package com.example.meydantestapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.repository.AuthRepository
import com.example.meydantestapp.repository.ProjectRepository
import kotlinx.coroutines.launch

class ProjectDetailsViewModel : ViewModel() {

    private val projectRepository = ProjectRepository()
    private val authRepository = AuthRepository()

    private val _projectDetails = MutableLiveData<Map<String, Any>?>(null)
    val projectDetails: LiveData<Map<String, Any>?> = _projectDetails

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _organizationId = MutableLiveData<String?>(null)
    val organizationId: LiveData<String?> = _organizationId

    private val _projectId = MutableLiveData<String?>(null)
    val projectId: LiveData<String?> = _projectId

    fun initialize(projectId: String?, organizationId: String?) {
        _projectId.value = projectId
        if (organizationId != null) {
            _organizationId.value = organizationId
            fetchProject()
        } else {
            resolveOrganizationId()
        }
    }

    private fun resolveOrganizationId() {
        viewModelScope.launch {
            val uid = authRepository.getCurrentUserId()
            if (uid == null) {
                _errorMessage.value = "لم يتم تسجيل الدخول"
                return@launch
            }
            val data = authRepository.getUserData(uid).getOrNull()
            val orgId = data?.get("organizationId")?.toString()
            if (orgId != null) {
                _organizationId.value = orgId
                fetchProject()
            } else {
                _errorMessage.value = "تعذر تحديد مؤسسة المستخدم."
            }
        }
    }

    private fun fetchProject() {
        val pid = _projectId.value ?: return
        val oid = _organizationId.value ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = projectRepository.getProjectById(oid, pid)
                if (result.isSuccess) {
                    _projectDetails.value = result.getOrNull()
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
                    fetchProject()
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

