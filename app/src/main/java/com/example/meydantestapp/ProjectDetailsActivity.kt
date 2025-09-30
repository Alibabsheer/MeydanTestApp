package com.example.meydantestapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.meydantestapp.databinding.ActivityProjectDetailsBinding
import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.FirestoreTimestampConverter
import com.example.meydantestapp.utils.migrateTimestampIfNeeded
import com.example.meydantestapp.utils.toProjectSafe
import com.example.meydantestapp.utils.toDisplayDateString
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
class ProjectDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectDetailsBinding
    private var organizationId: String? = null
    private var projectId: String? = null
    private var projectName: String? = null
    private var selectedProject: Project? = null
    private val db = FirebaseFirestore.getInstance()
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

        // 2) إن لم تصل organizationId من الشاشة السابقة، نحاول تحديدها تلقائيًا
        if (organizationId.isNullOrBlank()) {
            resolveOrganizationIdForCurrentUser(
                onResolved = { orgId ->
                    organizationId = orgId
                    maybeLoadProject()
                },
                onFail = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            )
        } else {
            maybeLoadProject()
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

    private fun maybeLoadProject() {
        val pid = projectId
        val oid = organizationId
        if (pid.isNullOrBlank() || oid.isNullOrBlank()) {
            // نعرض الاسم فقط إن وُجد، لكن لا نجلب التفاصيل بدون معرفين
            return
        }
        val projectRef = db.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(oid)
            .collection(Constants.COLLECTION_PROJECTS)
            .document(pid)

        projectRef.get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Log.w("ProjectDetails", "Project document not found for id=$pid")
                    binding.startDateText.text = ""
                    binding.endDateText.text = ""
                    selectedProject = null
                    return@addOnSuccessListener
                }

                val data = doc.data ?: emptyMap<String, Any?>()
                val startAny = data["startDate"]
                val endAny = data["endDate"]

                Log.d("ProjectDetails", "Raw project dates start=$startAny end=$endAny")

                val startTs = FirestoreTimestampConverter.fromAny(startAny)
                val endTs = FirestoreTimestampConverter.fromAny(endAny)

                doc.migrateTimestampIfNeeded("startDate", startAny, startTs)
                doc.migrateTimestampIfNeeded("endDate", endAny, endTs)

                Log.i(
                    "ProjectDetails",
                    "Resolved project dates → start=${startTs?.seconds} end=${endTs?.seconds}"
                )

                binding.startDateText.text = startTs.toDisplayDateString()
                binding.endDateText.text = endTs.toDisplayDateString()

                selectedProject = doc.toProjectSafe(startTs, endTs)
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل في جلب تفاصيل المشروع", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resolveOrganizationIdForCurrentUser(onResolved: (String) -> Unit, onFail: (String) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            onFail("لم يتم تسجيل الدخول")
            return
        }
        // 1) المؤسسة = uid
        db.collection(Constants.COLLECTION_ORGANIZATIONS).document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onResolved(uid)
                } else {
                    // 2) مستخدم تابع داخل organizations/*/users
                    db.collectionGroup(Constants.COLLECTION_USERS)
                        .whereEqualTo("uid", uid)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { q ->
                            val orgId = q.documents.firstOrNull()?.reference?.parent?.parent?.id
                            if (orgId != null) onResolved(orgId) else {
                                // 3) احتياطي: userslogin/{uid}
                                db.collection(Constants.COLLECTION_USERSLOGIN).document(uid).get()
                                    .addOnSuccessListener { mirror ->
                                        val mirrorOrgId = mirror.getString("organizationId")
                                        if (!mirrorOrgId.isNullOrBlank()) onResolved(mirrorOrgId) else onFail("تعذر تحديد مؤسسة المستخدم.")
                                    }
                                    .addOnFailureListener { onFail("فشل في تحديد مؤسسة المستخدم.") }
                            }
                        }
                        .addOnFailureListener { onFail("فشل في تحديد مؤسسة المستخدم.") }
                }
            }
            .addOnFailureListener { onFail("فشل في تحديد مؤسسة المستخدم.") }
    }

}
