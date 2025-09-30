package com.example.meydantestapp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.security.ProviderInstaller

class MyApp : Application(), Application.ActivityLifecycleCallbacks {

    private var startedCount = 0

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

        try {
            val appCheck = FirebaseAppCheck.getInstance()
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d("AppCheck", "PlayIntegrityAppCheckProviderFactory installed")
        } catch (e: Exception) {
            Log.w("AppCheck", "Failed to install AppCheck provider: ${e.message}")
        }

        val themePrefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val nightMode = themePrefs.getInt("NightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(nightMode)

        val sessionPrefs = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
        val wasBackgrounded = sessionPrefs.getBoolean(KEY_WAS_BACKGROUNDED, false)
        if (wasBackgrounded) {
            FirebaseAuth.getInstance().signOut()
            sessionPrefs.edit().putBoolean(KEY_WAS_BACKGROUNDED, false).apply()
        }

        sessionPrefs.edit().putLong(KEY_LAST_ACTIVE_MS, System.currentTimeMillis()).apply()
        registerActivityLifecycleCallbacks(this)

        initializeSecurityProvider()
    }

    override fun onActivityStarted(activity: Activity) { /* نفس منطق debug لو أردت */ }
    override fun onActivityStopped(activity: Activity) { /* … */ }
    override fun onActivityResumed(activity: Activity) { /* … */ }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private fun initializeSecurityProvider() {
        try {
            ProviderInstaller.installIfNeededAsync(
                this,
                object : ProviderInstaller.ProviderInstallListener {
                    override fun onProviderInstalled() {
                        Log.d("SecurityProvider", "Provider installer finished successfully")
                    }

                    override fun onProviderInstallFailed(errorCode: Int, resolutionIntent: Intent?) {
                        val message = GoogleApiAvailability.getInstance().getErrorString(errorCode)
                        Log.w(
                            "SecurityProvider",
                            "Provider install failed code=$errorCode message=$message"
                        )
                    }
                }
            )
        } catch (security: SecurityException) {
            Log.w(
                "SecurityProvider",
                "Security exception while installing provider: ${security.message}"
            )
        } catch (throwable: Throwable) {
            Log.w(
                "SecurityProvider",
                "Unexpected error while installing provider: ${throwable.message}",
                throwable
            )
        }
    }

    companion object {
        const val SESSION_TIMEOUT_MS: Long = 15L * 60L * 1000L
        const val KEY_WAS_BACKGROUNDED = "was_backgrounded"
        const val KEY_LAST_ACTIVE_MS = "last_active_time"
    }
}
