package com.example.meydantestapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.repository.AuthRepository
import com.example.meydantestapp.utils.ValidationUtils
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    private val _loginResult = MutableLiveData<Result<String>>()
    val loginResult: LiveData<Result<String>> = _loginResult

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
                val result = authRepository.loginUser(email, password)
                _loginResult.value = result
            } catch (e: Exception) {
                _loginResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearErrors() {
        _emailError.value = null
        _passwordError.value = null
    }

    fun isUserLoggedIn(): Boolean {
        return authRepository.getCurrentUserId() != null
    }
}

