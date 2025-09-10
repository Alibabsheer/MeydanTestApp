package com.example.meydantestapp

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.adapters.PhotoSlotAdapter
import com.example.meydantestapp.adapters.applyTemplateGrid
import com.example.meydantestapp.databinding.ActivityCreateDailyReportBinding
import com.example.meydantestapp.models.PhotoEntry
import com.example.meydantestapp.models.PhotoTemplate
import com.example.meydantestapp.models.PhotoTemplates
import com.example.meydantestapp.models.TemplateId
import com.example.meydantestapp.ui.photolayout.TemplatePickerBottomSheet
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class CreateDailyReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateDailyReportBinding
    private val vm: CreateDailyReportViewModel by viewModels()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var organizationId: String? = null
    private var projectId: String? = null
    private var projectName: String? = null
    private var selectedProject: Project? = null

    // ملف مؤقت للكاميرا
    private var cameraTempFile: File? = null

    // ===== شبكة القوالب (الواجهة الجديدة) =====
    private var gridRoot: View? = null
    private var rvPhotoSlots: RecyclerView? = null
    private var slotAdapter: PhotoSlotAdapter? = null
    private var selectedTemplate: PhotoTemplate? = null
    private var pendingSlotIndex: Int = -1

    companion object {
        private const val MAX_SELECTION = 10
        private const val FINISH_DELAY_MS = 700L
    }

    // ===== لانشرات =====
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val file = cameraTempFile
            val uri = file?.let { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) }
            if (ok && uri != null) {
                setEntryInNextEmptySlot(uri)
            } else {
                file?.delete()
            }
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera() else Toast.makeText(this, "تم رفض صلاحية الكاميرا.", Toast.LENGTH_SHORT).show()
        }

    private val pickMultiple =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (!uris.isNullOrEmpty()) {
                val capacity = selectedTemplate?.slots?.size ?: MAX_SELECTION
                val trimmed = if (uris.size > MAX_SELECTION) {
                    Toast.makeText(this, "لا يمكن اختيار أكثر من $MAX_SELECTION صورة دفعة واحدة.", Toast.LENGTH_LONG).show()
                    uris.take(MAX_SELECTION)
                } else uris

                // املأ الخانات المتاحة بالترتيب
                var filled = 0
                trimmed.forEach { uri ->
                    if (filled < capacity) {
                        if (setEntryInNextEmptySlot(uri)) filled++
                    }
                }
            }
        }

    private val slotImagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val slot = pendingSlotIndex
            pendingSlotIndex = -1
            if (uri != null && slot >= 0) {
                setEntryForSlot(slot, uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateDailyReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // قيَم من الشاشة السابقة
        projectId = intent.getStringExtra("projectId")
        projectName = intent.getStringExtra("projectName")
        organizationId = intent.getStringExtra("organizationId")

        // تمهيد أسماء العرض الملتقطة وقت الإنشاء
        vm.setCreatedByName(auth.currentUser?.displayName)

        // تعطيل التاريخ حتى تحميل المشروع
        binding.reportDateInput.isEnabled = false

        // إجمالي العمالة للعرض فقط
        binding.totalLaborInput.isEnabled = false

        // أزرار عامة
        binding.backButton.setOnClickListener { finish() }
        binding.addPhotoButton.setOnClickListener { showImageSourceOptions() }
        binding.reportDateInput.setOnClickListener { showDatePicker() }
        binding.saveReportButton.setOnClickListener { onSaveClicked() }

        // إعداد شبكة القوالب داخل الحاوية الحالية للصور المصغّرة
        setupPhotoGridSection()

        // مراقبة الطقس (تحويل درجة الحرارة لعدد صحيح قبل العرض)
        vm.temperature.observe(this) { raw ->
            binding.temperatureInput.setText(formatTempToIntString(raw))
        }
        vm.weatherStatus.observe(this) { binding.weatherStatusInput.setText(it ?: "") }

        // ربط الحقول لتجميع العمالة لحظيًا
        setUpLaborAutoSum()

        // ربط طبقة التحميل بالحالات والنِّسَب
        vm.saveState.observe(this) { state ->
            when (state) {
                SaveState.Idle -> {
                    hideUploadOverlay()
                    setInputsEnabled(true)
                }
                SaveState.Uploading -> {
                    setInputsEnabled(false)
                    showUploadOverlay(status = "جاري رفع الصور...", percent = vm.uploadProgress.value ?: 0, indeterminate = false)
                }
                SaveState.Saving -> {
                    setInputsEnabled(false)
                    showUploadOverlay(status = "جاري حفظ التقرير...", percent = (vm.uploadProgress.value ?: 90).coerceAtLeast(50), indeterminate = false)
                }
                SaveState.Success -> {
                    showUploadOverlay(status = "اكتمل الحفظ", percent = 100, indeterminate = false)
                    showResultBanner(success = true, text = "تم حفظ التقرير بنجاح")
                    Handler(Looper.getMainLooper()).postDelayed({
                        setResult(RESULT_OK)
                        finish()
                    }, FINISH_DELAY_MS)
                }
                SaveState.FailureNetwork -> {
                    hideUploadOverlay()
                    setInputsEnabled(true)
                    showResultBanner(success = false, text = "فشل حفظ التقرير بسبب ضعف الانترنت، يرجى المحاولة مرة أخرى")
                }
                SaveState.FailureOther -> {
                    hideUploadOverlay()
                    setInputsEnabled(true)
                    // الرسالة التفصيلية تُعرض عبر vm.message
                }
            }
        }

        vm.uploadProgress.observe(this) { p ->
            if (binding.uploadOverlay.visibility == View.VISIBLE) {
                binding.uploadProgressBar.isIndeterminate = false
                binding.uploadProgressBar.progress = p.coerceIn(0, 100)
                binding.uploadPercentText.text = "$p%"
            }
        }

        // رسائل عامة
        vm.message.observe(this) { msg ->
            msg?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                if (it.contains("فشل")) {
                    showResultBanner(success = false, text = it)
                }
            }
        }

        // التدفق القديم لعرض المصغرات سيُهمَل عندما نكون في وضع الشبكة
        vm.photos.observe(this) { uris ->
            if (selectedTemplate == null) renderPhotos(uris)
        }

        // صف افتراضي لكل قسم
        initDynamicSections()

        // تحميل المؤسسة ثم المشروع
        resolveOrganizationAndLoadProject()
    }

    // ===== قسم شبكة القوالب =====
    private fun setupPhotoGridSection() {
        // نستخدم include_photo_grid كواجهة الشبكة
        binding.photoPreviewContainer.removeAllViews()
        val root = layoutInflater.inflate(R.layout.include_photo_grid, binding.photoPreviewContainer, false)
        binding.photoPreviewContainer.addView(root)
        gridRoot = root

        rvPhotoSlots = root.findViewById(R.id.rvPhotoSlots)

        // مزامنة القالب المبدئي مع الـ ViewModel
        vm.selectTemplate(TemplateId.E4)

        // مُهيّئ Adapter بقالب افتراضي
        slotAdapter = PhotoSlotAdapter(
            template = PhotoTemplates.byId(TemplateId.E4),
            onPickImage = { slot ->
                pendingSlotIndex = slot
                slotImagePicker.launch("image/*")
            },
            onCaptionChanged = { slot, cap -> updateCaptionForSlot(slot, cap) },
            onRemoveImage = { slot -> removeEntryForSlot(slot) }
        )
        rvPhotoSlots?.adapter = slotAdapter
        rvPhotoSlots?.applyTemplateGrid(PhotoTemplates.byId(TemplateId.E4))

        // اختيار القالب عبر BottomSheet
        gridRoot?.findViewById<View>(R.id.btnPickTemplate)?.setOnClickListener {
            TemplatePickerBottomSheet.show(supportFragmentManager, selectedTemplate?.id)
        }

        supportFragmentManager.setFragmentResultListener(
            TemplatePickerBottomSheet.REQUEST_KEY, this
        ) { _, bundle ->
            val idName = bundle.getString(TemplatePickerBottomSheet.RESULT_TEMPLATE_ID)
            val tid = runCatching { TemplateId.valueOf(idName ?: "") }.getOrNull()
            if (tid != null) onTemplateChosen(tid) else Toast.makeText(this, "تعذر تحديد القالب.", Toast.LENGTH_SHORT).show()
        }

        // اجعل القالب الافتراضي E4 عند البدء (يمكن تغييره فورًا من المستخدم)
        onTemplateChosen(TemplateId.E4)
    }

    private fun onTemplateChosen(templateId: TemplateId) {
        val tpl = PhotoTemplates.byId(templateId)
        selectedTemplate = tpl
        rvPhotoSlots?.applyTemplateGrid(tpl)
        slotAdapter?.submitTemplate(tpl)
        // مزامنة مع الـ ViewModel لضمان نفس عدد الخانات
        vm.selectTemplate(templateId)
    }

    private fun setEntryInNextEmptySlot(uri: Uri): Boolean {
        val tpl = selectedTemplate ?: return false
        val adapter = slotAdapter ?: return false
        val current = adapter.getCurrentEntries().toMutableList()
        val emptyIndex = current.indexOfFirst { it == null }
        if (emptyIndex == -1) {
            Toast.makeText(this, "تم ملء جميع خانات الصفحة الحالية.", Toast.LENGTH_SHORT).show()
            return false
        }
        val entry = PhotoEntry(
            templateId = tpl.id,
            pageIndex = 0,
            slotIndex = emptyIndex,
            localUri = uri.toString(),
            originalUrl = null,
            caption = null
        )
        // تحديث خانة واحدة فقط + مزامنة الـ VM
        adapter.setEntry(emptyIndex, entry)
        vm.setSlotImage(emptyIndex, uri)
        return true
    }

    private fun setEntryForSlot(slotIndex: Int, uri: Uri) {
        val tpl = selectedTemplate ?: return
        val adapter = slotAdapter ?: return
        val current = adapter.getCurrentEntries().toMutableList()
        if (slotIndex !in current.indices) return
        val entry = PhotoEntry(
            templateId = tpl.id,
            pageIndex = 0,
            slotIndex = slotIndex,
            localUri = uri.toString(),
            originalUrl = null,
            caption = current[slotIndex]?.caption
        )
        adapter.setEntry(slotIndex, entry)
        vm.setSlotImage(slotIndex, uri)
    }

    private fun updateCaptionForSlot(slotIndex: Int, caption: String?) {
        val adapter = slotAdapter ?: return
        val current = adapter.getCurrentEntries().toMutableList()
        if (slotIndex !in current.indices) return
        val entry = current[slotIndex]
        if (entry != null) {
            val updated = entry.copy(caption = caption?.trim()?.take(PhotoEntry.MAX_CAPTION_CHARS))
            adapter.setEntry(slotIndex, updated)
        }
        vm.setSlotCaption(slotIndex, caption)
    }

    private fun removeEntryForSlot(slotIndex: Int) {
        slotAdapter?.clearSlot(slotIndex)
        vm.removeSlot(slotIndex)
    }

    /** يحوّل أي تمثيل لدرجة الحرارة إلى عدد صحيح كسلسلة دون فواصل أو وحدات */
    private fun formatTempToIntString(src: String?): String {
        if (src.isNullOrBlank()) return ""
        val filtered = src.trim().filter { ch ->
            ch.isDigit() || ch == '.' || ch == '+' || ch == '-'
        }
        if (filtered.isBlank()) return ""
        val d = filtered.toDoubleOrNull()
        return if (d != null) d.roundToInt().toString() else filtered.filter { it.isDigit() }
    }

    /** يربط حقول العمالة مع الـ ViewModel ويعرض الإجمالي لحظيًا */
    private fun setUpLaborAutoSum() {
        binding.skilledLaborInput.doOnTextChanged { text, _, _, _ -> vm.onSkilledChanged(text) }
        binding.unskilledLaborInput.doOnTextChanged { text, _, _, _ -> vm.onUnskilledChanged(text) }
        vm.totalLaborText.observe(this) { totalText ->
            val current = binding.totalLaborInput.text?.toString()
            if (current != totalText) {
                binding.totalLaborInput.setText(totalText ?: "")
            }
        }
    }

    /** تحديد organizationId ثم تحميل المشروع + التقاط organizationName في الـ VM */
    private fun resolveOrganizationAndLoadProject() {
        val pid = projectId
        if (pid.isNullOrBlank()) {
            Toast.makeText(this, "معرّف المشروع غير متاح.", Toast.LENGTH_LONG).show()
            return
        }

        val passedOrg = organizationId
        if (!passedOrg.isNullOrBlank()) {
            organizationId = passedOrg
            loadOrganizationName(passedOrg)
            loadProject(passedOrg, pid)
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "لم يتم تسجيل الدخول.", Toast.LENGTH_LONG).show()
            return
        }

        db.collection("organizations").document(currentUser.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    organizationId = currentUser.uid
                    loadOrganizationName(currentUser.uid)
                    loadProject(currentUser.uid, pid)
                } else {
                    db.collectionGroup("users")
                        .whereEqualTo("uid", currentUser.uid)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { q ->
                            val orgId = q.documents.firstOrNull()?.reference?.parent?.parent?.id
                            if (orgId.isNullOrBlank()) {
                                Toast.makeText(this, "تعذر تحديد مؤسسة المستخدم.", Toast.LENGTH_LONG).show()
                            } else {
                                organizationId = orgId
                                loadOrganizationName(orgId)
                                loadProject(orgId, pid)
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "فشل في تحديد مؤسسة المستخدم.", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل في جلب بيانات المؤسسة.", Toast.LENGTH_LONG).show()
            }
    }

    /** جلب organizationName وتمريره لـ ViewModel للالتقاط وقت الإنشاء */
    private fun loadOrganizationName(orgId: String) {
        db.collection("organizations").document(orgId)
            .get()
            .addOnSuccessListener { doc ->
                val orgName = (doc.get("name") as? String)
                    ?: (doc.get("organizationName") as? String)
                vm.setOrganizationName(orgName)
            }
            .addOnFailureListener { /* تجاهل */ }
    }

    /** تحميل وثيقة المشروع وتمريرها للـ ViewModel (تشمل projectName/lat/lng ...) */
    private fun loadProject(orgId: String, pid: String) {
        db.collection("organizations").document(orgId)
            .collection("projects").document(pid)
            .get()
            .addOnSuccessListener { doc ->
                selectedProject = doc.toObject(Project::class.java)?.also { it.id = doc.id }

                val projectMap = doc.data ?: emptyMap<String, Any>()
                vm.setProjectInfo(projectMap)

                if (projectName.isNullOrBlank()) {
                    projectName = (projectMap["projectName"] as? String)
                        ?: (projectMap["name"] as? String)
                }

                binding.reportDateInput.isEnabled = true
            }
            .addOnFailureListener {
                Toast.makeText(this, "فشل في جلب بيانات المشروع.", Toast.LENGTH_LONG).show()
            }
    }

    // ===== التاريخ + الطقس =====
    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        val dlg = DatePickerDialog(
            this,
            { _, y, m, d ->
                val c = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dateStr = df.format(c.time)
                binding.reportDateInput.setText(dateStr)
                vm.setDate(dateStr)

                var lat = selectedProject?.latitude
                var lng = selectedProject?.longitude

                if ((lat == null || lat == 0.0) || (lng == null || lng == 0.0)) {
                    val map = vm.projectInfo.value
                    lat = when (val v = map?.get("latitude")) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull()
                        else -> null
                    } ?: 0.0
                    lng = when (val v = map?.get("longitude")) {
                        is Number -> v.toDouble()
                        is String -> v.toDoubleOrNull()
                        else -> null
                    } ?: 0.0
                }

                if (lat == 0.0 || lng == 0.0) {
                    Toast.makeText(this, "بيانات الموقع غير متوفرة لهذا المشروع.", Toast.LENGTH_SHORT).show()
                } else {
                    vm.fetchWeatherFor(dateStr, lat, lng)
                }
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dlg.show()
    }

    // ===== الصور (اختيار المصدر العام) =====
    private fun showImageSourceOptions() {
        val options = arrayOf("التقاط صورة بالكاميرا", "اختيار صور متعددة من المعرض")
        AlertDialog.Builder(this)
            .setTitle("إضافة صور")
            .setItems(options) { dlg, which ->
                when (which) {
                    0 -> requestOrLaunchCamera()
                    1 -> pickImages()
                }
                dlg.dismiss()
            }.show()
    }

    private fun pickImages() { pickMultiple.launch("image/*") }

    private fun requestOrLaunchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        } else {
            launchCamera()
        }
    }

    private fun launchCamera() {
        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        cameraTempFile = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cameraTempFile!!)
        takePictureLauncher.launch(uri)
    }

    // ===== حفظ التقرير =====
    private fun onSaveClicked() {
        if (!validateInputs()) return

        val orgId = organizationId ?: run {
            Toast.makeText(this, "معلومات المؤسسة غير متاحة.", Toast.LENGTH_LONG).show()
            return
        }

        // تأكيد تمرير أسماء العرض للـ VM قبل الحفظ
        if (vm.organizationName.value.isNullOrBlank()) { loadOrganizationName(orgId) }
        if (vm.createdByName.value.isNullOrBlank()) { vm.setCreatedByName(auth.currentUser?.displayName) }

        // بدء طبقة التحميل من البداية
        showUploadOverlay(status = "جاري رفع التقرير...", percent = 0, indeterminate = true)

        // ✅ وفق الخطة الاستثنائية: لا نضيف الصور إلى التدفق القديم (_photos).
        // الاعتماد سيكون على خانات القالب داخل الـ ViewModel لتركيب صفحات A4 عمودية.

        val activities = extractTextsFrom(binding.activityDescriptionInput).filter { it.isNotBlank() }
        val equipment = extractTextsFrom(binding.equipmentInputContainer).filter { it.isNotBlank() }
        val issues = extractTextsFrom(binding.issueInputContainer).filter { it.isNotBlank() }
        val notes = extractTextsFrom(binding.noteInputContainer).filter { it.isNotBlank() }

        vm.saveReport(
            organizationId = orgId,
            projectId = projectId ?: "",
            activities = activities,
            skilledLabor = binding.skilledLaborInput.text?.toString(),
            unskilledLabor = binding.unskilledLaborInput.text?.toString(),
            totalLabor = binding.totalLaborInput.text?.toString(),
            equipmentList = equipment,
            challengesList = issues,
            notesList = notes
        )
    }

    private fun validateInputs(): Boolean {
        val dateOk = !binding.reportDateInput.text.isNullOrBlank()
        val hasAnyActivity = extractTextsFrom(binding.activityDescriptionInput).any { it.isNotBlank() }
        if (!dateOk) {
            Toast.makeText(this, "يرجى تحديد تاريخ التقرير.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!hasAnyActivity) {
            Toast.makeText(this, "يرجى إدخال نشاط واحد على الأقل.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    /** استخراج النصوص من داخل TextInputLayout/TextInputEditText */
    private fun extractTextsFrom(container: ViewGroup): List<String> {
        val result = mutableListOf<String>()
        fun traverse(v: View) {
            when (v) {
                is TextInputLayout -> {
                    val et = v.editText
                    val s = et?.text?.toString()?.trim().orEmpty()
                    if (s.isNotEmpty()) result += s
                }
                is TextInputEditText -> {
                    val s = v.text?.toString()?.trim().orEmpty()
                    if (s.isNotEmpty()) result += s
                }
                is ViewGroup -> {
                    for (i in 0 until v.childCount) traverse(v.getChildAt(i))
                }
            }
        }
        traverse(container)
        return result
    }

    // ===== أقسام ديناميكية (نشاطات/معدات/عوائق/ملاحظات) =====
    private fun initDynamicSections() {
        addWorkRow()
        addEquipmentRow()
        addIssueRow()
        addNoteRow()
    }

    private fun addWorkRow() {
        val row = LayoutInflater.from(this).inflate(R.layout.item_work, binding.activityDescriptionInput, false)
        row.findViewById<ImageButton?>(R.id.addWorkButton)?.setOnClickListener { addWorkRow() }
        binding.activityDescriptionInput.addView(row)
    }

    private fun addEquipmentRow() {
        val row = LayoutInflater.from(this).inflate(R.layout.item_equipment, binding.equipmentInputContainer, false)
        row.findViewById<ImageButton?>(R.id.addEquipmentButton)?.setOnClickListener { addEquipmentRow() }
        binding.equipmentInputContainer.addView(row)
    }

    private fun addIssueRow() {
        val row = LayoutInflater.from(this).inflate(R.layout.item_issue, binding.issueInputContainer, false)
        row.findViewById<ImageButton?>(R.id.addIssueButton)?.setOnClickListener { addIssueRow() }
        binding.issueInputContainer.addView(row)
    }

    private fun addNoteRow() {
        val row = LayoutInflater.from(this).inflate(R.layout.item_note, binding.noteInputContainer, false)
        row.findViewById<ImageButton?>(R.id.addNoteButton)?.setOnClickListener { addNoteRow() }
        binding.noteInputContainer.addView(row)
    }

    // ===== العرض القديم للصور المصغّرة (للتوافق فقط عندما لا يكون القالب محددًا) =====
    private fun renderPhotos(uris: List<Uri>) {
        binding.photoPreviewContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        uris.forEach { uri ->
            val item = inflater.inflate(R.layout.item_photo_preview, binding.photoPreviewContainer, false)
            val thumb: ImageView? = item.findViewById(R.id.photoThumb)
            thumb?.setImageURI(uri)
            binding.photoPreviewContainer.addView(item)
        }
    }

    // ===== واجهة طبقة التحميل/الراية =====
    private fun showUploadOverlay(status: String, percent: Int, indeterminate: Boolean) {
        binding.uploadOverlay.visibility = View.VISIBLE
        binding.uploadStatusText.text = status
        binding.uploadProgressBar.isIndeterminate = indeterminate
        binding.uploadProgressBar.progress = percent.coerceIn(0, 100)
        binding.uploadPercentText.text = if (indeterminate) "..." else "$percent%"
    }

    private fun hideUploadOverlay() {
        binding.uploadOverlay.visibility = View.GONE
    }

    private fun showResultBanner(success: Boolean, text: String) {
        binding.saveResultBanner.visibility = View.VISIBLE
        binding.saveResultText.text = text
        if (success) {
            binding.saveResultBanner.setBackgroundResource(R.drawable.bg_banner_success)
            binding.saveResultIcon.setImageResource(android.R.drawable.checkbox_on_background)
        } else {
            // إن لم تتوفر خلفية مخصصة للأخطاء، نستخدم نفس الخلفية الافتراضية مع أيقونة تحذير
            binding.saveResultBanner.setBackgroundResource(R.drawable.bg_banner_success)
            binding.saveResultIcon.setImageResource(android.R.drawable.ic_dialog_alert)
        }
    }

    private fun setInputsEnabled(enabled: Boolean) {
        binding.saveReportButton.isEnabled = enabled
        binding.addPhotoButton.isEnabled = enabled
        binding.reportDateInput.isEnabled = enabled && (selectedProject != null)
    }
}
