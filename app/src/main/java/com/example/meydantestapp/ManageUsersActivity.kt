package com.example.meydantestapp

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meydantestapp.databinding.ActivityManageUsersBinding
import com.example.meydantestapp.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ManageUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageUsersBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var adapter: UsersAdapter
    private val usersList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.usersRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UsersAdapter(this, usersList)
        binding.usersRecyclerView.adapter = adapter

        binding.backButton.setOnClickListener { finish() }

        // Priority 1: orgId from intent
        val orgIdFromIntent = intent.getStringExtra(Constants.EXTRA_ORGANIZATION_ID)
        if (!orgIdFromIntent.isNullOrEmpty()) {
            Log.d("ManageUsers", "organizationId from Intent = $orgIdFromIntent")
            fetchOrganizationAndUsers(orgIdFromIntent)
            return
        }

        // Priority 2: resolve via userslogin (for affiliated admin pages if ever used)
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            Toast.makeText(this, "لم يتم التعرف على المستخدم الحالي", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection(Constants.COLLECTION_USERSLOGIN)
            .document(currentUid)
            .get()
            .addOnSuccessListener { loginDoc ->
                val orgId = loginDoc.getString("organizationId")
                if (orgId.isNullOrEmpty()) {
                    Toast.makeText(this, "تعذّر تحديد المؤسسة للمستخدم الحالي", Toast.LENGTH_LONG).show()
                } else {
                    fetchOrganizationAndUsers(orgId)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل تحديد المؤسسة للمستخدم الحالي", Toast.LENGTH_SHORT).show()
                Log.e("ManageUsers", "Error fetching userslogin", it)
            }
    }

    private fun fetchOrganizationAndUsers(organizationId: String) {
        usersList.clear()

        val orgRef = db.collection(Constants.COLLECTION_ORGANIZATIONS).document(organizationId)
        orgRef.get()
            .addOnSuccessListener { orgDoc ->
                val ownerId = orgDoc.getString("ownerId").orEmpty()
                val orgName = orgDoc.getString("organizationName").orEmpty()
                val orgEmail = orgDoc.getString("email") ?: ""

                // header row for organization
                usersList.add(
                    User(
                        uid = ownerId,
                        displayId = orgDoc.getString("displayId") ?: "",
                        name = if (orgName.isNotEmpty()) orgName else "اسم المؤسسة",
                        email = orgEmail,
                        organizationId = organizationId,
                        organizationName = orgName,
                        role = "صاحب المؤسسة",
                        accountType = "organization"
                    )
                )

                orgRef.collection(Constants.COLLECTION_USERS)
                    .get()
                    .addOnSuccessListener { result ->
                        for (doc in result) {
                            try {
                                val user = doc.toObject(User::class.java)
                                usersList.add(user)
                            } catch (e: Exception) {
                                Log.e("ManageUsers", "toObject(User) failed for ${doc.id}", e)
                            }
                        }
                        adapter.notifyDataSetChanged()
                        if (usersList.size <= 1) {
                            Toast.makeText(this, "لا يوجد مستخدمون تابعون للمؤسسة", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "فشل تحميل المستخدمين", Toast.LENGTH_SHORT).show()
                        Log.e("ManageUsers", "Error fetching org users", it)
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل تحميل بيانات المؤسسة", Toast.LENGTH_SHORT).show()
                Log.e("ManageUsers", "Error fetching organization", it)
            }
    }
}
