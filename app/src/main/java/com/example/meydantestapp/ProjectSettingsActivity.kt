package com.example.meydantestapp

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.example.meydantestapp.databinding.ActivityProjectSettingsBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.example.meydantestapp.Project
import com.example.meydantestapp.utils.ProjectLocationUtils

class ProjectSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectSettingsBinding

    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null

    private lateinit var selectLocationLauncher: ActivityResultLauncher<Intent>

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var projectId: String
    private var currentProjectWorkType: String? = null

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var hasUnsavedChanges = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewContractTableButton.isEnabled = false

        selectLocationLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val selectedAddress = data?.getStringExtra("address") ?: ""
                selectedLatitude = data?.getDoubleExtra("latitude", 0.0)
                selectedLongitude = data?.getDoubleExtra("longitude", 0.0)
                binding.projectLocationEditText.setText(selectedAddress)
                enableSaveButton()
            }
        }

        projectId = intent.getStringExtra("projectId") ?: ""
        if (projectId.isNotEmpty()) {
            binding.saveChangesButton.text = "حفظ التعديلات"
            binding.deleteProjectButton.visibility = View.VISIBLE
            binding.titleText.text = "تفاصيل المشروع"
            loadProjectData(projectId)
        } else {
            Toast.makeText(this, "خطأ: تم فتح شاشة تعديل المشروع بدون معرف مشروع.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.backButton.setOnClickListener {
            if (hasUnsavedChanges) {
                AlertDialog.Builder(this)
                    .setTitle("تنبيه")
                    .setMessage("لم تقم بحفظ التعديلات. هل تريد الخروج؟")
                    .setPositiveButton("نعم") { _, _ -> finish() }
                    .setNegativeButton("إلغاء", null)
                    .show()
            } else {
                finish()
            }
        }


        binding.projectNameLayout.setEndIconOnClickListener {
            toggleEditTextState(binding.projectNameEditText, true)
        }
        binding.projectLocationLayout.setEndIconOnClickListener {
            toggleEditTextState(binding.projectLocationEditText, true)
            binding.projectLocationEditText.setOnClickListener { navigateToSelectLocation() }
        }
        binding.startDateLayout.setEndIconOnClickListener {
            toggleEditTextState(binding.startDateEditText, true)
            binding.startDateEditText.setOnClickListener { showDatePickerDialog(binding.startDateEditText) }
        }
        binding.endDateLayout.setEndIconOnClickListener {
            toggleEditTextState(binding.endDateEditText, true)
            binding.endDateEditText.setOnClickListener { showDatePickerDialog(binding.endDateEditText) }
        }
        binding.contractValueLayout.setEndIconOnClickListener {
            if (currentProjectWorkType != "جدول كميات" && currentProjectWorkType != "مقطوعية") {
                toggleEditTextState(binding.contractValueEditText, true)
            } else {
                Toast.makeText(this, "لا يمكن تعديل قيمة العقد يدوياً لهذا المشروع. القيمة محسوبة من الجدول.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.projectNameEditText.addTextChangedListener {
            hasUnsavedChanges = true
            enableSaveButton()
        }

        binding.projectLocationEditText.addTextChangedListener {
            hasUnsavedChanges = true
            enableSaveButton()
        }

        binding.startDateEditText.addTextChangedListener {
            hasUnsavedChanges = true
            enableSaveButton()
        }

        binding.endDateEditText.addTextChangedListener {
            hasUnsavedChanges = true
            enableSaveButton()
        }

        binding.contractValueEditText.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (binding.contractValueEditText.isEnabled && s.toString() != current) {
                    binding.contractValueEditText.removeTextChangedListener(this)
                    val cleanString = s.toString().replace(",", "")
                    val parsed = cleanString.toDoubleOrNull()
                    val formatted = parsed?.let { NumberFormat.getInstance(Locale.US).format(it) } ?: ""
                    current = formatted
                    binding.contractValueEditText.setText(formatted)
                    binding.contractValueEditText.setSelection(formatted.length)
                    binding.contractValueEditText.addTextChangedListener(this)
                    hasUnsavedChanges = true
                    enableSaveButton()
                }
            }
        })

        binding.viewContractTableButton.setOnClickListener {
            navigateToContractTable()
        }

        binding.saveChangesButton.setOnClickListener {
            saveProjectChangesToFirestore()
        }

        binding.deleteProjectButton.setOnClickListener {
            confirmAndDeleteProject()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::projectId.isInitialized && projectId.isNotEmpty()) {
            loadProjectData(projectId)
        }
    }

    private fun enableSaveButton() {
        val name = binding.projectNameEditText.text.toString().trim()
        val location = binding.projectLocationEditText.text.toString().trim()
        val startStr = binding.startDateEditText.text.toString().trim()
        val endStr = binding.endDateEditText.text.toString().trim()
        binding.saveChangesButton.isEnabled = name.isNotEmpty() && location.isNotEmpty() && startStr.isNotEmpty() && endStr.isNotEmpty()
    }

    private fun toggleEditTextState(editText: EditText, enable: Boolean) {
        editText.isEnabled = enable
        editText.isFocusableInTouchMode = enable
        editText.isClickable = enable
        if (enable) editText.requestFocus()
    }

    private fun navigateToSelectLocation() {
        val intent = Intent(this, SelectLocationActivity::class.java)
        selectedLatitude?.let { intent.putExtra("latitude", it) }
        selectedLongitude?.let { intent.putExtra("longitude", it) }
        selectLocationLauncher.launch(intent)
    }

    private fun loadProjectData(projectId: String) {
        val userId = auth.currentUser?.uid ?: return finishWithToast("خطأ: المستخدم غير مسجل الدخول.")
        val docRef = db.collection("organizations").document(userId).collection("projects").document(projectId)
        docRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val project = doc.toObject(Project::class.java)
                project?.let {
                    binding.projectNameEditText.setText(it.projectName ?: "")
                    binding.projectLocationEditText.setText(it.location ?: "")
                    selectedLatitude = it.latitude
                    selectedLongitude = it.longitude
                    binding.startDateEditText.setText(it.startDate?.toDate()?.let { d -> dateFormatter.format(d) } ?: "")
                    binding.endDateEditText.setText(it.endDate?.toDate()?.let { d -> dateFormatter.format(d) } ?: "")
                    currentProjectWorkType = it.workType

                    Log.d("PROJECT_TYPE", "نوع العمل المحمل: ${it.workType}")

                    binding.projectTypeEditText.apply {
                        isEnabled = true
                        isFocusable = false
                        isClickable = false
                        setText(it.workType ?: "غير محدد")
                    }

                    if (it.contractValue != null) {
                        binding.contractValueEditText.setText(NumberFormat.getInstance(Locale.US).format(it.contractValue))
                    }
                    if (it.workType == "جدول كميات" || it.workType == "مقطوعية") {
                        binding.contractValueLayout.isEndIconVisible = false
                        binding.contractValueEditText.isEnabled = false

                    } else {
                        binding.contractValueLayout.isEndIconVisible = true
                        binding.contractValueEditText.isEnabled = false

                    }
                    binding.viewContractTableButton.isEnabled = true
                }
            } else finishWithToast("المشروع غير موجود.")
        }.addOnFailureListener { e -> finishWithToast("فشل في جلب بيانات المشروع: ${e.message}") }
    }

    private fun finishWithToast(message: String): Nothing {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
        throw IllegalStateException("تم إنهاء النشاط")
    }

    private fun showDatePickerDialog(target: EditText) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val selected = Calendar.getInstance().apply { set(y, m, d) }
            target.setText(dateFormatter.format(selected.time))
            toggleEditTextState(target, false)
            enableSaveButton()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveProjectChangesToFirestore() {
        val name = binding.projectNameEditText.text.toString().trim()
        val location = binding.projectLocationEditText.text.toString().trim()
        val startStr = binding.startDateEditText.text.toString().trim()
        val endStr = binding.endDateEditText.text.toString().trim()
        val workType = currentProjectWorkType ?: ""
        if (name.isEmpty() || location.isEmpty() || startStr.isEmpty() || endStr.isEmpty() || workType.isEmpty()) {
            Toast.makeText(this, "الرجاء تعبئة جميع الحقول المطلوبة.", Toast.LENGTH_LONG).show()
            return
        }
        val userId = auth.currentUser?.uid ?: return finishWithToast("خطأ: المستخدم غير مسجل الدخول.")
        val startTs = runCatching { dateFormatter.parse(startStr)?.let { Timestamp(it) } }.getOrNull()
        val endTs = runCatching { dateFormatter.parse(endStr)?.let { Timestamp(it) } }.getOrNull()
        val data = mutableMapOf<String, Any?>(
            "name" to name,
            "location" to location,
            "latitude" to selectedLatitude,
            "longitude" to selectedLongitude,
            "workType" to workType,
            "startDate" to startTs,
            "endDate" to endTs,
            "updatedAt" to Timestamp.now(),
            "projectNumber" to projectId
        )

        val googleMapsUrl = ProjectLocationUtils.buildGoogleMapsUrl(
            selectedLatitude,
            selectedLongitude,
            null,
            location
        )
        data["googleMapsUrl"] = googleMapsUrl
        if (workType != "جدول كميات" && workType != "مقطوعية") {
            val valueStr = binding.contractValueEditText.text.toString().replace(",", "")
            data["contractValue"] = valueStr.toDoubleOrNull()
        }
        db.collection("organizations").document(userId).collection("projects").document(projectId)
            .update(data).addOnSuccessListener {
                Toast.makeText(this, "تم تحديث تفاصيل المشروع بنجاح.", Toast.LENGTH_SHORT).show()
                toggleEditTextState(binding.projectNameEditText, false)
                toggleEditTextState(binding.projectLocationEditText, false)
                toggleEditTextState(binding.startDateEditText, false)
                toggleEditTextState(binding.endDateEditText, false)
                if (workType != "جدول كميات" && workType != "مقطوعية") {
                    toggleEditTextState(binding.contractValueEditText, false)
                }
                binding.saveChangesButton.isEnabled = false

// ✅ تعيين أن التعديلات تم حفظها
                hasUnsavedChanges = false

// ✅ الانتقال إلى شاشة المشاريع
                val intent = Intent(this, ProjectsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()

            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل في تحديث المشروع: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun navigateToContractTable() {
        if (projectId.isNotEmpty() && !currentProjectWorkType.isNullOrEmpty()) {
            val intent = Intent(this, ContractTableActivity::class.java)
            intent.putExtra("projectId", projectId)
            intent.putExtra("workType", currentProjectWorkType)
            startActivity(intent)
        } else {
            Toast.makeText(this, "الرجاء تحديد نوع العمل للمشروع أولاً.", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmAndDeleteProject() {
        AlertDialog.Builder(this)
            .setTitle("حذف المشروع")
            .setMessage("هل أنت متأكد أنك تريد حذف هذا المشروع؟ لا يمكن التراجع عن هذا الإجراء.")
            .setPositiveButton("نعم، احذف") { dialog, _ ->
                deleteProject()
                dialog.dismiss()
            }
            .setNegativeButton("إلغاء") { dialog, _ ->
                dialog.dismiss()
            }.create().show()
    }

    private fun deleteProject() {
        val userId = auth.currentUser?.uid ?: return finishWithToast("خطأ: المستخدم غير مسجل الدخول.")
        db.collection("organizations").document(userId)
            .collection("projects").document(projectId)
            .delete().addOnSuccessListener {
                Toast.makeText(this, "تم حذف المشروع بنجاح.", Toast.LENGTH_SHORT).show()
                finish()
            }.addOnFailureListener {
                Toast.makeText(this, "فشل في حذف المشروع: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}
