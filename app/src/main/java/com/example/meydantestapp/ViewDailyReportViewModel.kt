package com.example.meydantestapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.repository.DailyReportRepository
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

/**
 * ViewDailyReportViewModel – عرض تقرير يومي بصور كاملة حسب القالب + شعار المؤسسة.
 *
 * الميزات:
 * 1) loadOrganizationLogo(organizationId): يجلب شعار المؤسسة من التخزين مع كاش الذاكرة ومنع السباق.
 * 2) loadReport(organizationId, projectId, reportId): يجلب بيانات التقرير ويحضّر مصادر العرض
 *    اعتمادًا على الحقلين sitepages و sitepagesmeta.
 * 3) displayPages: قائمة روابط الصور التي يجب عرضها (صفحة كاملة لكل عنصر).
 * 4) usingSitePages: مؤشر هل المصدر يحوي صفحات مركّبة أم لا.
*/
class ViewDailyReportViewModel : ViewModel() {

    // =============== تخزين الشعار =============== //
    private val storage by lazy { FirebaseStorage.getInstance() }

    private val _logo = MutableLiveData<Bitmap?>()
    val logo: LiveData<Bitmap?> = _logo

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var lastOrgId: String? = null
    private var cachedLogo: Bitmap? = null

    private var currentLogoJob: Job? = null

    /**
     * تحميل شعار المؤسسة حسب المعرّف. يستخدم كاش الذاكرة لنفس orgId،
     * ويُلغي أي عملية تحميل سابقة لتجنّب حالات السباق.
     */
    fun loadOrganizationLogo(organizationId: String?) {
        val orgId = organizationId?.trim()
        if (orgId.isNullOrEmpty()) {
            _logo.postValue(null)
            return
        }

        // إعادة استخدام الكاش لنفس المعرف
        if (orgId == lastOrgId && cachedLogo != null) {
            _logo.postValue(cachedLogo)
            return
        }

        // إطلاق مهمة IO جديدة بعد إلغاء السابقة (إن وُجدت)
        viewModelScope.launch(Dispatchers.IO) {
            currentLogoJob?.cancelAndJoin()
            currentLogoJob = launch {
                _isLoading.postValue(true)
                try {
                    val exts = listOf("png", "webp", "jpg", "jpeg")
                    val candidates = buildList {
                        exts.forEach { add("organization_logos/$orgId.$it") }
                        exts.forEach { add("organizations/$orgId/logo.$it") }
                    }

                    var loaded: Bitmap? = null
                    for (path in candidates) {
                        loaded = runCatching {
                            val bytes = storage.reference.child(path).getBytes(MAX_LOGO_BYTES).await()
                            decodeDownsampled(bytes, maxDim = 1024)
                        }.getOrNull()
                        if (loaded != null) break
                    }

                    cachedLogo = loaded
                    lastOrgId = orgId
                    _logo.postValue(loaded)
                } catch (_: Exception) {
                    _logo.postValue(null)
                } finally {
                    _isLoading.postValue(false)
                }
            }
        }
    }

    /** إفراغ كاش الشعار يدويًا (مثلاً عند تبديل الحساب). */
    fun clearLogoCache() {
        cachedLogo = null
        lastOrgId = null
    }

    override fun onCleared() {
        super.onCleared()
        currentLogoJob?.cancel()
        currentReportJob?.cancel()
    }

    // ================== عرض التقرير ================== //
    private val reportsRepo by lazy { DailyReportRepository() }

    private val _report = MutableLiveData<Map<String, Any>?>(null)
    val report: LiveData<Map<String, Any>?> = _report

    private val _sitePages = MutableLiveData<List<String>>(emptyList())
    val sitePages: LiveData<List<String>> = _sitePages

    private val _pagesMeta = MutableLiveData<List<Map<String, Any>>>(emptyList())
    val pagesMeta: LiveData<List<Map<String, Any>>> = _pagesMeta

    private val _displayPages = MutableLiveData<List<String>>(emptyList())
    val displayPages: LiveData<List<String>> = _displayPages

    private val _usingSitePages = MutableLiveData(false)
    val usingSitePages: LiveData<Boolean> = _usingSitePages

    private val _loadingReport = MutableLiveData(false)
    val loadingReport: LiveData<Boolean> = _loadingReport

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    private var currentReportJob: Job? = null

    /**
     * يجلب تقريرًا مفردًا ويجهّز مصادر العرض اعتمادًا على الحقول الجديدة للصفحات.
     */
    fun loadReport(organizationId: String, projectId: String, reportId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // امنع تداخل الطلبات على نفس الشاشة
            currentReportJob?.cancelAndJoin()
            currentReportJob = launch {
                _loadingReport.postValue(true)
                try {
                    val result = reportsRepo.getDailyReportById(organizationId, projectId, reportId)
                    val data = result.getOrElse { throw it }
                    _report.postValue(data)

                    // قراءة الحقول الجديدة فقط
                    val pages = coerceStringList(data["sitepages"])
                    val meta = coerceMetaList(data["sitepagesmeta"]).take(pages.size)

                    _sitePages.postValue(pages)
                    _pagesMeta.postValue(meta)
                    _displayPages.postValue(pages)
                    _usingSitePages.postValue(pages.isNotEmpty())
                } catch (e: Exception) {
                    _message.postValue(e.message ?: "تعذر تحميل التقرير")
                    _report.postValue(null)
                    _sitePages.postValue(emptyList())
                    _pagesMeta.postValue(emptyList())
                    _displayPages.postValue(emptyList())
                    _usingSitePages.postValue(false)
                } finally {
                    _loadingReport.postValue(false)
                }
            }
        }
    }

    // ===================== Helpers ===================== //

    private fun decodeDownsampled(bytes: ByteArray, maxDim: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var inSample = 1
        val w0 = bounds.outWidth
        val h0 = bounds.outHeight
        while (w0 / inSample > maxDim || h0 / inSample > maxDim) inSample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = inSample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (_: Exception) {
        null
    }

    private fun coerceStringList(any: Any?): List<String> {
        val src = any as? List<*> ?: return emptyList()
        return src.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
    }

    private fun coerceMetaList(any: Any?): List<Map<String, Any>> {
        val src = any as? List<*> ?: return emptyList()
        val out = mutableListOf<Map<String, Any>>()
        for (el in src) {
            val m = el as? Map<*, *> ?: continue
            val clean = mutableMapOf<String, Any>()
            for ((k, v) in m) if (k is String && v != null) clean[k] = v
            if (clean.isNotEmpty()) out += clean
        }
        return out
    }

    companion object {
        private const val MAX_LOGO_BYTES = 5_000_000L // 5MB
    }
}
