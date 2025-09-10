package com.example.meydantestapp.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    
    fun saveUserSession(userId: String, organizationId: String, userType: String) {
        prefs.edit().apply {
            putString(Constants.KEY_USER_ID, userId)
            putString(Constants.KEY_ORGANIZATION_ID, organizationId)
            putString(Constants.KEY_USER_TYPE, userType)
            putBoolean(Constants.KEY_IS_LOGGED_IN, true)
            apply()
        }
    }
    
    fun clearUserSession() {
        prefs.edit().apply {
            remove(Constants.KEY_USER_ID)
            remove(Constants.KEY_ORGANIZATION_ID)
            remove(Constants.KEY_USER_TYPE)
            putBoolean(Constants.KEY_IS_LOGGED_IN, false)
            apply()
        }
    }
    
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(Constants.KEY_IS_LOGGED_IN, false)
    }
    
    fun getUserId(): String? {
        return prefs.getString(Constants.KEY_USER_ID, null)
    }
    
    fun getOrganizationId(): String? {
        return prefs.getString(Constants.KEY_ORGANIZATION_ID, null)
    }
    
    fun getUserType(): String? {
        return prefs.getString(Constants.KEY_USER_TYPE, null)
    }
    
    fun isOrganizationUser(): Boolean {
        return getUserType() == Constants.USER_TYPE_ORGANIZATION
    }
    
    fun isAffiliatedUser(): Boolean {
        return getUserType() == Constants.USER_TYPE_AFFILIATED
    }
}

