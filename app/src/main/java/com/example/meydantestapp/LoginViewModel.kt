package com.example.meydantestapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.repository.AuthRepository
import com.example.meydantestapp.repository.AuthRepository.AccountTypeResult
import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.ValidationUtils
import kotlinx.coroutines.launch

sealed class LoginDestination {
    object OrganizationDashboard : LoginDestination()
    data class UserProjects(val organizationId: String) : LoginDestination()
}

class LoginViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    private val _loginResult = MutableLiveData<Result<LoginDestination>>()
    val loginResult: LiveData<Result<LoginDestination>> = _loginResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _emailError = MutableLiveData<String?>()
    val emailError: LiveData<String?> = _emailError

    private val _passwordError = MutableLiveData<String?>()
    val passwordError: LiveData<String?> = _passwordError

    fun login(email: String, password: String) {
        // Validate input
        val emailErrorMsg = ValidationUtils.getEmailErrorMessage(email)
        val passwordErrorMsg = ValidationUtils.getPasswordErrorMessage(password)

        _emailError.value = emailErrorMsg
        _passwordError.value = passwordErrorMsg

        if (emailErrorMsg != null || passwordErrorMsg != null) {
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val loginRes = authRepository.loginUser(email, password)
                loginRes.fold(
                    onSuccess = { userId ->
                        handleAccountType(userId)
                    },
                    onFailure = { e -> _loginResult.value = Result.failure(e) }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearErrors() {
        _emailError.value = null
        _passwordError.value = null
    }

    fun checkUserSession() {
        val userId = authRepository.getCurrentUserId() ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                handleAccountType(userId)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun handleAccountType(userId: String) {
        val typeResult = authRepository.resolveAccountType(userId)
        typeResult.fold(
            onSuccess = { info: AccountTypeResult ->
                val destination = if (info.userType == Constants.USER_TYPE_ORGANIZATION) {
                    LoginDestination.OrganizationDashboard
                } else {
                    LoginDestination.UserProjects(info.organizationId ?: "")
                }
                _loginResult.value = Result.success(destination)
            },
            onFailure = {
                authRepository.logout()
                _loginResult.value = Result.failure(it)
            }
        )
    }
}

