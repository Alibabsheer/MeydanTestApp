package com.example.meydantestapp.ui.viewmodels.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.meydantestapp.ui.viewmodels.BaseViewModel

class TypeSelectionViewModel : BaseViewModel() {

    private val _navigateToRegisterOrganization = MutableLiveData<Boolean>()
    val navigateToRegisterOrganization: LiveData<Boolean> = _navigateToRegisterOrganization

    private val _navigateToJoinCodeEntry = MutableLiveData<Boolean>()
    val navigateToJoinCodeEntry: LiveData<Boolean> = _navigateToJoinCodeEntry

    fun onOrganizationAccountClicked() {
        _navigateToRegisterOrganization.value = true
    }

    fun onUserAccountClicked() {
        _navigateToJoinCodeEntry.value = true
    }

    fun navigationComplete() {
        _navigateToRegisterOrganization.value = false
        _navigateToJoinCodeEntry.value = false
    }
}

