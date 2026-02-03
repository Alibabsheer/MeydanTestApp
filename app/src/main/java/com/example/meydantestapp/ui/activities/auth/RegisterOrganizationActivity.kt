package com.example.meydantestapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.meydantestapp.databinding.ActivityRegisterOrganizationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.*
import androidx.lifecycle.ViewModelProvider
import android.view.View

class RegisterOrganizationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterOrganizationBinding
    private lateinit var registerOrganizationViewModel: RegisterOrganizationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        // استرجاع الوضع الليلي
        val sharedPreferences = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val nightMode = sharedPreferences.getInt("NightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(nightMode)

        super.onCreate(savedInstanceState)
        binding = ActivityRegisterOrganizationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        registerOrganizationViewModel = ViewModelProvider(this).get(RegisterOrganizationViewModel::class.java)

        // Observe isLoading LiveData
        registerOrganizationViewModel.isLoading.observe(this) {
            binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
            binding.createAccountButton.isEnabled = !it
            binding.orgNameInput.isEnabled = !it
            binding.activityTypeInput.isEnabled = !it
            binding.emailInput.isEnabled = !it
            binding.passwordInput.isEnabled = !it
            binding.confirmPasswordInput.isEnabled = !it
            binding.confirmDataCheck.isEnabled = !it
        }

        // Observe registrationResult LiveData
        registerOrganizationViewModel.registrationResult.observe(this) {
            it.onSuccess {
                Toast.makeText(this, "تم تسجيل المؤسسة بنجاح. تم إرسال رسالة تحقق إلى بريدك الإلكتروني.", Toast.LENGTH_LONG).show()
                val intent = Intent(this, OrganizationDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }.onFailure {
                Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
            }
        }

        // زر الرجوع
        binding.backButton.setOnClickListener {
            finish()
        }

        // تفعيل الزر عند تحديد المربع
        binding.createAccountButton.isEnabled = false
        binding.confirmDataCheck.setOnCheckedChangeListener { _, isChecked ->
            binding.createAccountButton.isEnabled = isChecked
        }

        // عند الضغط على إنشاء حساب
        binding.createAccountButton.setOnClickListener {
            val orgName = binding.orgNameInput.text.toString().trim()
            val activityType = binding.activityTypeInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
            val confirmPassword = binding.confirmPasswordInput.text.toString()

            if (password != confirmPassword) {
                Toast.makeText(this, "كلمتا المرور غير متطابقتين", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerOrganizationViewModel.registerOrganization(
                orgName,
                activityType,
                email,
                password,
                confirmPassword
            )
        }
    }
}