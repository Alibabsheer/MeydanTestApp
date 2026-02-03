package com.example.meydantestapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import com.example.meydantestapp.databinding.ActivityTypeSelectionBinding

class TypeSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTypeSelectionBinding
    private lateinit var viewModel: TypeSelectionViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPreferences = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val nightMode = sharedPreferences.getInt("NightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(nightMode)

        super.onCreate(savedInstanceState)
        binding = ActivityTypeSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this).get(TypeSelectionViewModel::class.java)

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.orgAccountButton.setOnClickListener {
            viewModel.onOrganizationAccountClicked()
        }

        binding.userAccountButton.setOnClickListener {
            viewModel.onUserAccountClicked()
        }

        // إضافة listeners للبطاقات الجديدة
        binding.orgAccountCard.setOnClickListener {
            viewModel.onOrganizationAccountClicked()
        }

        binding.userAccountCard.setOnClickListener {
            viewModel.onUserAccountClicked()
        }

        viewModel.navigateToRegisterOrganization.observe(this) { navigate ->
            if (navigate) {
                val intent = Intent(this, RegisterOrganizationActivity::class.java)
                startActivity(intent)
                viewModel.navigationComplete()
            }
        }

        viewModel.navigateToJoinCodeEntry.observe(this) { navigate ->
            if (navigate) {
                val intent = Intent(this, JoinCodeEntryActivity::class.java)
                startActivity(intent)
                viewModel.navigationComplete()
            }
        }
    }
}


