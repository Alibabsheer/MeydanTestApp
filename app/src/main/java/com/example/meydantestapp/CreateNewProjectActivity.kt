package com.example.meydantestapp

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri // ✅ لاستخدام String.toUri() من KTX
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import com.example.meydantestapp.databinding.ActivityCreateNewProjectBinding
import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.ProjectLocationUtils
import com.example.meydantestapp.utils.ValidationUtils
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class CreateNewProjectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateNewProjectBinding
    private lateinit var viewModel: CreateProjectViewModel

    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null
    private var selectedPlusCode: String? = null

    private lateinit var selectLocationLauncher: ActivityResultLauncher<Intent>
    private lateinit var importExcelLauncher: ActivityResultLauncher<Intent>

    private var quantitiesTableData: MutableList<QuantityItem>? = null
    private var lumpSumTableData: MutableList<LumpSumItem>? = null
    private var calculatedContractValue: Double? = null

    private val displayDateFormatter = SimpleDateFormat(Constants.DATE_FORMAT_DISPLAY, Locale.getDefault()).apply {
        isLenient = false
    }
    private val legacyInputFormatter = SimpleDateFormat("yyyy-M-d", Locale.getDefault()).apply {
        isLenient = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateNewProjectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[CreateProjectViewModel::class.java]
        observeViewModel()

        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        importExcelLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                handleExcelImport(uri)
            }
        }

        binding.importExcelButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            importExcelLauncher.launch(Intent.createChooser(intent, "اختر ملف Excel"))
        }

        val workTypes = resources.getStringArray(R.array.work_type_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, workTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.workTypeSpinner.adapter = adapter
        binding.workTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selected = parent?.getItemAtPosition(position).toString()
                when (selected) {
                    "مقطوعية" -> {
                        binding.downloadTemplateLink.text = "📎 اضغط هنا لتحميل نموذج المقطوعية"
                        binding.downloadTemplateLink.visibility = android.view.View.VISIBLE
                        binding.downloadTemplateLink.setOnClickListener {
                            val url = "https://firebasestorage.googleapis.com/v0/b/meydan-test-project.firebasestorage.app/o/templates%2Flump_sum_template.xlsx?alt=media&token=5435b6c5-b808-4b2e-ab07-6d495856ea15"
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri()) // ✅ toUri()
                            startActivity(intent)
                        }
                        binding.importExcelButton.isEnabled = true
                        binding.contractValueInput.isEnabled = false
                        binding.contractValueInput.setText("يتم الحساب تلقائياً من ملف الإكسل")
                        calculatedContractValue = null
                        quantitiesTableData = null
                        lumpSumTableData = null
                    }
                    "جدول كميات" -> {
                        binding.downloadTemplateLink.text = "📎 اضغط هنا لتحميل نموذج جدول الكميات"
                        binding.downloadTemplateLink.visibility = android.view.View.VISIBLE
                        binding.downloadTemplateLink.setOnClickListener {
                            val url = "https://firebasestorage.googleapis.com/v0/b/meydan-test-project.firebasestorage.app/o/templates%2Fquantities_template.xlsx?alt=media&token=7fd2c853-8f6a-49c1-af11-af1e02cf576d"
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri()) // ✅ toUri()
                            startActivity(intent)
                        }
                        binding.importExcelButton.isEnabled = true
                        binding.contractValueInput.isEnabled = false
                        binding.contractValueInput.setText("يتم الحساب تلقائياً من ملف الإكسل")
                        calculatedContractValue = null
                        quantitiesTableData = null
                        lumpSumTableData = null
                    }
                    else -> {
                        binding.downloadTemplateLink.visibility = android.view.View.GONE
                        binding.importExcelButton.isEnabled = false
                        binding.contractValueInput.isEnabled = true
                        binding.contractValueInput.setText("")
                        calculatedContractValue = null
                        quantitiesTableData = null
                        lumpSumTableData = null
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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
                binding.etProjectLocation.setText(selectedAddress)
                binding.projectLocationLayout.error = null
                updateSaveButtonState()
            }
        }

        binding.etProjectLocation.setOnClickListener {
            val intent = Intent(this, SelectLocationActivity::class.java).apply {
                selectedLatitude?.let { putExtra("latitude", it) }
                selectedLongitude?.let { putExtra("longitude", it) }
            }
            selectLocationLauncher.launch(intent)
        }

        binding.startDateInput.setOnClickListener { showDatePickerDialog(binding.startDateInput) }
        binding.endDateInput.setOnClickListener { showDatePickerDialog(binding.endDateInput) }

        binding.saveProjectButton.setOnClickListener { saveProjectViaViewModel() }

        setupInputListeners()
        updateSaveButtonState()

        binding.contractValueInput.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!binding.contractValueInput.isEnabled) return
                if (s.toString() != current) {
                    binding.contractValueInput.removeTextChangedListener(this)
                    val cleanString = s.toString().replace(",", "")
                    val parsed = cleanString.toDoubleOrNull()
                    val formatted = parsed?.let { NumberFormat.getInstance(Locale.US).format(it) } ?: ""
                    current = formatted
                    binding.contractValueInput.setText(formatted)
                    binding.contractValueInput.setSelection(formatted.length)
                    binding.contractValueInput.addTextChangedListener(this)
                }
            }
        })
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
            updateSaveButtonState()
        }
        viewModel.createSuccess.observe(this) {
            Toast.makeText(this, "تم إنشاء المشروع بنجاح", Toast.LENGTH_SHORT).show()
            finish()
        }
        viewModel.errorMessage.observe(this) { msg ->
            msg?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        }
    }

    private fun handleExcelImport(uri: Uri) {
        quantitiesTableData = null
        lumpSumTableData = null
        calculatedContractValue = 0.0

        val dataFormatter = DataFormatter()

        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = XSSFWorkbook(inputStream)
                val sheet = workbook.getSheetAt(0)

                val templateTitle = sheet.getRow(0)?.getCell(0)?.stringCellValue?.trim() ?: ""
                val headerRow = sheet.getRow(1) ?: run {
                    Toast.makeText(this, "خطأ: لم يتم العثور على صف العناوين في ملف الإكسل.", Toast.LENGTH_LONG).show()
                    return
                }

                val headers = mutableListOf<String>()
                for (cell in headerRow) {
                    headers.add(dataFormatter.formatCellValue(cell).trim())
                }

                when (templateTitle) {
                    "Quantity-Based Project Template" -> {
                        quantitiesTableData = mutableListOf()
                        val headerKeys = mapOf(
                            "رقم البند" to "itemNumber",
                            "وصف البند" to "description",
                            "وحدة القياس" to "unit",
                            "الكمية" to "quantity",
                            "سعر الوحدة" to "unitPrice",
                            "القيمة الاجمالية" to "totalValue"
                        )
                        for (i in 2..sheet.lastRowNum) {
                            val currentRow = sheet.getRow(i) ?: continue
                            var itemNumber: String? = null
                            var description: String? = null
                            var unit: String? = null
                            var quantity: Double? = null
                            var unitPrice: Double? = null
                            var totalValue: Double? = null

                            for (j in 0 until headers.size) {
                                val cell = currentRow.getCell(j)
                                val header = headers[j]
                                val key = headerKeys[header]
                                when (key) {
                                    "itemNumber" -> itemNumber = dataFormatter.formatCellValue(cell)?.trim()
                                    "description" -> description = dataFormatter.formatCellValue(cell)?.trim()
                                    "unit" -> unit = dataFormatter.formatCellValue(cell)?.trim()
                                    "quantity" -> quantity = cell?.numericCellValue
                                    "unitPrice" -> unitPrice = cell?.numericCellValue
                                    "totalValue" -> {
                                        totalValue = cell?.numericCellValue
                                        // ✅ إزالة !! غير الضرورية
                                        calculatedContractValue = (calculatedContractValue ?: 0.0) + (totalValue ?: 0.0)
                                    }
                                }
                            }
                            if (itemNumber != null && description != null && unit != null && quantity != null && unitPrice != null && totalValue != null) {
                                val item = QuantityItem(itemNumber, description, unit, quantity, unitPrice, totalValue)
                                quantitiesTableData?.add(item)
                            }
                        }
                        Toast.makeText(this, "تم استيراد جدول الكميات بنجاح.", Toast.LENGTH_SHORT).show()
                    }

                    "Lump-Sum Project Template" -> {
                        lumpSumTableData = mutableListOf()
                        val headerKeys = mapOf(
                            "رقم البند" to "itemNumber",
                            "وصف البند" to "description",
                            "القيمة الاجمالية" to "totalValue"
                        )
                        for (i in 2..sheet.lastRowNum) {
                            val currentRow = sheet.getRow(i) ?: continue
                            var itemNumber: String? = null
                            var description: String? = null
                            var totalValue: Double? = null

                            for (j in 0 until headers.size) {
                                val cell = currentRow.getCell(j)
                                val header = headers[j]
                                val key = headerKeys[header]
                                when (key) {
                                    "itemNumber" -> itemNumber = dataFormatter.formatCellValue(cell)?.trim()
                                    "description" -> description = dataFormatter.formatCellValue(cell)?.trim()
                                    "totalValue" -> {
                                        totalValue = cell?.numericCellValue
                                        // ✅ إزالة !! غير الضرورية
                                        calculatedContractValue = (calculatedContractValue ?: 0.0) + (totalValue ?: 0.0)
                                    }
                                }
                            }
                            if (itemNumber != null && description != null && totalValue != null) {
                                val item = LumpSumItem(itemNumber, description, totalValue)
                                lumpSumTableData?.add(item)
                            }
                        }
                        Toast.makeText(this, "تم استيراد عقد المقطوعية بنجاح.", Toast.LENGTH_SHORT).show()
                    }

                    else -> {
                        Toast.makeText(this, "نوع قالب الإكسل غير معروف أو غير مدعوم: $templateTitle", Toast.LENGTH_LONG).show()
                        return
                    }
                }

                val formattedTotal = NumberFormat.getInstance(Locale.US).format(calculatedContractValue ?: 0.0)
                binding.contractValueInput.setText(formattedTotal)
                binding.contractValueInput.isEnabled = false

            } ?: Toast.makeText(this, "فشل فتح ملف الإكسل.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ أثناء معالجة ملف الإكسل: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            calculatedContractValue = null
            quantitiesTableData = null
            lumpSumTableData = null
            binding.contractValueInput.setText("")
        }
    }

    private fun showDatePickerDialog(targetEditText: EditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val selected = Calendar.getInstance().apply { set(year, month, dayOfMonth, 0, 0, 0) }
            val dateStr = displayDateFormatter.format(selected.time)
            targetEditText.setText(dateStr)
            when (targetEditText.id) {
                binding.startDateInput.id -> binding.startDateInputLayout.error = null
                binding.endDateInput.id -> binding.endDateInputLayout.error = null
            }
            updateSaveButtonState()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveProjectViaViewModel() {
        val projectName = binding.projectNameInput.text.toString().trim()
        val location = binding.etProjectLocation.text.toString().trim()
        val startDateStr = binding.startDateInput.text.toString().trim()
        val endDateStr = binding.endDateInput.text.toString().trim()
        val workType = binding.workTypeSpinner.selectedItem?.toString() ?: ""

        clearInputErrors()

        var hasError = false

        if (projectName.isBlank()) {
            binding.projectNameInput.error = "اسم المشروع مطلوب"
            hasError = true
        } else if (!ValidationUtils.isValidProjectName(projectName)) {
            binding.projectNameInput.error = ValidationUtils.getProjectNameErrorMessage(projectName)
            hasError = true
        }

        if (location.isBlank()) {
            binding.projectLocationLayout.error = "موقع المشروع مطلوب"
            hasError = true
        }

        val startDate = parseDateOrNull(startDateStr)
        if (startDateStr.isBlank()) {
            binding.startDateInputLayout.error = "تاريخ البدء مطلوب"
            hasError = true
        } else if (startDate == null) {
            binding.startDateInputLayout.error = "صيغة تاريخ البدء غير صحيحة"
            hasError = true
        }

        val endDate = parseDateOrNull(endDateStr)
        if (endDateStr.isBlank()) {
            binding.endDateInputLayout.error = "تاريخ الانتهاء مطلوب"
            hasError = true
        } else if (endDate == null) {
            binding.endDateInputLayout.error = "صيغة تاريخ الانتهاء غير صحيحة"
            hasError = true
        }

        if (startDate != null && endDate != null && endDate.before(startDate)) {
            binding.endDateInputLayout.error = "تاريخ الانتهاء يجب أن يكون بعد تاريخ البدء"
            hasError = true
        }

        if (hasError) {
            Toast.makeText(this, "يرجى تعبئة جميع الحقول المطلوبة بشكل صحيح", Toast.LENGTH_LONG).show()
            updateSaveButtonState()
            return
        }

        viewModel.createProject(
            projectName = projectName,
            addressText = location,
            latitude = selectedLatitude,
            longitude = selectedLongitude,
            startDateStr = startDateStr,
            endDateStr = endDateStr,
            workType = workType,
            quantitiesTableData = quantitiesTableData,
            lumpSumTableData = lumpSumTableData,
            calculatedContractValue = calculatedContractValue,
            plusCode = selectedPlusCode
        )
    }

    private fun Intent?.getNullableDouble(key: String): Double? {
        if (this == null || !hasExtra(key)) return null
        val value = getDoubleExtra(key, Double.NaN)
        return if (value.isNaN()) null else value
    }

    private fun parseDateOrNull(dateStr: String): Date? {
        if (dateStr.isBlank()) return null
        return try {
            displayDateFormatter.parse(dateStr)
        } catch (_: ParseException) {
            try {
                legacyInputFormatter.parse(dateStr)
            } catch (_: ParseException) {
                null
            }
        }
    }

    private fun setupInputListeners() {
        binding.projectNameInput.addTextChangedListener {
            if (!it.isNullOrBlank()) {
                binding.projectNameInput.error = null
            }
            updateSaveButtonState()
        }

        binding.etProjectLocation.addTextChangedListener {
            if (!it.isNullOrBlank()) {
                binding.projectLocationLayout.error = null
            }
            updateSaveButtonState()
        }

        binding.startDateInput.addTextChangedListener {
            if (!it.isNullOrBlank()) {
                binding.startDateInputLayout.error = null
            }
            updateSaveButtonState()
        }

        binding.endDateInput.addTextChangedListener {
            if (!it.isNullOrBlank()) {
                binding.endDateInputLayout.error = null
            }
            updateSaveButtonState()
        }
    }

    private fun clearInputErrors() {
        binding.projectNameInput.error = null
        binding.projectLocationLayout.error = null
        binding.startDateInputLayout.error = null
        binding.endDateInputLayout.error = null
    }

    private fun updateSaveButtonState() {
        val projectNameFilled = binding.projectNameInput.text?.toString()?.trim()?.isNotEmpty() == true
        val locationFilled = binding.etProjectLocation.text?.toString()?.trim()?.isNotEmpty() == true
        val startDateFilled = binding.startDateInput.text?.toString()?.trim()?.isNotEmpty() == true
        val endDateFilled = binding.endDateInput.text?.toString()?.trim()?.isNotEmpty() == true
        val isLoading = viewModel.isLoading.value == true

        binding.saveProjectButton.isEnabled = projectNameFilled && locationFilled && startDateFilled && endDateFilled && !isLoading
    }
}
