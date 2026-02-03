package com.example.meydantestapp.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

object PlayServicesUtils {
    private const val TAG = "PlayServices"

    fun isAvailable(context: Context): Boolean {
        val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        val available = code == ConnectionResult.SUCCESS
        Log.d(TAG, "Availability check result=$code available=$available")
        return available
    }

    /**
     * Optionally show repair dialog if user-resolvable.
     * Returns true if services are OK, false otherwise.
     */
    fun ensureAvailableOrExplain(activity: Activity, requestCode: Int = 9000): Boolean {
        val api = GoogleApiAvailability.getInstance()
        val code = api.isGooglePlayServicesAvailable(activity)
        if (code == ConnectionResult.SUCCESS) {
            Log.d(TAG, "Google Play Services available")
            return true
        }
        Log.w(TAG, "Google Play Services unavailable with code=$code")
        if (api.isUserResolvableError(code)) {
            api.getErrorDialog(activity, code, requestCode)?.show()
        }
        return false
    }
}
