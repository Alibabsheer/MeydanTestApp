package com.example.meydantestapp.ui.viewmodels.report

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.data.repository.AuthRepository
import com.example.meydantestapp.data.repository.DailyReportRepository
import com.example.meydantestapp.utils.Constants
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import com.example.meydantestapp.ui.viewmodels.BaseViewModel

/**
 * DailyReportsViewModel
 * - يجلب التقارير اليومية من DailyReportRepository بالمسار الهرمي.
 * - يرتّب محليًا حسب reportIndex إن وجد (تصاعديًا)، وإلا حسب date تنازليًا.
 */
class DailyReportsViewModel : BaseViewModel() {

    private val authRepository = AuthRepository() // أبقيناه للتوافق إن كان يُستخدم بمكان آخر
    private val auth = FirebaseAuth.getInstance()
    private val dailyReportRepository = DailyReportRepository()

    private val _dailyReports = MutableLiveData<List<Map<String, Any>>>()
    val dailyReports: LiveData<List<Map<String, Any>>> = _dailyReports

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    /** واجهة توافقية: تستقبل projectId فقط وتستخدم UID كـ organizationId */
    fun fetchDailyReports(projectId: String) {
        val orgId = auth.currentUser?.uid
        if (orgId == null) {
            _errorMessage.value = "المستخدم غير مسجّل دخول."
            return
        }
        fetchDailyReports(orgId, projectId)
    }

    /** الواجهة الصريحة الموصى بها (هرمي) */
    fun fetchDailyReports(organizationId: String, projectId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = dailyReportRepository.getDailyReportsByProject(organizationId, projectId)
                if (result.isSuccess) {
                    val list = result.getOrThrow()
                    // ترتيب محلي: إذا وُجد reportIndex نرتّب تصاعديًا، وإلا fallback على date تنازليًا
                    val withIdx = list.filter { it[Constants.FIELD_REPORT_INDEX] != null }
                        .sortedBy { (it[Constants.FIELD_REPORT_INDEX] as Number).toLong() }
                    val withoutIdx = list.filter { it[Constants.FIELD_REPORT_INDEX] == null }
                        .sortedByDescending { (it["date"] as? Number)?.toLong() ?: 0L }
                    _dailyReports.value = withIdx + withoutIdx
                } else {
                    _errorMessage.value = "فشل في جلب التقارير اليومية: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "حدث خطأ: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** واجهة توافقية للحذف: تستعمل UID كـ organizationId */
    fun deleteDailyReport(reportId: String, projectId: String) {
        val orgId = auth.currentUser?.uid
        if (orgId == null) {
            _errorMessage.value = "المستخدم غير مسجّل دخول."
            return
        }
        deleteDailyReport(orgId, projectId, reportId)
    }

    /** الواجهة الصريحة للحذف (هرمي) */
    fun deleteDailyReport(organizationId: String, projectId: String, reportId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = dailyReportRepository.deleteDailyReport(organizationId, projectId, reportId)
                if (result.isSuccess) {
                    fetchDailyReports(organizationId, projectId) // إعادة التحميل بعد الحذف
                } else {
                    _errorMessage.value = "فشل في حذف التقرير اليومي: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "حدث خطأ: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
