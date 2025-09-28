package com.example.meydantestapp

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.data.model.Project
import com.example.meydantestapp.utils.Constants
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProjectsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProjectAdapter
    private lateinit var fabAddProject: FloatingActionButton

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // نخزن orgId الحالي لكي نمرّره دائمًا لشاشة التفاصيل
    private var currentOrganizationId: String? = null

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
                val intent = Intent(this, ProjectSettingsActivity::class.java)
                intent.putExtra(Constants.EXTRA_PROJECT_ID, project.projectId)
                startActivity(intent)
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

    override fun onResume() {
        super.onResume()
        fetchProjects()
    }

    private fun fetchProjects() {
        val currentUser = auth.currentUser ?: return
        val userId = currentUser.uid

        // هل المستخدم مؤسسة مالكة؟
        db.collection(Constants.COLLECTION_ORGANIZATIONS).document(userId).get()
            .addOnSuccessListener { orgDoc ->
                if (orgDoc.exists()) {
                    // المؤسسة نفسها
                    currentOrganizationId = userId
                    fetchOrganizationProjects(userId)
                } else {
                    // مستخدم تابع → نحدّد orgId أولاً ثم نجلب مشاريعها
                    fetchAffiliatedUserProjects(userId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل في تحديد نوع المستخدم", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchOrganizationProjects(orgId: String) {
        db.collection(Constants.COLLECTION_ORGANIZATIONS).document(orgId)
            .collection(Constants.COLLECTION_PROJECTS).get()
            .addOnSuccessListener { snapshot ->
                val projects = snapshot.documents.map { Project.from(it) }
                updateProjectList(projects)
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل في جلب مشاريع المؤسسة", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchAffiliatedUserProjects(userId: String) {
        // userslogin/{uid} أو مجموعة users تحت المؤسسة
        db.collectionGroup(Constants.COLLECTION_USERS)
            .whereEqualTo("uid", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { q ->
                val orgId = q.documents.firstOrNull()?.reference?.parent?.parent?.id
                if (orgId != null) {
                    currentOrganizationId = orgId
                    fetchOrganizationProjects(orgId)
                } else {
                    // احتياطي: userslogin/{uid}
                    db.collection(Constants.COLLECTION_USERSLOGIN).document(userId).get()
                        .addOnSuccessListener { mirror ->
                            val mirrorOrgId = mirror.getString("organizationId")
                            if (!mirrorOrgId.isNullOrBlank()) {
                                currentOrganizationId = mirrorOrgId
                                fetchOrganizationProjects(mirrorOrgId)
                            } else {
                                Toast.makeText(this, "لم يتم العثور على المؤسسة المرتبطة للمستخدم", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "فشل في جلب بيانات المستخدم", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل في جلب بيانات المستخدم", Toast.LENGTH_SHORT).show()
            }
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
                val intent = Intent(this, ProjectSettingsActivity::class.java)
                intent.putExtra(Constants.EXTRA_PROJECT_ID, project.projectId)
                startActivity(intent)
            }
        )
        recyclerView.adapter = adapter
    }
}