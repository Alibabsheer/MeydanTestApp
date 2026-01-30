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
        // تم إزالة سلوك تسجيل الخروج التلقائي عند الخلفية لتحسين تجربة المستخدم
        // الاعتماد فقط على SESSION_TIMEOUT_MS للأمان
        sessionPrefs.edit().putLong(KEY_LAST_ACTIVE_MS, System.currentTimeMillis()).apply()
        registerActivityLifecycleCallbacks(this)

        initializeSecurityProvider()
    }

    override fun onActivityStarted(activity: Activity) {
        if (startedCount == 0) {
            val sp = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
            // تحديث وقت آخر نشاط عند عودة التطبيق للمقدمة
            sp.edit().putLong(KEY_LAST_ACTIVE_MS, System.currentTimeMillis()).apply()
        }
        startedCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount--
        if (!activity.isChangingConfigurations && startedCount == 0) {
            val sp = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
            // تحديث وقت آخر نشاط فقط، بدون تسجيل الخروج
            sp.edit().putLong(KEY_LAST_ACTIVE_MS, System.currentTimeMillis()).apply()
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
        // تم زيادة مهلة الخمول من 15 دقيقة إلى ساعة واحدة لتحسين تجربة المستخدم
        const val SESSION_TIMEOUT_MS: Long = 60L * 60L * 1000L // ساعة واحدة
        const val KEY_WAS_BACKGROUNDED = "was_backgrounded" // محفوظ للتوافق
        const val KEY_LAST_ACTIVE_MS = "last_active_time"
    }
}
