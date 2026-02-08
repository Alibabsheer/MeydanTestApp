package com.example.meydantestapp.ui.viewmodels.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.example.meydantestapp.data.repository.OrganizationRepository
import kotlinx.coroutines.launch
import android.net.Uri
import com.example.meydantestapp.ui.viewmodels.BaseViewModel

class AccountSettingsViewModel : BaseViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val repo = OrganizationRepository()

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _organizationName = MutableLiveData<String>()
    val organizationName: LiveData<String> = _organizationName

    private val _activityType = MutableLiveData<String>()
    val activityType: LiveData<String> = _activityType

    private val _joinCode = MutableLiveData<String>()
    val joinCode: LiveData<String> = _joinCode

    private val _logoUrl = MutableLiveData<String?>()
    val logoUrl: LiveData<String?> = _logoUrl

    fun loadData() {
        val userId = auth.currentUser?.uid ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val data = repo.getOrganizationDoc(userId)
                _organizationName.value = (data?.get("organizationName") as? String).orEmpty()
                _activityType.value = (data?.get("activityType") as? String).orEmpty()
                _joinCode.value = (data?.get("joinCode") as? String).orEmpty()
                _logoUrl.value = data?.get("logoUrl") as? String
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateName(name: String) = updateFields(name = name)
    fun updateActivityType(type: String) = updateFields(activityType = type)
    fun updateJoinCode(code: String) = updateFields(joinCode = code)

    fun saveAll(name: String, type: String, code: String) {
        updateFields(name = name, activityType = type, joinCode = code)
    }

    private fun updateFields(name: String? = null, activityType: String? = null, joinCode: String? = null) {
        val userId = auth.currentUser?.uid ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repo.updateOrganizationFields(userId, name, activityType, joinCode, null)
                name?.let { _organizationName.value = it }
                activityType?.let { _activityType.value = it }
                joinCode?.let { _joinCode.value = it.uppercase() }
                _message.value = "تم حفظ التغييرات"
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadLogo(imageUri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val url = repo.uploadOrganizationLogo(userId, imageUri)
                // احفظ رابط الشعار داخل مستند المؤسسة حتى يتم عرضه دائمًا بشكل افتراضي
                repo.setLogoUrl(userId, url)
                _logoUrl.value = url
                _message.value = "تم تحديث الشعار"
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateJoinCodeFromEmail(): String? {
        val email = auth.currentUser?.email ?: return null
        val prefix = email.substringBefore("@").uppercase()
        val number = (100..999).random()
        val code = "$prefix$number"
        _joinCode.value = code
        return code
    }

    fun deleteOrganizationAndAccount(onDone: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return
        val uid = user.uid
        _isLoading.value = true
        viewModelScope.launch {
            try {
                repo.deleteOrganization(uid)
                user.delete()
                onDone(true, null)
            } catch (e: Exception) {
                onDone(false, e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
