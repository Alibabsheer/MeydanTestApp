package com.example.meydantestapp

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserProjectsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserProjectsAdapter

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var organizationId: String? = null
    private val projectItems = mutableListOf<Pair<String, String>>() // (projectId, projectName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_projects)

        findViewById<ImageView>(R.id.backButton)?.setOnClickListener { finish() }

        recyclerView = findViewById(R.id.userProjectsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = UserProjectsAdapter(projectItems.map { it.second }) { projectName ->
            val selected = projectItems.firstOrNull { it.second == projectName }
            val intent = Intent(this, ProjectDetailsActivity::class.java)
            intent.putExtra(Constants.EXTRA_PROJECT_NAME, projectName)
            intent.putExtra(Constants.EXTRA_PROJECT_ID, selected?.first)
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

        if (!organizationId.isNullOrBlank()) {
            loadProjects(organizationId!!)
        } else {
            resolveOrganizationIdForCurrentUser(
                onResolved = { orgId -> loadProjects(orgId) },
                onFail = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            )
        }
    }

    private fun resolveOrganizationIdForCurrentUser(onResolved: (String) -> Unit, onFail: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            onFail("لم يتم تسجيل الدخول")
            return
        }
        // البحث أولًا في المجلد المتداخل users ضمن organizations/{orgId}/users/{uid}
        db.collectionGroup(Constants.COLLECTION_USERS)
            .whereEqualTo("uid", uid)
            .limit(1)
            .get()
            .addOnSuccessListener { q ->
                val orgId = q.documents.firstOrNull()?.reference?.parent?.parent?.id
                if (orgId != null) {
                    organizationId = orgId
                    onResolved(orgId)
                } else {
                    // خطة بديلة: مرآة الدخول العلوية userslogin/{uid}
                    db.collection(Constants.COLLECTION_USERSLOGIN).document(uid).get()
                        .addOnSuccessListener { mirror ->
                            val mirrorOrgId = mirror.getString("organizationId")
                            if (!mirrorOrgId.isNullOrBlank()) {
                                organizationId = mirrorOrgId
                                onResolved(mirrorOrgId)
                            } else {
                                onFail("لم يتم العثور على المؤسسة المرتبطة بالحساب")
                            }
                        }
                        .addOnFailureListener { e -> onFail("فشل في جلب المؤسسة: ${e.message}") }
                }
            }
            .addOnFailureListener { e -> onFail("فشل في جلب المؤسسة: ${e.message}") }
    }

    private fun loadProjects(orgId: String) {
        db.collection(Constants.COLLECTION_ORGANIZATIONS)
            .document(orgId)
            .collection(Constants.COLLECTION_PROJECTS)
            .get()
            .addOnSuccessListener { result ->
                projectItems.clear()
                for (doc in result.documents) {
                    val name = doc.getString("projectName")
                        ?: doc.getString("name")
                        ?: doc.getString("title")
                        ?: "مشروع بدون اسم"
                    projectItems.add(doc.id to name)
                }
                // تحديث الأسماء في الأدابتر مع الاحتفاظ بالـ callback نفسه
                adapter = UserProjectsAdapter(projectItems.map { it.second }) { projectName ->
                    val selected = projectItems.firstOrNull { it.second == projectName }
                    val intent = Intent(this, ProjectDetailsActivity::class.java)
                    intent.putExtra(Constants.EXTRA_PROJECT_NAME, projectName)
                    intent.putExtra(Constants.EXTRA_PROJECT_ID, selected?.first)
                    organizationId?.let {
                        intent.putExtra(Constants.EXTRA_ORGANIZATION_ID, it)
                        intent.putExtra("organizationId", it)
                    }
                    startActivity(intent)
                }
                recyclerView.adapter = adapter

                if (projectItems.isEmpty()) {
                    Toast.makeText(this, "لا توجد مشاريع متاحة.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "فشل في جلب المشاريع: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
