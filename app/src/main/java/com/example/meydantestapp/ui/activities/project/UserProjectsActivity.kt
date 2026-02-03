package com.example.meydantestapp

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.data.ProjectsRepository
import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.ProjectNavigationValidator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UserProjectsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserProjectsAdapter

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var organizationId: String? = null
    private val projectItems = mutableListOf<Pair<String, String>>() // (projectId, projectName)
    private var initLoadOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_projects)

        findViewById<ImageView>(R.id.backButton)?.setOnClickListener { finish() }

        recyclerView = findViewById(R.id.userProjectsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = UserProjectsAdapter(projectItems.map { it.second }) { projectName ->
            val selected = projectItems.firstOrNull { it.second == projectName }
            val projectId = ProjectNavigationValidator.sanitize(selected?.first)
            if (projectId == null) {
                Toast.makeText(
                    this,
                    "تعذر فتح تفاصيل المشروع بدون معرف صالح.",
                    Toast.LENGTH_SHORT
                ).show()
                return@UserProjectsAdapter
            }
            val intent = Intent(this, ProjectDetailsActivity::class.java)
            intent.putExtra(Constants.EXTRA_PROJECT_NAME, projectName)
            intent.putExtra(Constants.EXTRA_PROJECT_ID, projectId)
            // تمرير معرّف المؤسسة صراحة (لضمان سريان الشعار الصحيح لاحقًا داخل سلسلة الشاشات)
            organizationId?.let {
                intent.putExtra(Constants.EXTRA_ORGANIZATION_ID, it)
                intent.putExtra("organizationId", it) // توافقًا مع أي مفاتيح قديمة
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // محاولة قراءة orgId من Intent (مفتاح رسمي + توافق قديم)
        organizationId = intent.getStringExtra(Constants.EXTRA_ORGANIZATION_ID)
            ?: intent.getStringExtra("organizationId")

    }

    override fun onStart() {
        super.onStart()
        if (!initLoadOnce) {
            initLoadOnce = true
            val t0 = SystemClock.elapsedRealtime()
            lifecycleScope.launch {
                try {
                    val resolvedOrgId = organizationId ?: resolveOrganizationIdForCurrentUser()
                        ?: run {
                            Toast.makeText(
                                this@UserProjectsActivity,
                                "تعذّر تحميل المشاريع الآن",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                    organizationId = resolvedOrgId

                    val projects = withContext(Dispatchers.IO) {
                        ProjectsRepository(db, resolvedOrgId).fetchProjectsFirstPage(20)
                    }

                    projectItems.clear()
                    projectItems.addAll(
                        projects.map { project ->
                            val name = project.projectName.ifBlank {
                                project.addressText?.takeIf { it.isNotBlank() }
                                    ?: "مشروع بدون اسم"
                            }
                            project.projectId to name
                        }
                    )

                    updateAdapter()

                    val elapsed = SystemClock.elapsedRealtime() - t0
                    Log.d("ProjectsPerf", "First page loaded in ${elapsed}ms, count=${projectItems.size}")

                    if (projectItems.isEmpty()) {
                        Toast.makeText(this@UserProjectsActivity, "لا توجد مشاريع متاحة.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ProjectsPerf", "Failed to load user projects", e)
                    Toast.makeText(
                        this@UserProjectsActivity,
                        "تعذّر تحميل المشاريع الآن",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun resolveOrganizationIdForCurrentUser(): String? {
        val uid = auth.currentUser?.uid ?: return null

        val affiliatedSnapshot = db.collectionGroup(Constants.COLLECTION_USERS)
            .whereEqualTo("uid", uid)
            .limit(1)
            .get()
            .await()

        val orgId = affiliatedSnapshot.documents
            .firstOrNull()
            ?.reference
            ?.parent
            ?.parent
            ?.id

        if (!orgId.isNullOrBlank()) {
            return orgId
        }

        val mirrorDoc = db.collection(Constants.COLLECTION_USERSLOGIN)
            .document(uid)
            .get()
            .await()

        return mirrorDoc.getString("organizationId")
    }

    private fun updateAdapter() {
        adapter = UserProjectsAdapter(projectItems.map { it.second }) { projectName ->
            val selected = projectItems.firstOrNull { it.second == projectName }
            val projectId = ProjectNavigationValidator.sanitize(selected?.first)
            if (projectId == null) {
                Toast.makeText(
                    this,
                    "تعذر فتح تفاصيل المشروع بدون معرف صالح.",
                    Toast.LENGTH_SHORT
                ).show()
                return@UserProjectsAdapter
            }
            val intent = Intent(this, ProjectDetailsActivity::class.java)
            intent.putExtra(Constants.EXTRA_PROJECT_NAME, projectName)
            intent.putExtra(Constants.EXTRA_PROJECT_ID, projectId)
            organizationId?.let {
                intent.putExtra(Constants.EXTRA_ORGANIZATION_ID, it)
                intent.putExtra("organizationId", it)
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }
}
