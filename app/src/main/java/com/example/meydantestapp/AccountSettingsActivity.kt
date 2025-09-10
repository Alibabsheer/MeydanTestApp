package com.example.meydantestapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.meydantestapp.databinding.ActivityAccountSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.meydantestapp.utils.Constants

class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountSettingsBinding
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var viewModel: AccountSettingsViewModel

    // لإيجاد المؤسسة وتمرير organizationId
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var isNameEditable = false
    private var isBusinessEditable = false
    private var isCodeEditable = false

    // تتبع التعديلات غير المحفوظة
    private var hasUnsavedChanges = false
    private var initialName: String = ""
    private var initialType: String = ""
    private var initialJoinCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this).get(AccountSettingsViewModel::class.java)

        // Image picker
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageUri = result.data?.data
                if (imageUri != null) {
                    binding.logoPreview.setImageURI(imageUri)
                    // رفع الشعار وحفظ رابطه داخل Firestore عبر الـ ViewModel
                    viewModel.uploadLogo(imageUri)
                    // رفع الشعار يتم حفظه مباشرةً، لذلك لا يعتبر تغييرًا غير محفوظ
                }
            }
        }

        // Listeners
        binding.backButton.setOnClickListener { confirmExitIfNeeded() }

        binding.orgNameLayout.setEndIconOnClickListener {
            isNameEditable = !isNameEditable
            binding.orgNameEditText.isEnabled = isNameEditable
        }

        binding.businessTypeLayout.setEndIconOnClickListener {
            isBusinessEditable = !isBusinessEditable
            binding.businessTypeEditText.isEnabled = isBusinessEditable
        }

        binding.editJoinCodeButton.setOnClickListener {
            isCodeEditable = !isCodeEditable
            binding.joinCodeEditText.isEnabled = isCodeEditable
        }

        binding.copyJoinCodeButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Join Code", binding.joinCodeEditText.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "تم نسخ الكود", Toast.LENGTH_SHORT).show()
        }

        binding.generateCodeButton.setOnClickListener {
            val newCode = viewModel.generateJoinCodeFromEmail()
            newCode?.let { binding.joinCodeEditText.setText(it) }
        }

        binding.uploadLogoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            imagePickerLauncher.launch(intent)
        }

        binding.saveChangesButton.setOnClickListener {
            val name = binding.orgNameEditText.text.toString()
            val businessType = binding.businessTypeEditText.text.toString()
            val joinCode = binding.joinCodeEditText.text.toString().uppercase()
            viewModel.saveAll(name, businessType, joinCode)
            hasUnsavedChanges = false
            Toast.makeText(this, "تم حفظ التغييرات", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.deleteAccountButton.setOnClickListener {
            viewModel.deleteOrganizationAndAccount { ok, err ->
                if (ok) {
                    Toast.makeText(this, "تم حذف حساب المؤسسة بنجاح.", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "فشل حذف الحساب: $err", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // زر "عرض وإدارة المستخدمين" — تمرير organizationId إلى ManageUsersActivity
        binding.manageUsersButton.setOnClickListener {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "تعذّر التعرف على المستخدم الحالي", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("organizations")
                .whereEqualTo("ownerId", uid)
                .limit(1)
                .get()
                .addOnSuccessListener { qs ->
                    if (!qs.isEmpty) {
                        val orgId = qs.documents.first().id
                        Log.d("BTN_CLICK", "manageUsersButton -> ManageUsersActivity (orgId=$orgId)")
                        val intent = Intent(this, ManageUsersActivity::class.java)
                        intent.putExtra(Constants.EXTRA_ORGANIZATION_ID, orgId)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "لا توجد مؤسسة مرتبطة بهذا الحساب.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "فشل تحديد المؤسسة.", Toast.LENGTH_SHORT).show()
                }
        }

        // رصد تغيّر الحقول لتحديد إن كانت هناك تغييرات غير محفوظة
        fun updateDirtyFlag() {
            val name = binding.orgNameEditText.text?.toString() ?: ""
            val type = binding.businessTypeEditText.text?.toString() ?: ""
            val code = binding.joinCodeEditText.text?.toString() ?: ""
            hasUnsavedChanges = (name != initialName) || (type != initialType) || (code != initialJoinCode)
        }
        binding.orgNameEditText.addTextChangedListener { updateDirtyFlag() }
        binding.businessTypeEditText.addTextChangedListener { updateDirtyFlag() }
        binding.joinCodeEditText.addTextChangedListener { updateDirtyFlag() }

        // اعتراض زر الرجوع بالنظام
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmExitIfNeeded()
            }
        })

        // Observers
        viewModel.organizationName.observe(this, Observer { value ->
            binding.orgNameEditText.setText(value)
            initialName = value ?: ""
            hasUnsavedChanges = false
        })
        viewModel.activityType.observe(this, Observer { value ->
            binding.businessTypeEditText.setText(value)
            initialType = value ?: ""
            hasUnsavedChanges = false
        })
        viewModel.joinCode.observe(this, Observer { value ->
            binding.joinCodeEditText.setText(value)
            initialJoinCode = value ?: ""
            hasUnsavedChanges = false
        })
        viewModel.logoUrl.observe(this, Observer { url ->
            if (url != null) {
                Glide.with(this).load(url).placeholder(R.drawable.default_logo).into(binding.logoPreview)
            } else {
                binding.logoPreview.setImageResource(R.drawable.default_logo)
            }
        })
        viewModel.message.observe(this, Observer { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        })
        viewModel.error.observe(this, Observer { err ->
            err?.let { Toast.makeText(this, "خطأ: $it", Toast.LENGTH_LONG).show() }
        })

        // Initial load
        viewModel.loadData()
    }

    private fun confirmExitIfNeeded() {
        if (!hasUnsavedChanges) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("لم يتم حفظ التغييرات")
            .setMessage("هل تود حفظ التغييرات قبل الرجوع؟")
            .setPositiveButton("نعم") { dialog, _ ->
                val name = binding.orgNameEditText.text.toString()
                val businessType = binding.businessTypeEditText.text.toString()
                val joinCode = binding.joinCodeEditText.text.toString().uppercase()
                viewModel.saveAll(name, businessType, joinCode)
                hasUnsavedChanges = false
                Toast.makeText(this, "تم حفظ التغييرات", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                finish()
            }
            .setNegativeButton("لا") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNeutralButton("إلغاء") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
