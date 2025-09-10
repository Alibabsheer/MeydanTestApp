package com.example.meydantestapp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth

// App Check
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class MyApp : Application(), Application.ActivityLifecycleCallbacks {

    private var startedCount = 0

    override fun onCreate() {
        super.onCreate()

        // ========= App Check =========
        try {
            val appCheck = FirebaseAppCheck.getInstance()
            if (isDebugBuild()) {
                // أثناء التطوير: Debug Provider (سيطبع Debug Token في Logcat أول مرة)
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d("AppCheck", "DebugAppCheckProviderFactory installed")
            } else {
                // في الإطلاق: Play Integrity
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.d("AppCheck", "PlayIntegrityAppCheckProviderFactory installed")
            }
        } catch (e: Exception) {
            Log.w("AppCheck", "Failed to install AppCheck provider: ${e.message}")
        }
        // ============================

        // الثيم
        val themePrefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val nightMode = themePrefs.getInt("NightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        Log.d("ThemeCheck", "MyApp onCreate: Loading theme mode: $nightMode")
        AppCompatDelegate.setDefaultNightMode(nightMode)

        // جلسات المستخدم
        val sessionPrefs = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
        val wasBackgrounded = sessionPrefs.getBoolean(KEY_WAS_BACKGROUNDED, false)
        if (wasBackgrounded) {
            Log.d("Session", "Cold start after background → force signOut()")
            FirebaseAuth.getInstance().signOut()
            sessionPrefs.edit().putBoolean(KEY_WAS_BACKGROUNDED, false).apply()
        }

        sessionPrefs.edit().putLong(KEY_LAST_ACTIVE_MS, System.currentTimeMillis()).apply()
        registerActivityLifecycleCallbacks(this)
    }

    // بديل آمن لـ BuildConfig.DEBUG
    private fun isDebugBuild(): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    // ===== ActivityLifecycleCallbacks =====
    override fun onActivityStarted(activity: Activity) {
        if (startedCount == 0) {
            val sp = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
            sp.edit()
                .putBoolean(KEY_WAS_BACKGROUNDED, false)
                .putLong(KEY_LAST_ACTIVE_MS, System.currentTimeMillis())
                .apply()
            Log.d("Session", "App in foreground")
        }
        startedCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount--
        if (!activity.isChangingConfigurations && startedCount == 0) {
            val sp = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
            sp.edit()
                .putBoolean(KEY_WAS_BACKGROUNDED, true)
                .putLong(KEY_LAST_ACTIVE_MS, System.currentTimeMillis())
                .apply()
            Log.d("Session", "App in background")
        }
    }

    override fun onActivityResumed(activity: Activity) {
        val sp = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
        sp.edit().putLong(KEY_LAST_ACTIVE_MS, System.currentTimeMillis()).apply()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        const val SESSION_TIMEOUT_MS: Long = 15L * 60L * 1000L // 15 دقيقة
        const val KEY_WAS_BACKGROUNDED = "was_backgrounded"
        const val KEY_LAST_ACTIVE_MS = "last_active_time"
    }
}
