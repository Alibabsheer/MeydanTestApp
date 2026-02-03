package com.example.meydantestapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.repository.AuthRepository
import com.example.meydantestapp.utils.ValidationUtils
import kotlinx.coroutines.launch

class RegisterOrganizationViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    private val _registrationResult = MutableLiveData<Result<String>>()
    val registrationResult: LiveData<Result<String>> = _registrationResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _organizationNameError = MutableLiveData<String?>()
    val organizationNameError: LiveData<String?> = _organizationNameError

    private val _emailError = MutableLiveData<String?>()
    val emailError: LiveData<String?> = _emailError

    private val _passwordError = MutableLiveData<String?>()
    val passwordError: LiveData<String?> = _passwordError

    private val _confirmPasswordError = MutableLiveData<String?>()
    val confirmPasswordError: LiveData<String?> = _confirmPasswordError

    fun registerOrganization(
        organizationName: String,
        activityType: String,
        email: String,
        password: String,
        confirmPassword: String
    ) {
        // Validate input
        val orgNameErrorMsg = ValidationUtils.getOrganizationNameErrorMessage(organizationName)
        val emailErrorMsg = ValidationUtils.getEmailErrorMessage(email)
        val passwordErrorMsg = ValidationUtils.getPasswordErrorMessage(password)
        val confirmPasswordErrorMsg = ValidationUtils.getConfirmPasswordErrorMessage(password, confirmPassword)

        _organizationNameError.value = orgNameErrorMsg
        _emailError.value = emailErrorMsg
        _passwordError.value = passwordErrorMsg
        _confirmPasswordError.value = confirmPasswordErrorMsg

        if (orgNameErrorMsg != null || emailErrorMsg != null || 
            passwordErrorMsg != null || confirmPasswordErrorMsg != null) {
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = authRepository.registerOrganization(
                    organizationName, activityType, email, password
                )
                _registrationResult.value = result
            } catch (e: Exception) {
                _registrationResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearErrors() {
        _organizationNameError.value = null
        _emailError.value = null
        _passwordError.value = null
        _confirmPasswordError.value = null
    }
}

