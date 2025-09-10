package com.example.meydantestapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.meydantestapp.databinding.ActivityProjectTasksBinding

class ProjectTasksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectTasksBinding
    private lateinit var projectId: String
    private lateinit var projectName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectId = intent.getStringExtra("projectId") ?: ""
        projectName = intent.getStringExtra("projectName") ?: "--"

        binding.titleText.text = "مهام المشروع: $projectName"

        binding.backButton.setOnClickListener {
            finish()
        }
    }
}

