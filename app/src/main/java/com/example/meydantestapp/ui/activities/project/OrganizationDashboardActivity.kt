package com.example.meydantestapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.meydantestapp.databinding.ActivityOrganizationDashboardBinding

class OrganizationDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrganizationDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrganizationDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // زر الرجوع
        binding.backButton.setOnClickListener {
            finish()
        }

        // زر عرض وإدارة المشاريع
        binding.manageProjectsButton.setOnClickListener {
            val intent = Intent(this, ProjectsActivity::class.java)
            startActivity(intent)
        }

        // زر إعدادات الحساب
        binding.accountSettingsButton.setOnClickListener {
            val intent = Intent(this, AccountSettingsActivity::class.java)
            startActivity(intent)
        }
    }

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
}
