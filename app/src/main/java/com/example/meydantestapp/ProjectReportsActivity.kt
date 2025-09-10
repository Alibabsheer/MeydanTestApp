package com.example.meydantestapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meydantestapp.databinding.ActivityProjectReportsBinding
import com.example.meydantestapp.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ProjectReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectReportsBinding

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var organizationId: String? = null
    private lateinit var projectId: String
    private lateinit var projectName: String

    private var selectedProject: Project? = null

    private lateinit var reportsAdapter: DailyReportAdapter
    private val reportList = mutableListOf<DailyReport>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // استلام القيم من الشاشة السابقة (موحّد + توافق قديم)
        organizationId = intent.getStringExtra(Constants.EXTRA_ORGANIZATION_ID)
            ?: intent.getStringExtra("organizationId")
        projectId = intent.getStringExtra(Constants.EXTRA_PROJECT_ID)
            ?: intent.getStringExtra("projectId")
                    ?: ""
        projectName = intent.getStringExtra(Constants.EXTRA_PROJECT_NAME)
            ?: intent.getStringExtra("projectName")
                    ?: "--"

        if (projectId.isBlank()) {
            Toast.makeText(this, "لم يتم تمرير معرف المشروع", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        binding.titleText.text = "تقارير المشروع: $projectName"
        binding.backButton.setOnClickListener { finish() }

        // Recycler
        binding.reportsRecyclerView.layoutManager = LinearLayoutManager(this)
        reportsAdapter = DailyReportAdapter(
            reports = reportList,
            onItemClick = { report: DailyReport ->
                val i = Intent(this, ViewDailyReportActivity::class.java)
                i.putExtra("dailyReport", report)
                // نمرّر organizationId صراحةً لضمان تحميل شعار المؤسسة الصحيح
                i.putExtra(Constants.EXTRA_ORGANIZATION_ID, organizationId)
                i.putExtra("organizationId", organizationId) // توافق قديم
                i.putExtra(Constants.EXTRA_PROJECT_ID, projectId)
                i.putExtra(Constants.EXTRA_PROJECT_NAME, projectName)
                startActivity(i)
            },
            organizationId = organizationId,
            projectId = projectId,
            projectName = projectName
        )
        binding.reportsRecyclerView.adapter = reportsAdapter

        // زر إضافة تقرير جديد
        binding.fabAddReport.setOnClickListener {
            if (selectedProject == null) {
                Toast.makeText(this, "تفاصيل المشروع غير جاهزة بعد", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, CreateDailyReportActivity::class.java)
            i.putExtra(Constants.EXTRA_PROJECT_ID, selectedProject?.id)
            i.putExtra(Constants.EXTRA_PROJECT_NAME, selectedProject?.projectName ?: projectName)
            i.putExtra(Constants.EXTRA_ORGANIZATION_ID, organizationId)
            // توافق قديم
            i.putExtra("projectId", selectedProject?.id)
            i.putExtra("projectName", selectedProject?.projectName ?: projectName)
            i.putExtra("organizationId", organizationId)
            startActivity(i)
        }

        // إن لم يُمرّر orgId نحاول تحديده تلقائيًا (ينفع مع حساب المستخدم)
        if (organizationId.isNullOrBlank()) {
            resolveOrganizationIdForCurrentUser(
                onResolved = { orgId ->
                    organizationId = orgId
                    fetchProjectDetails(orgId, projectId)
                    fetchDailyReports(orgId, projectId)
                },
                onFail = { msg ->
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    finish()
                }
            )
        } else {
            fetchProjectDetails(organizationId!!, projectId)
            fetchDailyReports(organizationId!!, projectId)
        }
    }

    override fun onResume() {
        super.onResume()
        val oid = organizationId
        if (!oid.isNullOrBlank() && projectId.isNotBlank()) {
            fetchDailyReports(oid, projectId)
        }
    }

    private fun fetchProjectDetails(orgId: String, projId: String) {
        db.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(orgId)
            .collection(Constants.COLLECTION_PROJECTS)
            .document(projId)
            .get()
            .addOnSuccessListener { doc ->
                selectedProject = doc.toObject(Project::class.java)?.also { it.id = doc.id }
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل في جلب تفاصيل المشروع", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchDailyReports(orgId: String, projId: String) {
        db.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(orgId)
            .collection(Constants.COLLECTION_PROJECTS)
            .document(projId)
            .collection(Constants.SUBCOLLECTION_DAILY_REPORTS)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                reportList.clear()
                for (document in documents) {
                    val report = document.toObject(DailyReport::class.java)
                    report.id = document.id
                    reportList.add(report)
                }
                reportsAdapter.notifyDataSetChanged()
                binding.noReportsText.visibility = if (reportList.isEmpty()) View.VISIBLE else View.GONE
                binding.reportsRecyclerView.visibility = if (reportList.isEmpty()) View.GONE else View.VISIBLE
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل في تحميل التقارير اليومية.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resolveOrganizationIdForCurrentUser(onResolved: (String) -> Unit, onFail: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            onFail("لم يتم تسجيل الدخول")
            return
        }
        // 1) إذا كان الحساب مؤسسة (orgId = uid)
        db.collection(Constants.COLLECTION_ORGANIZATIONS).document(uid).get()
            .addOnSuccessListener { orgDoc ->
                if (orgDoc.exists()) {
                    onResolved(uid)
                } else {
                    // 2) مستخدم تابع داخل organizations/*/users
                    db.collectionGroup(Constants.COLLECTION_USERS)
                        .whereEqualTo("uid", uid)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { q ->
                            val orgId = q.documents.firstOrNull()?.reference?.parent?.parent?.id
                            if (!orgId.isNullOrBlank()) {
                                onResolved(orgId)
                            } else {
                                // 3) احتياطي: userslogin/{uid}
                                db.collection(Constants.COLLECTION_USERSLOGIN).document(uid).get()
                                    .addOnSuccessListener { mirror ->
                                        val mirrorOrgId = mirror.getString("organizationId")
                                        if (!mirrorOrgId.isNullOrBlank()) onResolved(mirrorOrgId) else onFail("تعذر تحديد مؤسسة المستخدم")
                                    }
                                    .addOnFailureListener { onFail("تعذر تحديد مؤسسة المستخدم") }
                            }
                        }
                        .addOnFailureListener { onFail("تعذر تحديد مؤسسة المستخدم") }
                }
            }
            .addOnFailureListener { onFail("تعذر تحديد مؤسسة المستخدم") }
    }
}
