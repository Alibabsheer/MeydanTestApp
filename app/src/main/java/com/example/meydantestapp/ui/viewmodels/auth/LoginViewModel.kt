package com.example.meydantestapp.ui.viewmodels.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.meydantestapp.data.repository.AuthRepository
import com.example.meydantestapp.ui.viewmodels.BaseViewModel
import com.example.meydantestapp.utils.ValidationUtils
import com.example.meydantestapp.R

class LoginViewModel : BaseViewModel() {

    private val authRepository = AuthRepository()

    private val _loginResult = MutableLiveData<Result<String>>()
    val loginResult: LiveData<Result<String>> = _loginResult

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

        launchWithResult(
            onSuccess = { userId ->
                _loginResult.value = Result.success(userId)
            },
            onError = { error ->
                _loginResult.value = Result.failure(error)
            }
        ) {
            authRepository.loginUser(email, password)
        }
    }

    fun clearErrors() {
        _emailError.value = null
        _passwordError.value = null
        clearMessages()
    }

    fun isUserLoggedIn(): Boolean {
        return authRepository.getCurrentUserId() != null
    }
}
