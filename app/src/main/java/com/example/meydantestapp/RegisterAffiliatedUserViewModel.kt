package com.example.meydantestapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.repository.AuthRepository
import kotlinx.coroutines.launch

class RegisterAffiliatedUserViewModel : ViewModel() {

    private val authRepository = AuthRepository()

    private val _registrationResult = MutableLiveData<Result<String>>()
    val registrationResult: LiveData<Result<String>> = _registrationResult

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userNameError = MutableLiveData<String?>()
    val userNameError: LiveData<String?> = _userNameError

    private val _emailError = MutableLiveData<String?>()
    val emailError: LiveData<String?> = _emailError

    private val _passwordError = MutableLiveData<String?>()
    val passwordError: LiveData<String?> = _passwordError

    private val _confirmPasswordError = MutableLiveData<String?>()
    val confirmPasswordError: LiveData<String?> = _confirmPasswordError

    private val _joinCodeError = MutableLiveData<String?>()
    val joinCodeError: LiveData<String?> = _joinCodeError

    fun registerAffiliatedUser(
        userName: String,
        email: String,
        password: String,
        confirmPassword: String,
        organizationId: String,
        joinCode: String
    ) {
        // basic validation (keep your existing ValidationUtils if needed)
        val userNameErrorMsg = if (userName.isBlank()) "اسم المستخدم مطلوب" else null
        val emailErrorMsg = if (email.isBlank()) "البريد الإلكتروني مطلوب" else null
        val passwordErrorMsg = if (password.length < 6) "كلمة المرور قصيرة" else null
        val confirmPasswordErrorMsg = if (password != confirmPassword) "كلمتا المرور غير متطابقتين" else null
        val joinCodeErrorMsg = if (joinCode.isBlank()) "كود الانضمام مطلوب" else null

        _userNameError.value = userNameErrorMsg
        _emailError.value = emailErrorMsg
        _passwordError.value = passwordErrorMsg
        _confirmPasswordError.value = confirmPasswordErrorMsg
        _joinCodeError.value = joinCodeErrorMsg

        if (listOf(userNameErrorMsg, emailErrorMsg, passwordErrorMsg, confirmPasswordErrorMsg, joinCodeErrorMsg).any { it != null }) {
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = authRepository.registerAffiliatedUser(
                    userName, email, password, organizationId, joinCode
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
        _userNameError.value = null
        _emailError.value = null
        _passwordError.value = null
        _confirmPasswordError.value = null
        _joinCodeError.value = null
    }
}
