package com.example.meydantestapp

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.example.meydantestapp.databinding.ActivityMainBinding
import com.example.meydantestapp.utils.Constants
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var loginViewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val savedMode = sharedPreferences.getInt("NightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val currentMode = AppCompatDelegate.getDefaultNightMode()

        if (savedMode != currentMode) {
            AppCompatDelegate.setDefaultNightMode(savedMode)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loginViewModel = ViewModelProvider(this)[LoginViewModel::class.java]

        enforceSessionTimeout()
        updateLastActiveStamp()

        loginViewModel.isLoading.observe(this) {
            binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
            binding.loginButton.isEnabled = !it
            binding.emailEditText.isEnabled = !it
            binding.passwordEditText.isEnabled = !it
            binding.createAccountText.isEnabled = !it
        }

        loginViewModel.loginResult.observe(this) {
            it.onSuccess { destination ->
                when (destination) {
                    is LoginDestination.OrganizationDashboard -> {
                        startActivity(Intent(this, OrganizationDashboardActivity::class.java))
                        finish()
                    }
                    is LoginDestination.UserProjects -> {
                        val intent = Intent(this, UserProjectsActivity::class.java)
                        intent.putExtra(Constants.EXTRA_ORGANIZATION_ID, destination.organizationId)
                        startActivity(intent)
                        finish()
                    }
                }
            }.onFailure { e ->
                Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        }

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()
            loginViewModel.login(email, password)
        }

        binding.createAccountText.setOnClickListener {
            startActivity(Intent(this, TypeSelectionActivity::class.java))
        }

        loginViewModel.checkUserSession()
    }

    // --------- مهلة الخمول ---------
    override fun onUserInteraction() {
        super.onUserInteraction()
        updateLastActiveStamp()
    }

    override fun onResume() {
        super.onResume()
        updateLastActiveStamp()
    }

    private fun updateLastActiveStamp() {
        val prefs = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
        prefs.edit().putLong(MyApp.KEY_LAST_ACTIVE_MS, System.currentTimeMillis()).apply()
    }

    private fun enforceSessionTimeout() {
        val prefs = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
        val last = prefs.getLong(MyApp.KEY_LAST_ACTIVE_MS, 0L)
        val now = System.currentTimeMillis()
        val timedOut = last != 0L && (now - last) > MyApp.SESSION_TIMEOUT_MS
        if (timedOut) {
            Log.d("Session", "Idle timeout exceeded → signOut()")
            FirebaseAuth.getInstance().signOut()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("LoginActivity", "onConfigurationChanged: " + newConfig.uiMode)
    }
}
