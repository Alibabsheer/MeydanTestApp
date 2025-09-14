package com.example.meydantestapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.meydantestapp.databinding.ActivityProjectDetailsBinding
import com.example.meydantestapp.utils.Constants
import com.google.android.material.bottomsheet.BottomSheetDialog

class ProjectDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectDetailsBinding
    private var organizationId: String? = null
    private var projectId: String? = null
    private var projectName: String? = null
    private val vm: ProjectDetailsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1) استلام القيم من الشاشة السابقة (المفاتيح الرسمية + توافق قديم)
        organizationId = intent.getStringExtra(Constants.EXTRA_ORGANIZATION_ID)
            ?: intent.getStringExtra("organizationId")
        projectId = intent.getStringExtra(Constants.EXTRA_PROJECT_ID)
            ?: intent.getStringExtra("projectId")
        projectName = intent.getStringExtra(Constants.EXTRA_PROJECT_NAME)
            ?: intent.getStringExtra("projectName")

        binding.projectNameText.text = projectName ?: "--"
        binding.fabAddReport.visibility = View.VISIBLE

        binding.backButton.setOnClickListener { finish() }

        vm.initialize(projectId, organizationId)
        vm.organizationId.observe(this) { organizationId = it }
        vm.projectDetails.observe(this) { details ->
            if (projectName.isNullOrBlank()) {
                val pn = (details?.get("projectName") ?: details?.get("name")) as? String
                if (!pn.isNullOrBlank()) {
                    projectName = pn
                    binding.projectNameText.text = pn
                }
            }
        }
        vm.errorMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        // فتح قائمة إنشاء تقرير
        binding.fabAddReport.setOnClickListener { showReportOptionsSheet() }

        // بطاقات التنقل
        binding.dailyReportsCard.setOnClickListener {
            if (projectId.isNullOrBlank() || organizationId.isNullOrBlank()) {
                Toast.makeText(this, "بيانات المشروع غير مكتملة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, DailyReportsActivity::class.java)
            // مفاتيح موحدة
            i.putExtra(Constants.EXTRA_PROJECT_ID, projectId)
            i.putExtra(Constants.EXTRA_PROJECT_NAME, projectName)
            i.putExtra(Constants.EXTRA_ORGANIZATION_ID, organizationId)
            // مفاتيح توافق قديم
            i.putExtra("projectId", projectId)
            i.putExtra("projectName", projectName)
            i.putExtra("organizationId", organizationId)
            startActivity(i)
        }

        binding.tasksCard.setOnClickListener {
            if (projectId.isNullOrBlank() || organizationId.isNullOrBlank()) {
                Toast.makeText(this, "بيانات المشروع غير مكتملة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, ProjectTasksActivity::class.java)
            // مفاتيح موحدة
            i.putExtra(Constants.EXTRA_PROJECT_ID, projectId)
            i.putExtra(Constants.EXTRA_PROJECT_NAME, projectName)
            i.putExtra(Constants.EXTRA_ORGANIZATION_ID, organizationId)
            // مفاتيح توافق قديم
            i.putExtra("projectId", projectId)
            i.putExtra("projectName", projectName)
            i.putExtra("organizationId", organizationId)
            startActivity(i)
        }
    }

    private fun showReportOptionsSheet() {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_report_options, null, false)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.optionDailyReport)?.setOnClickListener {
            dialog.dismiss()
            if (projectId.isNullOrBlank() || organizationId.isNullOrBlank()) {
                Toast.makeText(this, "بيانات المشروع غير مكتملة", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, CreateDailyReportActivity::class.java)
            // مفاتيح موحدة
            i.putExtra(Constants.EXTRA_PROJECT_ID, projectId)
            i.putExtra(Constants.EXTRA_PROJECT_NAME, projectName)
            i.putExtra(Constants.EXTRA_ORGANIZATION_ID, organizationId)
            // مفاتيح توافق قديم
            i.putExtra("projectId", projectId)
            i.putExtra("projectName", projectName)
            i.putExtra("organizationId", organizationId)
            startActivity(i)
        }

        dialog.show()
    }

    // لم يعد النشاط يحتوي على منطق جلب المشروع أو تحديد المؤسسة؛ يتم ذلك عبر الـ ViewModel
}
