package com.example.meydantestapp

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UsersAdapter(
    private val context: Context,
    private val users: MutableList<User>,
    private val onListChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_card, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        val currentUid = auth.currentUser?.uid

        holder.userName.text = user.name

        if (user.accountType == "organization") {
            val emailLine = user.email.ifBlank { "" }
            val labelLine = "الحساب الرئيسي"
            holder.userEmail.text = listOf(emailLine, labelLine).filter { it.isNotBlank() }.joinToString("\n")
            holder.editPermissionsButton.visibility = View.GONE
            holder.deleteUserButton.visibility = View.GONE
            return
        }

        holder.userEmail.text = user.email

        if (user.uid == currentUid) {
            holder.editPermissionsButton.visibility = View.GONE
            holder.deleteUserButton.visibility = View.GONE
        } else {
            holder.editPermissionsButton.setOnClickListener {
                Toast.makeText(context, "ميزة تعديل الصلاحية قادمة قريبًا", Toast.LENGTH_SHORT).show()
            }

            holder.deleteUserButton.setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle("تأكيد الحذف")
                    .setMessage("هل أنت متأكد من حذف هذا المستخدم؟ سيتم الحذف من مجلد المؤسسة ومن userslogin.")
                    .setPositiveButton("حذف") { _, _ ->
                        val orgId = user.organizationId
                        if (orgId.isBlank()) {
                            Toast.makeText(context, "لا يمكن الحذف: organizationId غير موجود.", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        // 1) حذف من المسار الداخلي
                        db.collection(Constants.COLLECTION_ORGANIZATIONS)
                            .document(orgId)
                            .collection(Constants.COLLECTION_USERS)
                            .document(user.uid)
                            .delete()
                            .addOnSuccessListener {
                                // 2) حذف من المسار العلوي userslogin
                                db.collection(Constants.COLLECTION_USERSLOGIN)
                                    .document(user.uid)
                                    .delete()
                                    .addOnCompleteListener {
                                        // تحديث واجهة المستخدم: إزالة العنصر محليًا
                                        val idx = users.indexOfFirst { it.uid == user.uid }
                                        if (idx >= 0) {
                                            users.removeAt(idx)
                                            notifyItemRemoved(idx)
                                            onListChanged?.invoke()
                                        }
                                        Toast.makeText(context, "تم حذف المستخدم من المسارين", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(context, "حُذف من المؤسسة لكن فشل حذف userslogin", Toast.LENGTH_LONG).show()
                                    }
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "فشل في حذف المستخدم من المؤسسة", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
        }
    }

    override fun getItemCount(): Int = users.size

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userName: TextView = view.findViewById(R.id.userNameText)
        val userEmail: TextView = view.findViewById(R.id.userEmailText)
        val editPermissionsButton: Button = view.findViewById(R.id.editPermissionsButton)
        val deleteUserButton: Button = view.findViewById(R.id.deleteUserButton)
    }
}
