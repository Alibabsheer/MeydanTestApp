package com.example.meydantestapp

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.example.meydantestapp.databinding.ActivityProjectSettingsBinding
import androidx.core.view.isVisible
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.example.meydantestapp.utils.ProjectLocationUtils
import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.FirestoreTimestampConverter
import com.example.meydantestapp.utils.migrateTimestampIfNeeded

class ProjectSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjectSettingsBinding

    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private var selectedPlusCode: String? = null
    private var isUpdatingLocationText = false

    private lateinit var selectLocationLauncher: ActivityResultLauncher<Intent>

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var projectId: String
    private var currentProjectWorkType: String? = null

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var hasUnsavedChanges = false

    private companion object {
        private const val TAG = "ProjectSettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewContractTableButton.isEnabled = false

        val resolvedProjectId = intent.getStringExtra(Constants.EXTRA_PROJECT_ID)
            ?: intent.getStringExtra("PROJECT_ID")
            ?: intent.getStringExtra("projectId")
            ?: intent.getStringExtra("id")

        if (resolvedProjectId.isNullOrBlank()) {
            Log.e(TAG, "Opened without projectId extra. Finishing.")
            Toast.makeText(
                this,
                "لا يمكن فتح إعدادات المشروع بدون معرف مشروع",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        projectId = resolvedProjectId
        Log.d(TAG, "Loaded ProjectSettingsActivity with projectId=$projectId")

        selectLocationLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val selectedAddress = data?.getStringExtra("address") ?: ""
                selectedLatitude = data.getNullableDouble("latitude")
                selectedLongitude = data.getNullableDouble("longitude")
                selectedPlusCode = ProjectLocationUtils.normalizePlusCode(
                    data?.getStringExtra("plusCode")
                )
                withLocationTextUpdate {
                    binding.projectLocationEditText.setText(selectedAddress)
                }
                enableSaveButton()
                updateClearLocationVisibility()
            }
        }
        binding.saveChangesButton.text = "حفظ التعديلات"
        binding.deleteProjectButton.visibility = View.VISIBLE
        binding.titleText.text = "تفاصيل المشروع"
        loadProjectData(projectId)

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
        binding.clearLocationButton.setOnClickListener { clearLocation() }
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
            if (!isUpdatingLocationText) {
                selectedLatitude = null
                selectedLongitude = null
                selectedPlusCode = null
            }
            hasUnsavedChanges = true
            enableSaveButton()
            updateClearLocationVisibility()
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

        updateClearLocationVisibility()
    }

    override fun onResume() {
        super.onResume()
        if (::projectId.isInitialized && projectId.isNotEmpty()) {
            loadProjectData(projectId)
        }
    }

    private fun enableSaveButton() {
        val name = binding.projectNameEditText.text.toString().trim()
        val startStr = binding.startDateEditText.text.toString().trim()
        val endStr = binding.endDateEditText.text.toString().trim()
        binding.saveChangesButton.isEnabled = name.isNotEmpty() && startStr.isNotEmpty() && endStr.isNotEmpty()
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
                val data = doc.data ?: emptyMap<String, Any?>()

                val startConversion = FirestoreTimestampConverter.fromAny(data["startDate"])
                val endConversion = FirestoreTimestampConverter.fromAny(data["endDate"])

                doc.migrateTimestampIfNeeded("startDate", startConversion)
                doc.migrateTimestampIfNeeded("endDate", endConversion)

                val startTs = startConversion.timestamp
                val endTs = endConversion.timestamp

                Log.i(
                    TAG,
                    "Loaded project=$projectId start=${startTs?.seconds} end=${endTs?.seconds}"
                )

                val projectName = (data["projectName"] as? String)
                    ?: (data["name"] as? String)
                    ?: ""
                binding.projectNameEditText.setText(projectName)

                val address = (data["addressText"] as? String)
                    ?: (data["location"] as? String)
                    ?: ""
                withLocationTextUpdate {
                    binding.projectLocationEditText.setText(address)
                }

                selectedLatitude = (data["latitude"] as? Number)?.toDouble()
                selectedLongitude = (data["longitude"] as? Number)?.toDouble()
                selectedPlusCode = data["plusCode"] as? String

                val startText = startTs?.toDate()?.let { dateFormatter.format(it) } ?: ""
                val endText = endTs?.toDate()?.let { dateFormatter.format(it) } ?: ""
                binding.startDateEditText.setText(startText)
                binding.endDateEditText.setText(endText)

                currentProjectWorkType = data["workType"] as? String

                binding.projectTypeEditText.apply {
                    isEnabled = true
                    isFocusable = false
                    isClickable = false
                    setText(currentProjectWorkType ?: "غير محدد")
                }

                val contractValueRaw = data["contractValue"]
                val contractValue = when (contractValueRaw) {
                    is Number -> contractValueRaw.toDouble()
                    is String -> contractValueRaw.toDoubleOrNull()
                    else -> null
                }
                if (contractValue != null) {
                    binding.contractValueEditText.setText(
                        NumberFormat.getInstance(Locale.US).format(contractValue)
                    )
                } else {
                    binding.contractValueEditText.setText("")
                }

                if (currentProjectWorkType == "جدول كميات" || currentProjectWorkType == "مقطوعية") {
                    binding.contractValueLayout.isEndIconVisible = false
                    binding.contractValueEditText.isEnabled = false
                } else {
                    binding.contractValueLayout.isEndIconVisible = true
                    binding.contractValueEditText.isEnabled = false
                }

                binding.viewContractTableButton.isEnabled = true
                updateClearLocationVisibility()
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
        val startStr = binding.startDateEditText.text.toString().trim()
        val endStr = binding.endDateEditText.text.toString().trim()
        val workType = currentProjectWorkType ?: ""
        if (name.isEmpty() || startStr.isEmpty() || endStr.isEmpty() || workType.isEmpty()) {
            Toast.makeText(this, "الرجاء تعبئة جميع الحقول المطلوبة.", Toast.LENGTH_LONG).show()
            return
        }
        val userId = auth.currentUser?.uid ?: return finishWithToast("خطأ: المستخدم غير مسجل الدخول.")
        val startConversion = FirestoreTimestampConverter.fromAny(startStr)
        val endConversion = FirestoreTimestampConverter.fromAny(endStr)
        val startTs = startConversion.timestamp
        val endTs = endConversion.timestamp
        if (startTs == null || endTs == null) {
            Toast.makeText(this, "تعذر قراءة التواريخ المدخلة.", Toast.LENGTH_LONG).show()
            return
        }

        Log.i(TAG, "Saving project=$projectId start=${startTs.seconds} end=${endTs.seconds}")

        val normalizedAddress = ProjectLocationUtils.normalizeAddressText(
            binding.projectLocationEditText.text?.toString()
        )
        val normalizedPlusCode = ProjectLocationUtils.normalizePlusCode(selectedPlusCode)
        val lat = selectedLatitude
        val lng = selectedLongitude
        val googleMapsUrl = ProjectLocationUtils.buildGoogleMapsUrl(lat, lng)

        val data = mutableMapOf<String, Any?>(
            "name" to name,
            "projectName" to name,
            "location" to normalizedAddress,
            "addressText" to normalizedAddress,
            "latitude" to lat,
            "longitude" to lng,
            "plusCode" to normalizedPlusCode,
            "workType" to workType,
            "startDate" to startTs,
            "endDate" to endTs,
            "updatedAt" to Timestamp.now(),
            "projectNumber" to projectId,
            "googleMapsUrl" to googleMapsUrl
        )

        if (workType != "جدول كميات" && workType != "مقطوعية") {
            val valueStr = binding.contractValueEditText.text.toString().replace(",", "")
            val parsed = valueStr.toDoubleOrNull()
            data["contractValue"] = parsed ?: FieldValue.delete()
        }
        if (normalizedAddress == null) {
            data["location"] = FieldValue.delete()
            data["addressText"] = FieldValue.delete()
        }
        if (lat == null) {
            data["latitude"] = FieldValue.delete()
        }
        if (lng == null) {
            data["longitude"] = FieldValue.delete()
        }
        if (normalizedPlusCode == null) {
            data["plusCode"] = FieldValue.delete()
        }
        if (googleMapsUrl == null) {
            data["googleMapsUrl"] = FieldValue.delete()
        }

        val updatePayload = mutableMapOf<String, Any>()
        data.forEach { (key, value) ->
            updatePayload[key] = value ?: FieldValue.delete()
        }

        db.collection("organizations").document(userId).collection("projects").document(projectId)
            .update(updatePayload).addOnSuccessListener {
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

    private fun clearLocation() {
        selectedLatitude = null
        selectedLongitude = null
        selectedPlusCode = null
        withLocationTextUpdate {
            binding.projectLocationEditText.setText("")
        }
        hasUnsavedChanges = true
        enableSaveButton()
        updateClearLocationVisibility()
    }

    private fun updateClearLocationVisibility() {
        val hasAddress = !binding.projectLocationEditText.text.isNullOrBlank()
        binding.clearLocationButton.isVisible = hasAddress ||
            selectedLatitude != null ||
            selectedLongitude != null ||
            !selectedPlusCode.isNullOrBlank()
    }

    private fun Intent?.getNullableDouble(key: String): Double? {
        if (this == null || !hasExtra(key)) return null
        val value = getDoubleExtra(key, Double.NaN)
        return if (value.isNaN()) null else value
    }

    private inline fun withLocationTextUpdate(block: () -> Unit) {
        isUpdatingLocationText = true
        try {
            block()
        } finally {
            isUpdatingLocationText = false
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
