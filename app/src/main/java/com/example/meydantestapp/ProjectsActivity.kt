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
import com.example.meydantestapp.data.model.Project
import com.example.meydantestapp.utils.Constants
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ProjectsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProjectAdapter
    private lateinit var fabAddProject: FloatingActionButton

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // نخزن orgId الحالي لكي نمرّره دائمًا لشاشة التفاصيل
    private var currentOrganizationId: String? = null
    private var initLoadOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_projects)

        recyclerView = findViewById(R.id.recyclerViewProjects)
        fabAddProject = findViewById(R.id.fabAddProject)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ProjectAdapter(emptyList(),
            onViewClick = { project ->
                val intent = Intent(this, ProjectDetailsActivity::class.java)
                intent.putExtra(Constants.EXTRA_PROJECT_NAME, project.projectName)
                intent.putExtra(Constants.EXTRA_PROJECT_ID, project.projectId)
                currentOrganizationId?.let { intent.putExtra(Constants.EXTRA_ORGANIZATION_ID, it) }
                startActivity(intent)
            },
            onEditClick = { project ->
                openProjectSettings(project.projectId, "ProjectsActivity:initAdapter")
            }
        )

        recyclerView.adapter = adapter

        fabAddProject.setOnClickListener {
            val intent = Intent(this, CreateNewProjectActivity::class.java)
            startActivity(intent)
        }

        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        if (!initLoadOnce) {
            initLoadOnce = true
            val t0 = SystemClock.elapsedRealtime()
            lifecycleScope.launch {
                try {
                    val currentUser = auth.currentUser ?: run {
                        Toast.makeText(
                            this@ProjectsActivity,
                            "تعذّر تحميل المشاريع الآن",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    val organizationId = resolveOrganizationId(currentUser.uid)
                        ?: run {
                            Toast.makeText(
                                this@ProjectsActivity,
                                "تعذّر تحميل المشاريع الآن",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                    currentOrganizationId = organizationId

                    val projects = withContext(Dispatchers.IO) {
                        ProjectsRepository(db, organizationId).fetchProjectsFirstPage(20)
                    }

                    updateProjectList(projects)

                    val elapsed = SystemClock.elapsedRealtime() - t0
                    Log.d("ProjectsPerf", "First page loaded in ${elapsed}ms, count=${projects.size}")
                } catch (e: Exception) {
                    Log.e("ProjectsPerf", "Failed to load first page", e)
                    Toast.makeText(
                        this@ProjectsActivity,
                        "تعذّر تحميل المشاريع الآن",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private suspend fun resolveOrganizationId(userId: String): String? {
        val orgDoc = db.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(userId)
            .get()
            .await()

        if (orgDoc.exists()) {
            return userId
        }

        val affiliatedSnapshot = db.collectionGroup(Constants.COLLECTION_USERS)
            .whereEqualTo("uid", userId)
            .limit(1)
            .get()
            .await()

        val affiliatedOrgId = affiliatedSnapshot.documents
            .firstOrNull()
            ?.reference
            ?.parent
            ?.parent
            ?.id

        if (!affiliatedOrgId.isNullOrBlank()) {
            return affiliatedOrgId
        }

        val mirrorDoc = db.collection(Constants.COLLECTION_USERSLOGIN)
            .document(userId)
            .get()
            .await()

        return mirrorDoc.getString("organizationId")
    }

    private fun updateProjectList(projects: List<Project>) {
        adapter = ProjectAdapter(projects,
            onViewClick = { project ->
                val intent = Intent(this, ProjectDetailsActivity::class.java)
                intent.putExtra(Constants.EXTRA_PROJECT_NAME, project.projectName)
                intent.putExtra(Constants.EXTRA_PROJECT_ID, project.projectId)
                currentOrganizationId?.let { intent.putExtra(Constants.EXTRA_ORGANIZATION_ID, it) }
                startActivity(intent)
            },
            onEditClick = { project ->
                openProjectSettings(project.projectId, "ProjectsActivity:updateProjectList")
            }
        )
        recyclerView.adapter = adapter
    }

    private fun openProjectSettings(projectId: String?, source: String) {
        if (projectId.isNullOrBlank()) {
            Log.e(TAG_OPEN_SETTINGS, "Attempted to open settings from $source without projectId")
            Toast.makeText(
                this,
                "تعذر فتح إعدادات المشروع بدون معرف مشروع",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        Log.d(TAG_OPEN_SETTINGS, "Opening ProjectSettingsActivity with id=$projectId from $source")
        val intent = Intent(this, ProjectSettingsActivity::class.java)
            .putExtra(Constants.EXTRA_PROJECT_ID, projectId)
        startActivity(intent)
    }

    private companion object {
        private const val TAG_OPEN_SETTINGS = "OpenProjectSettings"
    }
}
