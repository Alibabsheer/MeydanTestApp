package com.example.meydantestapp

import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.*
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.meydantestapp.models.PhotoEntry
import com.example.meydantestapp.models.PhotoTemplates
import com.example.meydantestapp.models.TemplateId
import com.example.meydantestapp.repository.DailyReportRepository
import com.example.meydantestapp.repository.DailyReportUploadSpec
import com.example.meydantestapp.repository.DailyReportUploadWorker
import com.example.meydantestapp.repository.WeatherRepository
import com.example.meydantestapp.utils.ImageUtils
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * CreateDailyReportViewModel – إدارة الحالة والتخزين للتقرير اليومي.
 *
 * - يُكوّن صفحة صورة **A4 عمودية** حسب القالب (شبكة صور + تعليق لكل خانة) بدون قصّ داخل الخانات (Fit-Inside).
 * - يحفظ الصفحة كـ WebP بجودة عالية، ويرفعها إلى Storage تحت المسار:
 *   daily_reports/{reportId}/pages/page_XXX.webp
 * - يخزن الحقول الجديدة: sitepages, sitepagesmeta. ويحافظ على حقل photos للتوافق عند عدم استخدام القوالب.
 */
class CreateDailyReportViewModel(app: Application) : AndroidViewModel(app) {

    // ===== Dependencies =====
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val weatherRepo by lazy { WeatherRepository() }
    private val reportsRepo by lazy { DailyReportRepository() }
    private val workManager by lazy { WorkManager.getInstance(getApplication()) }
    private val draftStorage by lazy { DailyReportDraftStorage(getApplication()) }

    private var currentUploadWorkId: UUID? = null
    private var uploadObservationJob: Job? = null
    private var lastSaveArgs: SaveReportArgs? = null

    private data class UploadOutcome(
        val urls: List<String>,
        val cleanupUris: List<String>
    )

    private data class SaveReportArgs(
        val organizationId: String,
        val projectId: String,
        val activities: List<String>?,
        val skilledLabor: String?,
        val unskilledLabor: String?,
        val totalLabor: String?,
        val equipmentList: List<String>?,
        val challengesList: List<String>?,
        val notesList: List<String>?
    )

    // ===== UI State عامة =====
    private val _date = MutableLiveData<String?>()
    val date: LiveData<String?> = _date

    private val _temperature = MutableLiveData<String?>()
    val temperature: LiveData<String?> = _temperature

    private val _weatherStatus = MutableLiveData<String?>()
    val weatherStatus: LiveData<String?> = _weatherStatus

    // التدفق القديم للصور (قائمة URIs)
    private val _photos = MutableLiveData<List<Uri>>(emptyList())
    val photos: LiveData<List<Uri>> = _photos

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _uploadProgress = MutableLiveData(0)
    val uploadProgress: LiveData<Int> = _uploadProgress

    private val _uploadStatusMessage = MutableLiveData("جاري رفع الصور...")
    val uploadStatusMessage: LiveData<String> = _uploadStatusMessage

    private val _uploadIndeterminate = MutableLiveData(true)
    val uploadIndeterminate: LiveData<Boolean> = _uploadIndeterminate

    private val _uploadCancelable = MutableLiveData(false)
    val uploadCancelable: LiveData<Boolean> = _uploadCancelable

    private val _uploadResumable = MutableLiveData(false)
    val uploadResumable: LiveData<Boolean> = _uploadResumable

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

    // ===== الصور (التدفق القديم – ضغط وتجهيز مفرد) =====
    fun addImage(resolver: ContentResolver, uri: Uri, projectName: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decodeMaxDim = 1280
                val bmp = ImageUtils.decodeBitmap(resolver, uri, maxDim = decodeMaxDim) ?: return@launch
                val prepared = ImageUtils.prepareHorizontalWithOverlay(
                    getApplication(), bmp, 1280, 720, null, null
                )
                val compressedUri = ImageUtils.compressToCacheJpeg(getApplication(), prepared, 92)
                withContext(Dispatchers.Main) {
                    _photos.value = _photos.value!! + compressedUri
                }
            } catch (_: Exception) {
                _message.postValue("فشل في معالجة إحدى الصور.")
            }
        }
    }

    fun addImages(resolver: ContentResolver, uris: List<Uri>, projectName: String?, maxPhotos: Int = 10) {
        if (uris.isEmpty()) return
        val current = _photos.value?.size ?: 0
        if (current >= maxPhotos) {
            _message.value = "تم بلوغ الحد الأقصى ($maxPhotos) من الصور."
            return
        }
        val remain = maxPhotos - current
        val take = uris.take(remain)
        take.forEach { addImage(resolver, it, projectName) }
        if (uris.size > take.size) _message.value = "تم بلوغ الحد الأقصى ($maxPhotos) من الصور."
    }

    fun clearPhotos() { _photos.value = emptyList() }

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
        val argsSnapshot = SaveReportArgs(
            organizationId = organizationId,
            projectId = projectId,
            activities = activities?.toList(),
            skilledLabor = skilledLabor,
            unskilledLabor = unskilledLabor,
            totalLabor = totalLabor,
            equipmentList = equipmentList?.toList(),
            challengesList = challengesList?.toList(),
            notesList = notesList?.toList()
        )
        lastSaveArgs = argsSnapshot
        _uploadResumable.value = false

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
            uploadObservationJob?.cancel()
            uploadObservationJob = null
            currentUploadWorkId = null
            _uploadStatusMessage.postValue("جاري تجهيز الصور للرفع...")
            _uploadIndeterminate.postValue(true)
            _uploadCancelable.postValue(false)
            _uploadResumable.postValue(false)
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

                // 1) مفاتيح الاستئناف المحلية
                val storageKey = DailyReportDraftStorage.storageKey(organizationId, projectId, dateMillis)
                val gridDraft = draftStorage.getDraft(storageKey, DailyReportUploadSpec.GRID_PAGE)
                val legacyDraft = draftStorage.getDraft(storageKey, DailyReportUploadSpec.LEGACY_PHOTO)
                var reportId: String = gridDraft?.reportId?.takeIf { it.isNotBlank() }
                    ?: legacyDraft?.reportId?.takeIf { it.isNotBlank() }
                    ?: draftStorage.findExistingReportId(storageKey)?.takeIf { it.isNotBlank() }
                    ?: System.currentTimeMillis().toString()

                var staged = false

                suspend fun ensureStage(): Boolean {
                    if (staged) return true
                    val baseData = mapOf(
                        "id" to reportId,
                        "projectId" to projectId,
                        "organizationId" to organizationId,
                        "date" to dateMillis,
                        "createdAt" to Timestamp.now(),
                        "createdBy" to (auth.currentUser?.uid ?: ""),
                        "isArchived" to false
                    )
                    val result = reportsRepo.stageDailyReportDocument(
                        organizationId,
                        projectId,
                        reportId,
                        baseData
                    )
                    if (result.isSuccess) {
                        staged = true
                        return true
                    }
                    _message.postValue("تعذر بدء رفع الصور.")
                    _saveState.postValue(SaveState.FailureOther)
                    _uploadResumable.postValue(false)
                    return false
                }

                // 2) حصر العناصر المملوءة في القالب
                val filledEntries: List<PhotoEntry> = _gridSlots.value.orEmpty().mapNotNull { it }

                // 3) التكوين/الرفع
                var pageUrls: List<String> = emptyList()
                var pagesMeta: List<Map<String, Any>> = emptyList()
                var photoUrlsLegacy: List<String> = emptyList()

                if (filledEntries.isNotEmpty()) {
                    // صفحة مركّبة كاملة بحسب القالب — A4 عمودي بدون قص داخل الخانات
                    val template = PhotoTemplates.byId(_selectedTemplateId.value ?: TemplateId.E4)

                    val restoredGridUris = restoreDraftUris(gridDraft?.uriStrings.orEmpty()).also {
                        if (gridDraft != null && it.isEmpty()) {
                            draftStorage.clearDraft(storageKey, DailyReportUploadSpec.GRID_PAGE)
                        }
                    }
                    if (!gridDraft?.reportId.isNullOrBlank()) {
                        reportId = gridDraft!!.reportId
                    }

                    var pageUploadUris = restoredGridUris

                    if (pageUploadUris.isEmpty()) {
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
                        pageUploadUris = listOf(pageUri)
                    }

                    // رفع كصفحة واحدة (MVP)
                    if (pageUploadUris.isNotEmpty()) {
                        if (!ensureStage()) {
                            return@launch
                        }
                        val request = reportsRepo.buildUploadWorkRequest(
                            reportId,
                            pageUploadUris,
                            DailyReportUploadSpec.GRID_PAGE
                        )
                        if (request != null) {
                            val outcome = enqueueAndAwaitUpload(
                                request,
                                queuedMessage = "بانتظار الاتصال لإعادة رفع الصفحة...",
                                runningMessage = "جاري رفع الصفحة...",
                                storageKey = storageKey,
                                reportId = reportId,
                                target = DailyReportUploadSpec.GRID_PAGE,
                                originalUris = pageUploadUris
                            )
                            pageUrls = outcome.urls
                            deleteCacheFilesSafely(outcome.cleanupUris)
                        }
                    }

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
                } else {
                    // مسار قديم: رفع الصور كما هي إذا وُجدت
                    val restoredLegacyUris = restoreDraftUris(legacyDraft?.uriStrings.orEmpty()).also {
                        if (legacyDraft != null && it.isEmpty()) {
                            draftStorage.clearDraft(storageKey, DailyReportUploadSpec.LEGACY_PHOTO)
                        }
                    }
                    if (!legacyDraft?.reportId.isNullOrBlank()) {
                        reportId = legacyDraft!!.reportId
                    }

                    val preparedUploadUris: List<Uri> = when {
                        restoredLegacyUris.isNotEmpty() -> restoredLegacyUris
                        !_photos.value.isNullOrEmpty() -> _photos.value!!
                        else -> {
                            val fromGridUris = currentGridUris()
                            if (fromGridUris.isEmpty()) emptyList() else prepareForUpload(fromGridUris)
                        }
                    }
                    if (preparedUploadUris.isNotEmpty()) {
                        if (!ensureStage()) {
                            return@launch
                        }
                        val request = reportsRepo.buildUploadWorkRequest(
                            reportId,
                            preparedUploadUris,
                            DailyReportUploadSpec.LEGACY_PHOTO
                        )
                        if (request != null) {
                            val outcome = enqueueAndAwaitUpload(
                                request,
                                queuedMessage = "بانتظار الاتصال لإعادة رفع الصور...",
                                runningMessage = "جاري رفع الصور...",
                                storageKey = storageKey,
                                reportId = reportId,
                                target = DailyReportUploadSpec.LEGACY_PHOTO,
                                originalUris = preparedUploadUris
                            )
                            photoUrlsLegacy = outcome.urls
                            deleteCacheFilesSafely(outcome.cleanupUris)
                        }
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

                    // توافق قديم
                    "photos" to (if (photoUrlsLegacy.isEmpty()) null else photoUrlsLegacy),

                    // الحقول الجديدة (إن وُجدت صفحات)
                    "sitepages" to (if (pageUrls.isEmpty()) null else pageUrls),
                    "sitepagesmeta" to (if (pagesMeta.isEmpty()) null else pagesMeta),

                    // الالتقاط وقت الإنشاء
                    "organizationName" to orgNameSnap,
                    "createdByName" to createdByNameSnap
                ).forEach { (k, v) -> if (v != null) data[k] = v }

                // 7) حفظ الوثيقة
                _saveState.postValue(SaveState.Saving)
                _uploadIndeterminate.postValue(false)
                _uploadProgress.postValue(maxOf(_uploadProgress.value ?: 0, 96))

                val write = reportsRepo.createDailyReportAutoNumbered(organizationId, projectId, reportId, data)
                if (write.isSuccess) {
                    _uploadProgress.postValue(100)
                    _saveState.postValue(SaveState.Success)
                    _lastReportId.postValue(reportId)
                    _message.postValue("تم حفظ التقرير بنجاح.")
                    draftStorage.clearAll(storageKey)
                    lastSaveArgs = null
                    _uploadResumable.postValue(false)
                } else {
                    _saveState.postValue(SaveState.FailureOther)
                    _message.postValue("فشل حفظ التقرير.")
                }

            } catch (e: CancellationException) {
                _saveState.postValue(SaveState.Paused)
                _uploadResumable.postValue(true)
                _message.postValue("تم إيقاف رفع الصور. يمكنك الاستئناف لاحقًا.")
            } catch (e: Exception) {
                if (e is FirebaseNetworkException) {
                    _saveState.postValue(SaveState.FailureNetwork)
                    _message.postValue("فشل حفظ التقرير بسبب ضعف الانترنت، يرجى المحاولة مرة أخرى")
                } else {
                    _saveState.postValue(SaveState.FailureOther)
                    val reason = e.message ?: "سبب غير معروف"
                    _message.postValue("فشل حفظ التقرير: $reason")
                }
                _uploadResumable.postValue(false)
            } finally {
                _loading.postValue(false)
                uploadObservationJob?.cancel()
                uploadObservationJob = null
                currentUploadWorkId = null
                _uploadCancelable.postValue(false)
            }
        }
    }

    // ===== Helpers =====
    private fun observeUpload(workId: UUID, queuedMessage: String, runningMessage: String) {
        uploadObservationJob?.cancel()
        uploadObservationJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { info ->
                when (info.state) {
                    WorkInfo.State.ENQUEUED -> {
                        _uploadStatusMessage.postValue(queuedMessage)
                        _uploadIndeterminate.postValue(true)
                        _uploadCancelable.postValue(true)
                        _uploadProgress.postValue(0)
                    }
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt(DailyReportUploadWorker.KEY_PROGRESS, 0)
                        _uploadStatusMessage.postValue(runningMessage)
                        _uploadIndeterminate.postValue(false)
                        _uploadCancelable.postValue(true)
                        _uploadProgress.postValue(progress.coerceIn(0, 100))
                    }
                    WorkInfo.State.SUCCEEDED,
                    WorkInfo.State.CANCELLED,
                    WorkInfo.State.FAILED -> {
                        _uploadCancelable.postValue(false)
                        return@collect
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun enqueueAndAwaitUpload(
        request: OneTimeWorkRequest,
        queuedMessage: String,
        runningMessage: String,
        storageKey: String,
        reportId: String,
        target: DailyReportUploadSpec,
        originalUris: List<Uri>
    ): UploadOutcome {
        observeUpload(request.id, queuedMessage, runningMessage)
        currentUploadWorkId = request.id
        workManager.cancelAllWorkByTag(reportsRepo.uploadWorkTag(reportId, target))
        workManager.enqueue(request)

        val finalInfo = workManager.getWorkInfoByIdFlow(request.id).first { it.state.isFinished }

        uploadObservationJob?.cancel()
        uploadObservationJob = null
        currentUploadWorkId = null

        val originalUriStrings = originalUris.map { it.toString() }.distinct()

        return when (finalInfo.state) {
            WorkInfo.State.SUCCEEDED -> {
                _uploadStatusMessage.postValue("تم رفع الصور بنجاح")
                _uploadIndeterminate.postValue(false)
                _uploadProgress.postValue(100)
                _uploadCancelable.postValue(false)
                _uploadResumable.postValue(false)
                draftStorage.clearDraft(storageKey, target)
                UploadOutcome(
                    urls = reportsRepo.extractUploadedUrls(finalInfo.outputData),
                    cleanupUris = reportsRepo.extractCleanupUris(finalInfo.outputData)
                )
            }
            WorkInfo.State.CANCELLED -> {
                _uploadStatusMessage.postValue("تم إيقاف عملية الرفع. اضغط استئناف للمتابعة.")
                _uploadIndeterminate.postValue(false)
                _uploadCancelable.postValue(false)
                _uploadResumable.postValue(true)
                draftStorage.saveDraft(storageKey, target, reportId, originalUriStrings)
                throw CancellationException("upload-cancelled")
            }
            WorkInfo.State.FAILED -> {
                _uploadStatusMessage.postValue("تعذر رفع الصور")
                _uploadIndeterminate.postValue(false)
                _uploadCancelable.postValue(false)
                _uploadResumable.postValue(false)
                val reason = reportsRepo.extractUploadError(finalInfo.outputData)
                draftStorage.saveDraft(storageKey, target, reportId, originalUriStrings)
                throw IllegalStateException(reason ?: "upload-failed")
            }
            else -> UploadOutcome(emptyList(), emptyList())
        }
    }

    fun cancelOngoingUpload() {
        val workId = currentUploadWorkId ?: return
        workManager.cancelWorkById(workId)
        _uploadCancelable.postValue(false)
        _uploadStatusMessage.postValue("جاري إيقاف عملية الرفع...")
    }

    fun resumeUpload() {
        if (_loading.value == true) return
        val args = lastSaveArgs ?: run {
            _message.value = "لا يوجد رفع متوقف لاستئنافه."
            return
        }
        saveReport(
            organizationId = args.organizationId,
            projectId = args.projectId,
            activities = args.activities?.toList(),
            skilledLabor = args.skilledLabor,
            unskilledLabor = args.unskilledLabor,
            totalLabor = args.totalLabor,
            equipmentList = args.equipmentList?.toList(),
            challengesList = args.challengesList?.toList(),
            notesList = args.notesList?.toList()
        )
    }

    override fun onCleared() {
        super.onCleared()
        uploadObservationJob?.cancel()
        uploadObservationJob = null
    }

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
    private suspend fun prepareForUpload(source: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val out = mutableListOf<Uri>()
        source.forEach { uri ->
            try {
                val decodeMaxDim = 1280
                val bmp = ImageUtils.decodeBitmap(resolver, uri, maxDim = decodeMaxDim) ?: return@forEach
                val prepared = ImageUtils.prepareHorizontalWithOverlay(
                    getApplication(), bmp, 1280, 720, null, null
                )
                val compressed = ImageUtils.compressToCacheJpeg(getApplication(), prepared, 92)
                out += compressed
            } catch (_: Exception) { /* تخطي العنصر المعطوب */ }
        }
        out
    }

    private fun restoreDraftUris(raw: List<String>): List<Uri> {
        if (raw.isEmpty()) return emptyList()
        return raw.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .distinctBy { it.toString() }
            .filter { uri -> isUriUsable(uri) }
    }

    private fun isUriUsable(uri: Uri): Boolean {
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).exists() } == true
            else -> true
        }
    }

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
                val rectLongest = max(rect.width(), rect.height())
                val decodeMaxDim = (rectLongest * 1.2f).roundToInt().coerceIn(256, max(pageWidth, pageHeight))
                val src = ImageUtils.decodeBitmap(resolver, Uri.parse(srcUri), maxDim = decodeMaxDim)
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

    private fun deleteCacheFilesSafely(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        val ctx = getApplication<Application>()
        val cacheDir = ctx.cacheDir
        val authority = "${'$'}{ctx.packageName}.fileprovider"

        uriStrings.distinct().forEach { raw ->
            if (raw.isBlank()) return@forEach
            try {
                val uri = Uri.parse(raw)
                val targetFile = when (uri.scheme) {
                    ContentResolver.SCHEME_FILE -> uri.path?.let { File(it) }
                    ContentResolver.SCHEME_CONTENT -> {
                        if (uri.authority == authority) {
                            val relative = uri.path?.trimStart('/') ?: return@forEach
                            if (relative.startsWith("images/") || relative.startsWith("pages/")) {
                                File(cacheDir, relative)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }
                    else -> null
                }
                if (targetFile != null && targetFile.exists() && targetFile.isFile && isInside(cacheDir, targetFile)) {
                    runCatching { targetFile.delete() }
                }
            } catch (_: Exception) {
                // تجاهل أخطاء الحذف لتجنب التعطل
            }
        }

        listOf("images", "pages").forEach { subDirName ->
            try {
                val dir = File(cacheDir, subDirName)
                val files = dir.listFiles()
                if (files != null && files.isEmpty()) {
                    dir.delete()
                }
            } catch (_: Exception) {
                // تجاهل تنظيف المجلد عند حدوث مشكلة
            }
        }
    }

    private fun isInside(parent: File, child: File): Boolean {
        return try {
            val parentPath = parent.canonicalPath
            val childPath = child.canonicalPath
            childPath.startsWith(parentPath)
        } catch (_: Exception) {
            false
        }
    }
}

class DailyReportDraftStorage(context: Context) {

    data class DraftRecord(
        val reportId: String,
        val uriStrings: List<String>
    )

    data class CachedSitePage(
        val reportId: String,
        val pageIndex: Int,
        val remoteUrl: String,
        val localPath: String,
        val updatedAt: Long
    ) {
        fun resolveFile(cacheDir: File): File = File(cacheDir, localPath)
    }

    companion object {
        private const val DB_NAME = "daily_report_drafts.db"
        private const val DB_VERSION = 2

        private const val TABLE_NAME = "report_upload_drafts"
        private const val COL_STORAGE_KEY = "storage_key"
        private const val COL_TARGET = "target"
        private const val COL_REPORT_ID = "report_id"
        private const val COL_URIS = "uris"
        private const val COL_UPDATED_AT = "updated_at"

        private const val TABLE_SITE_CACHE = "site_page_cache"
        private const val COL_SITE_REPORT_ID = "report_id"
        private const val COL_SITE_PAGE_INDEX = "page_index"
        private const val COL_SITE_REMOTE_URL = "remote_url"
        private const val COL_SITE_LOCAL_PATH = "local_path"
        private const val COL_SITE_UPDATED_AT = "updated_at"

        private const val SITE_PAGES_DIR = "sitepages"
        private const val SAFE_FILE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private const val MAX_REPORT_ID_LENGTH_FOR_FILE = 48
        private const val THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1000L

        fun storageKey(organizationId: String, projectId: String, dateMillis: Long): String {
            return listOf(organizationId, projectId, dateMillis.toString()).joinToString("|")
        }
    }

    private val appContext = context.applicationContext
    private val dbHelper = DraftDbHelper(appContext)

    fun getDraft(storageKey: String, target: DailyReportUploadSpec): DraftRecord? {
        if (storageKey.isBlank()) return null
        val db = dbHelper.readableDatabase
        val selection = "$COL_STORAGE_KEY=? AND $COL_TARGET=?"
        val args = arrayOf(storageKey, target.name)
        db.query(TABLE_NAME, arrayOf(COL_REPORT_ID, COL_URIS), selection, args, null, null, null).use { cursor ->
            if (cursor.moveToFirst()) {
                val reportId = cursor.getString(0)
                val uriPayload = cursor.getString(1)
                val uris = decodeUriPayload(uriPayload)
                if (reportId.isNullOrBlank() || uris.isEmpty()) {
                    return null
                }
                return DraftRecord(reportId, uris)
            }
        }
        return null
    }

    fun cacheSitePageBitmap(
        reportId: String?,
        pageIndex: Int,
        remoteUrl: String?,
        bitmap: Bitmap
    ): CachedSitePage? {
        val safeReportId = reportId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (pageIndex < 0) return null
        val remote = remoteUrl?.takeIf { it.isNotBlank() } ?: ""
        val dir = File(appContext.cacheDir, SITE_PAGES_DIR).apply { mkdirs() }
        val fileName = buildSitePageFileName(safeReportId, pageIndex)
        val outFile = File(dir, fileName)
        try {
            FileOutputStream(outFile).use { fos ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
                bitmap.compress(format, 92, fos)
            }
        } catch (_: Exception) {
            return null
        }

        val relativePath = "$SITE_PAGES_DIR/${outFile.name}"
        val now = System.currentTimeMillis()
        val existing = getCachedSitePageInternal(safeReportId, pageIndex)
        if (existing != null && existing.localPath != relativePath) {
            val oldFile = existing.resolveFile(appContext.cacheDir)
            if (oldFile.exists() && oldFile.isFile) {
                runCatching { oldFile.delete() }
            }
        }

        val values = ContentValues().apply {
            put(COL_SITE_REPORT_ID, safeReportId)
            put(COL_SITE_PAGE_INDEX, pageIndex)
            put(COL_SITE_REMOTE_URL, remote)
            put(COL_SITE_LOCAL_PATH, relativePath)
            put(COL_SITE_UPDATED_AT, now)
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            TABLE_SITE_CACHE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        return CachedSitePage(safeReportId, pageIndex, remote, relativePath, now)
    }

    fun getCachedSitePages(reportId: String?): List<CachedSitePage> {
        val safeReportId = reportId?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val result = mutableListOf<CachedSitePage>()
        val staleIndices = mutableListOf<Int>()
        val db = dbHelper.readableDatabase
        db.query(
            TABLE_SITE_CACHE,
            arrayOf(
                COL_SITE_PAGE_INDEX,
                COL_SITE_REMOTE_URL,
                COL_SITE_LOCAL_PATH,
                COL_SITE_UPDATED_AT
            ),
            "$COL_SITE_REPORT_ID=?",
            arrayOf(safeReportId),
            null,
            null,
            "$COL_SITE_PAGE_INDEX ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val pageIndex = cursor.getInt(0)
                val remote = cursor.getString(1) ?: ""
                val localPath = cursor.getString(2) ?: continue
                val updatedAt = cursor.getLong(3)
                val entry = CachedSitePage(safeReportId, pageIndex, remote, localPath, updatedAt)
                val file = entry.resolveFile(appContext.cacheDir)
                if (file.exists() && file.isFile) {
                    result += entry
                } else {
                    staleIndices += pageIndex
                }
            }
        }
        if (staleIndices.isNotEmpty()) {
            val whereClause = "$COL_SITE_REPORT_ID=? AND $COL_SITE_PAGE_INDEX=?"
            val writable = dbHelper.writableDatabase
            staleIndices.forEach { index ->
                writable.delete(TABLE_SITE_CACHE, whereClause, arrayOf(safeReportId, index.toString()))
            }
        }
        return result
    }

    fun cleanupExpiredSitePages(maxAgeMillis: Long = THIRTY_DAYS_MILLIS) {
        if (maxAgeMillis <= 0L) return
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        val db = dbHelper.writableDatabase
        val expiredEntries = mutableListOf<CachedSitePage>()
        db.query(
            TABLE_SITE_CACHE,
            arrayOf(
                COL_SITE_REPORT_ID,
                COL_SITE_PAGE_INDEX,
                COL_SITE_REMOTE_URL,
                COL_SITE_LOCAL_PATH,
                COL_SITE_UPDATED_AT
            ),
            "$COL_SITE_UPDATED_AT<?",
            arrayOf(cutoff.toString()),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val report = cursor.getString(0) ?: continue
                val pageIndex = cursor.getInt(1)
                val remote = cursor.getString(2) ?: ""
                val localPath = cursor.getString(3) ?: continue
                val updatedAt = cursor.getLong(4)
                expiredEntries += CachedSitePage(report, pageIndex, remote, localPath, updatedAt)
            }
        }
        if (expiredEntries.isEmpty()) return
        val whereClause = "$COL_SITE_REPORT_ID=? AND $COL_SITE_PAGE_INDEX=?"
        expiredEntries.forEach { entry ->
            val file = entry.resolveFile(appContext.cacheDir)
            if (file.exists() && file.isFile) {
                runCatching { file.delete() }
            }
            db.delete(
                TABLE_SITE_CACHE,
                whereClause,
                arrayOf(entry.reportId, entry.pageIndex.toString())
            )
        }
    }

    fun clearSitePages(reportId: String?) {
        val safeReportId = reportId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val entries = getCachedSitePages(safeReportId)
        entries.forEach { entry ->
            val file = entry.resolveFile(appContext.cacheDir)
            if (file.exists() && file.isFile) {
                runCatching { file.delete() }
            }
        }
        dbHelper.writableDatabase.delete(
            TABLE_SITE_CACHE,
            "$COL_SITE_REPORT_ID=?",
            arrayOf(safeReportId)
        )
    }

    fun findExistingReportId(storageKey: String): String? {
        if (storageKey.isBlank()) return null
        val db = dbHelper.readableDatabase
        db.query(
            TABLE_NAME,
            arrayOf(COL_REPORT_ID),
            "$COL_STORAGE_KEY=?",
            arrayOf(storageKey),
            null,
            null,
            "$COL_UPDATED_AT DESC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    fun saveDraft(
        storageKey: String,
        target: DailyReportUploadSpec,
        reportId: String?,
        uriStrings: List<String>
    ) {
        if (storageKey.isBlank() || reportId.isNullOrBlank()) return
        if (uriStrings.isEmpty()) {
            clearDraft(storageKey, target)
            return
        }
        val payload = JSONArray().apply {
            uriStrings.forEach { put(it) }
        }.toString()
        val values = ContentValues().apply {
            put(COL_STORAGE_KEY, storageKey)
            put(COL_TARGET, target.name)
            put(COL_REPORT_ID, reportId)
            put(COL_URIS, payload)
            put(COL_UPDATED_AT, System.currentTimeMillis())
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun clearDraft(storageKey: String, target: DailyReportUploadSpec) {
        if (storageKey.isBlank()) return
        dbHelper.writableDatabase.delete(
            TABLE_NAME,
            "$COL_STORAGE_KEY=? AND $COL_TARGET=?",
            arrayOf(storageKey, target.name)
        )
    }

    fun clearAll(storageKey: String) {
        if (storageKey.isBlank()) return
        dbHelper.writableDatabase.delete(
            TABLE_NAME,
            "$COL_STORAGE_KEY=?",
            arrayOf(storageKey)
        )
    }

    private fun decodeUriPayload(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i)
                    if (!value.isNullOrBlank()) add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getCachedSitePageInternal(reportId: String, pageIndex: Int): CachedSitePage? {
        val db = dbHelper.readableDatabase
        db.query(
            TABLE_SITE_CACHE,
            arrayOf(COL_SITE_REMOTE_URL, COL_SITE_LOCAL_PATH, COL_SITE_UPDATED_AT),
            "$COL_SITE_REPORT_ID=? AND $COL_SITE_PAGE_INDEX=?",
            arrayOf(reportId, pageIndex.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val remote = cursor.getString(0) ?: ""
                val localPath = cursor.getString(1) ?: return null
                val updatedAt = cursor.getLong(2)
                return CachedSitePage(reportId, pageIndex, remote, localPath, updatedAt)
            }
        }
        return null
    }

    private fun buildSitePageFileName(reportId: String, pageIndex: Int): String {
        val sanitizedId = reportId.take(MAX_REPORT_ID_LENGTH_FOR_FILE).map { ch ->
            if (SAFE_FILE_CHARS.indexOf(ch) >= 0) ch else '_'
        }.joinToString("")
        return "sitepage_${sanitizedId}_${pageIndex}.webp"
    }

    private class DraftDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    $COL_STORAGE_KEY TEXT NOT NULL,
                    $COL_TARGET TEXT NOT NULL,
                    $COL_REPORT_ID TEXT NOT NULL,
                    $COL_URIS TEXT NOT NULL,
                    $COL_UPDATED_AT INTEGER NOT NULL,
                    PRIMARY KEY($COL_STORAGE_KEY, $COL_TARGET)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SITE_CACHE (
                    $COL_SITE_REPORT_ID TEXT NOT NULL,
                    $COL_SITE_PAGE_INDEX INTEGER NOT NULL,
                    $COL_SITE_REMOTE_URL TEXT NOT NULL,
                    $COL_SITE_LOCAL_PATH TEXT NOT NULL,
                    $COL_SITE_UPDATED_AT INTEGER NOT NULL,
                    PRIMARY KEY($COL_SITE_REPORT_ID, $COL_SITE_PAGE_INDEX)
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2 && newVersion >= 2) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS $TABLE_SITE_CACHE (
                        $COL_SITE_REPORT_ID TEXT NOT NULL,
                        $COL_SITE_PAGE_INDEX INTEGER NOT NULL,
                        $COL_SITE_REMOTE_URL TEXT NOT NULL,
                        $COL_SITE_LOCAL_PATH TEXT NOT NULL,
                        $COL_SITE_UPDATED_AT INTEGER NOT NULL,
                        PRIMARY KEY($COL_SITE_REPORT_ID, $COL_SITE_PAGE_INDEX)
                    )
                    """.trimIndent()
                )
            }
            if (oldVersion > newVersion) {
                onCreate(db)
            }
        }
    }
}

enum class SaveState {
    Idle,
    Uploading,
    Saving,
    Paused,
    Success,
    FailureNetwork,
    FailureOther
}

// ------- Extensions -------
private fun <T> List<T>?.orElseEmpty(): List<T> = this ?: emptyList()
