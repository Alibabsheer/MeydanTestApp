package com.example.meydantestapp

import android.app.Application
import android.content.ContentResolver
import android.graphics.*
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.models.PhotoEntry
import com.example.meydantestapp.models.PhotoTemplates
import com.example.meydantestapp.models.TemplateId
import com.example.meydantestapp.repository.DailyReportRepository
import com.example.meydantestapp.repository.WeatherRepository
import com.example.meydantestapp.utils.ImageUtils
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * CreateDailyReportViewModel – إدارة الحالة والتخزين للتقرير اليومي.
 *
 * - يُكوّن صفحة صورة **A4 عمودية** حسب القالب (شبكة صور + تعليق لكل خانة) بدون قصّ داخل الخانات (Fit-Inside).
 * - يحفظ الصفحة كـ WebP بجودة عالية، ويرفعها إلى Storage تحت المسار:
 *   daily_reports/{reportId}/pages/page_XXX.webp
 * - يخزن الحقول الجديدة: sitepages, sitepagesmeta.
 */
class CreateDailyReportViewModel(app: Application) : AndroidViewModel(app) {

    // ===== Dependencies =====
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val weatherRepo by lazy { WeatherRepository() }
    private val reportsRepo by lazy { DailyReportRepository() }

    // ===== UI State عامة =====
    private val _date = MutableLiveData<String?>()
    val date: LiveData<String?> = _date

    private val _temperature = MutableLiveData<String?>()
    val temperature: LiveData<String?> = _temperature

    private val _weatherStatus = MutableLiveData<String?>()
    val weatherStatus: LiveData<String?> = _weatherStatus

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _uploadProgress = MutableLiveData(0)
    val uploadProgress: LiveData<Int> = _uploadProgress

    private val _saveState = MutableLiveData(SaveState.Idle)
    val saveState: LiveData<SaveState> = _saveState

    private val _lastReportId = MutableLiveData<String?>()
    val lastReportId: LiveData<String?> = _lastReportId

    // بيانات المشروع الخام
    private val _projectInfo = MutableLiveData<Map<String, Any>?>(null)
    val projectInfo: LiveData<Map<String, Any>?> = _projectInfo

    // أسماء إضافية تُلتقط وقت الإنشاء
    private val _organizationName = MutableLiveData<String?>(null)
    val organizationName: LiveData<String?> = _organizationName

    private val _createdByName = MutableLiveData<String?>(null)
    val createdByName: LiveData<String?> = _createdByName

    // ===== جمع العمالة لحظيًا =====
    private val _skilledLaborInput = MutableLiveData("")
    val skilledLaborInput: LiveData<String> = _skilledLaborInput

    private val _unskilledLaborInput = MutableLiveData("")
    val unskilledLaborInput: LiveData<String> = _unskilledLaborInput

    // الإجمالي المحسوب
    private val _totalLaborComputed = MediatorLiveData<Int?>().apply { value = null }
    val totalLaborComputed: LiveData<Int?> = _totalLaborComputed

    // نسخة نصية للإظهار
    private val _totalLaborText = MediatorLiveData<String?>().apply { value = null }
    val totalLaborText: LiveData<String?> = _totalLaborText

    // ===== شبكة القوالب (صفحة واحدة حسب القالب المختار) =====
    private val _selectedTemplateId = MutableLiveData<TemplateId?>(TemplateId.E4)
    val selectedTemplateId: LiveData<TemplateId?> = _selectedTemplateId

    private val _gridSlots = MutableLiveData<List<PhotoEntry?>>(emptyList())
    val gridSlots: LiveData<List<PhotoEntry?>> = _gridSlots

    init {
        _totalLaborComputed.addSource(_skilledLaborInput) { recomputeTotal() }
        _totalLaborComputed.addSource(_unskilledLaborInput) { recomputeTotal() }
        _totalLaborText.addSource(_totalLaborComputed) { sum -> _totalLaborText.value = sum?.toString() }

        selectTemplate(TemplateId.E4)
    }

    // ===== قوالب الشبكات =====
    fun selectTemplate(templateId: TemplateId) {
        _selectedTemplateId.value = templateId
        val slotsCount = PhotoTemplates.byId(templateId).slots.size
        _gridSlots.value = List(slotsCount) { null }
    }

    fun selectedTemplateSlotsCount(): Int =
        PhotoTemplates.byId(_selectedTemplateId.value ?: TemplateId.E4).slots.size

    fun clearGrid() { _gridSlots.value = List(selectedTemplateSlotsCount()) { null } }

    fun setSlotImage(slotIndex: Int, uri: Uri) {
        val tpl = _selectedTemplateId.value ?: TemplateId.E4
        val list = _gridSlots.value?.toMutableList() ?: return
        if (slotIndex !in list.indices) return
        val old = list[slotIndex]
        list[slotIndex] = PhotoEntry(
            templateId = tpl,
            pageIndex = 0,
            slotIndex = slotIndex,
            localUri = uri.toString(),
            caption = old?.caption
        )
        _gridSlots.value = list
    }

    fun setSlotCaption(slotIndex: Int, caption: String?) {
        val list = _gridSlots.value?.toMutableList() ?: return
        if (slotIndex !in list.indices) return
        val old = list[slotIndex]
        if (old != null) {
            list[slotIndex] = old.copy(caption = caption?.trim()?.take(PhotoEntry.MAX_CAPTION_CHARS))
            _gridSlots.value = list
        }
    }

    fun removeSlot(slotIndex: Int) {
        val list = _gridSlots.value?.toMutableList() ?: return
        if (slotIndex !in list.indices) return
        list[slotIndex] = null
        _gridSlots.value = list
    }

    fun currentGridUris(): List<Uri> = _gridSlots.value
        ?.mapNotNull { it?.localUri ?: it?.originalUrl }
        ?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        ?: emptyList()

    // ===== ضبط معلومات عامة =====
    fun setProjectInfo(info: Map<String, Any>) { _projectInfo.value = info }
    fun setDate(dateIso: String) { _date.value = dateIso }

    fun setOrganizationName(name: String?) { _organizationName.value = name?.trim()?.takeIf { it.isNotEmpty() } }

    fun setCreatedByName(name: String?) {
        val fromArg = name?.trim()?.takeIf { it.isNotEmpty() }
        _createdByName.value = fromArg ?: auth.currentUser?.displayName
    }

    // ===== الطقس =====
    fun fetchWeatherFor(dateIso: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            try {
                val res = weatherRepo.getDailyWeather(lat, lng, dateIso)
                _temperature.value = res.temperatureMax?.toString()
                _weatherStatus.value = res.weatherDescription
            } catch (_: Exception) {
                _temperature.value = null
                _weatherStatus.value = null
                _message.value = "تعذر جلب حالة الطقس."
            }
        }
    }

    // ===== حفظ التقرير =====
    fun saveReport(
        organizationId: String,
        projectId: String,
        activities: List<String>?,
        skilledLabor: String?,
        unskilledLabor: String?,
        totalLabor: String?,
        equipmentList: List<String>?,
        challengesList: List<String>?,
        notesList: List<String>?
    ) {
        val currentDateIso = _date.value
        val cleanedActivities = activities?.map { it.trim() }?.filter { it.isNotBlank() }.orElseEmpty()
        if (currentDateIso.isNullOrBlank() || cleanedActivities.isEmpty()) {
            _message.value = "تأكد من تعبئة تاريخ التقرير ونشاط واحد على الأقل."
            return
        }
        val dateMillis = isoToStartOfDayMillis(currentDateIso)

        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            _uploadProgress.postValue(0)
            _saveState.postValue(SaveState.Uploading)
            try {
                fun String?.nullIfBlank(): String? = if (this.isNullOrBlank()) null else this

                // 0) منع التكرار
                val exists = reportsRepo.existsReportForDate(organizationId, projectId, dateMillis)
                if (exists.isSuccess && exists.getOrNull() == true) {
                    _message.postValue("يوجد تقرير محفوظ لهذا المشروع في نفس التاريخ.")
                    _loading.postValue(false)
                    _saveState.postValue(SaveState.Idle)
                    return@launch
                }

                // 1) معرّف التقرير
                val reportId = System.currentTimeMillis().toString()

                // 2) حصر العناصر المملوءة في القالب
                val filledEntries: List<PhotoEntry> = _gridSlots.value.orEmpty().mapNotNull { it }

                // 3) التكوين/الرفع
                var pageUrls: List<String> = emptyList()
                var pagesMeta: List<Map<String, Any>> = emptyList()

                if (filledEntries.isNotEmpty()) {
                    // صفحة مركّبة كاملة بحسب القالب — A4 عمودي بدون قص داخل الخانات
                    val template = PhotoTemplates.byId(_selectedTemplateId.value ?: TemplateId.E4)

                    // قياس آمن للذاكرة (A4 عمودي تقريبًا): 1480x2100px
                    val pageBitmap = ImageUtils.composeA4PortraitPage(
                        context = getApplication(),
                        entries = filledEntries,
                        template = template,
                        pageW = 1480,
                        pageH = 2100,
                        projectName = (_projectInfo.value?.get("projectName") ?: _projectInfo.value?.get("name")) as? String,
                        reportDate = currentDateIso,
                        headerEnabled = false // الصفحة نفسها ستوضع داخل مساحة الصور في ال PDF
                    )

                    val pageUri = compressToCacheWebp(pageBitmap, quality = 92)

                    // رفع كصفحة واحدة (MVP)
                    pageUrls = reportsRepo.uploadGridPages(reportId, listOf(pageUri)) { p ->
                        _uploadProgress.postValue(p.coerceIn(0, 95))
                    }.getOrElse { throw it }

                    pagesMeta = listOf(
                        mapOf(
                            "templateId" to (template.id.name),
                            "pageIndex" to 0,
                            "slots" to filledEntries.map {
                                mapOf(
                                    "slotIndex" to it.slotIndex,
                                    "caption" to (it.caption ?: ""),
                                    "originalUrl" to null
                                )
                            }
                        )
                    )

                    if (pageUrls.size < pagesMeta.size) {
                        pagesMeta = pagesMeta.take(pageUrls.size)
                    }
                }

                // 4) العمالة
                val s = skilledLabor?.trim()?.toIntOrNull() ?: _skilledLaborInput.value?.toIntOrNull()
                val u = unskilledLabor?.trim()?.toIntOrNull() ?: _unskilledLaborInput.value?.toIntOrNull()
                val totalFromArgs = totalLabor?.trim()?.toIntOrNull()
                val totalFromComputed = _totalLaborComputed.value
                val total = totalFromArgs ?: totalFromComputed ?: run { if (s != null || u != null) (s ?: 0) + (u ?: 0) else null }

                // 5) حقول المشروع
                val projectData = _projectInfo.value.orEmpty()
                val projectName = (projectData["projectName"] ?: projectData["name"]) as? String
                val projectNumber = projectData["projectNumber"]?.toString()?.nullIfBlank()
                val location = projectData["location"]?.toString()?.nullIfBlank()
                val latitude = when (val v = projectData["latitude"]) {
                    is Number -> v.toDouble()
                    is String -> v.toDoubleOrNull()
                    else -> null
                }
                val longitude = when (val v = projectData["longitude"]) {
                    is Number -> v.toDouble()
                    is String -> v.toDoubleOrNull()
                    else -> null
                }

                // 6) بناء البيانات الأساسية
                val data = mutableMapOf<String, Any>(
                    "id" to reportId,
                    "projectId" to projectId,
                    "organizationId" to organizationId,
                    "date" to dateMillis,
                    "createdAt" to Timestamp.now(),
                    "createdBy" to (auth.currentUser?.uid ?: ""),
                    "isArchived" to false
                )

                val orgNameSnap = _organizationName.value?.nullIfBlank()
                val createdByNameSnap = _createdByName.value?.nullIfBlank()

                listOf(
                    // من المشروع
                    "projectName" to projectName,
                    "projectNumber" to projectNumber,
                    "location" to location,
                    "latitude" to latitude,
                    "longitude" to longitude,

                    // من الطقس/الواجهة
                    "temperature" to _temperature.value.nullIfBlank(),
                    "weatherStatus" to _weatherStatus.value.nullIfBlank(),

                    // من المدخلات
                    "dailyActivities" to cleanedActivities.ifEmpty { null },
                    "skilledLabor" to s,
                    "unskilledLabor" to u,
                    "totalLabor" to total,
                    "resourcesUsed" to (equipmentList?.map { it.trim() }.orEmpty().filter { it.isNotBlank() }.ifEmpty { null }),
                    "challenges" to (challengesList?.map { it.trim() }.orEmpty().filter { it.isNotBlank() }.ifEmpty { null }),
                    "notes" to (notesList?.map { it.trim() }.orEmpty().filter { it.isNotBlank() }.ifEmpty { null }),

                    // الحقول الجديدة (إن وُجدت صفحات)
                    "sitepages" to (if (pageUrls.isEmpty()) null else pageUrls),
                    "sitepagesmeta" to (if (pagesMeta.isEmpty()) null else pagesMeta),

                    // الالتقاط وقت الإنشاء
                    "organizationName" to orgNameSnap,
                    "createdByName" to createdByNameSnap
                ).forEach { (k, v) -> if (v != null) data[k] = v }

                // 7) حفظ الوثيقة
                _saveState.postValue(SaveState.Saving)
                _uploadProgress.postValue(96)

                val write = reportsRepo.createDailyReportAutoNumbered(organizationId, projectId, reportId, data)
                if (write.isSuccess) {
                    _uploadProgress.postValue(100)
                    _saveState.postValue(SaveState.Success)
                    _lastReportId.postValue(reportId)
                    _message.postValue("تم حفظ التقرير بنجاح.")
                } else {
                    _saveState.postValue(SaveState.FailureOther)
                    _message.postValue("فشل حفظ التقرير.")
                }

            } catch (e: Exception) {
                if (e is FirebaseNetworkException) {
                    _saveState.postValue(SaveState.FailureNetwork)
                    _message.postValue("فشل حفظ التقرير بسبب ضعف الانترنت، يرجى المحاولة مرة أخرى")
                } else {
                    _saveState.postValue(SaveState.FailureOther)
                    val reason = e.message ?: "سبب غير معروف"
                    _message.postValue("فشل حفظ التقرير: $reason")
                }
            } finally {
                _loading.postValue(false)
            }
        }
    }

    // ===== Helpers =====
    private fun recomputeTotal() {
        val sRaw = _skilledLaborInput.value
        val uRaw = _unskilledLaborInput.value
        val s = sRaw?.toIntOrNull()
        val u = uRaw?.toIntOrNull()
        val bothBlank = sRaw.isNullOrBlank() && uRaw.isNullOrBlank()
        _totalLaborComputed.value = if (bothBlank) null else (s ?: 0) + (u ?: 0)
    }

    private fun normalizeNumeric(text: CharSequence?): String =
        text?.toString()?.replace(Regex("[^\\d]"), "") ?: ""

    fun onSkilledChanged(text: CharSequence?) { _skilledLaborInput.value = normalizeNumeric(text) }
    fun onUnskilledChanged(text: CharSequence?) { _unskilledLaborInput.value = normalizeNumeric(text) }

    private fun isoToStartOfDayMillis(iso: String): Long {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return df.parse(iso)?.time ?: 0L
    }

    /**
     * يجهّز مجموعة URIs للرفع: decode → resize/overlay → compress إلى ملف مؤقت
     * ويُرجع Uri للملف الناتج لكل عنصر. (مسار قديم)
     */
    // ===== تجميع صفحة القالب (احتياطي داخلي لو لزم) =====
    // ملاحظة: نُبقي هذه النسخة للاستفادة بها إذا احتجنا تخصيصًا خاصًا داخل الـ VM.
    private fun renderGridPageBitmap(
        resolver: ContentResolver,
        entries: List<PhotoEntry>,
        columns: Int,
        pageWidth: Int = 1480,   // A4 عمودي تقريبي
        pageHeight: Int = 2100,
        outerMargin: Float = 28f,
        gutter: Float = 14f,
        cellCornerRadius: Float = 16f
    ): Bitmap {
        val rows = ceil(entries.size / columns.toFloat()).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val contentLeft = outerMargin
        val contentTop = outerMargin
        val contentRight = pageWidth - outerMargin
        val contentBottom = pageHeight - outerMargin
        val contentW = contentRight - contentLeft
        val contentH = contentBottom - contentTop

        val cellW = (contentW - gutter * (columns - 1)) / columns
        val cellH = (contentH - gutter * (rows - 1)) / rows

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cellBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(245, 245, 245) }
        val cellStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = max(1f, pageWidth * 0.0012f)
        }

        val captionTypeface = ImageUtils.appTypeface(getApplication())
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textAlign = Paint.Align.LEFT
            textSize = max(18f, pageWidth * 0.016f)
            captionTypeface?.let { typeface = it }
        }

        entries.forEachIndexed { idx, entry ->
            val r = idx / columns
            val c = idx % columns

            val left = contentLeft + c * (cellW + gutter)
            val top = contentTop + r * (cellH + gutter)
            val rect = RectF(left, top, left + cellW, top + cellH)

            // خلفية وحدّ للخلية
            canvas.drawRoundRect(rect, cellCornerRadius, cellCornerRadius, cellBg)
            canvas.drawRoundRect(rect, cellCornerRadius, cellCornerRadius, cellStroke)

            val srcUri = entry.localUri ?: entry.originalUrl
            if (!srcUri.isNullOrBlank()) {
                val src = ImageUtils.decodeBitmap(resolver, Uri.parse(srcUri), maxDim = max(pageWidth, pageHeight))
                if (src != null) {
                    // Fit-Inside داخل الخلية بدون قص
                    val scale = min(rect.width() / src.width.toFloat(), rect.height() / src.height.toFloat())
                    val dw = src.width * scale
                    val dh = src.height * scale
                    val dx = rect.centerX() - dw / 2f
                    val dy = rect.centerY() - dh / 2f
                    val dest = RectF(dx, dy, dx + dw, dy + dh)

                    val save = canvas.save()
                    val clip = Path().apply { addRoundRect(rect, cellCornerRadius, cellCornerRadius, Path.Direction.CW) }
                    canvas.clipPath(clip)
                    canvas.drawBitmap(src, null, dest, imagePaint)
                    canvas.restoreToCount(save)
                    src.recycle()
                }
            }

            // تعليق بسيط أسفل الخلية (سطر واحد)
            val caption = entry.caption?.trim()?.takeIf { it.isNotEmpty() }
            if (caption != null) {
                val pad = max(10f, pageWidth * 0.01f)
                val maxW = rect.width() - pad * 2
                var size = captionPaint.textSize
                while (size > 12f && captionPaint.measureText(caption) > maxW) {
                    size *= 0.96f
                    captionPaint.textSize = size
                }
                val baseY = rect.bottom - pad - captionPaint.fontMetrics.descent
                canvas.drawText(caption, rect.left + pad, baseY, captionPaint)
            }
        }
        return bmp
    }

    private fun compressToCacheWebp(bitmap: Bitmap, quality: Int = 92): Uri {
        val ctx = getApplication<Application>()
        val dir = File(ctx.cacheDir, "pages").apply { mkdirs() }
        val out = File(dir, "PAGE_${'$'}{System.currentTimeMillis()}.webp")
        FileOutputStream(out).use { fos ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            bitmap.compress(format, quality.coerceIn(80, 100), fos)
        }
        return Uri.fromFile(out)
    }
}

enum class SaveState {
    Idle, Uploading, Saving, Success, FailureNetwork, FailureOther
}

// ------- Extensions -------
private fun <T> List<T>?.orElseEmpty(): List<T> = this ?: emptyList()
