package com.example.meydantestapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.repository.ProjectRepository
import com.example.meydantestapp.utils.FirestoreTimestampConverter
import com.example.meydantestapp.utils.AppLogger
import com.example.meydantestapp.utils.ProjectLocationUtils
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * ViewModel مسؤول عن إنشاء مشروع جديد عبر ProjectRepository.
 * يبني خريطة المشروع بنفس مفاتيح CreateNewProjectActivity الحالية للحفاظ على الاتساق.
 */
class CreateProjectViewModel(
    private val repository: ProjectRepository = ProjectRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _createSuccess = MutableLiveData<String>() // projectId
    val createSuccess: LiveData<String> = _createSuccess

    /**
     * ينشئ مشروعًا جديدًا.
     * - workType: "جدول كميات" أو "مقطوعية"
     * - quantitiesTableData / lumpSumTableData: تُمرَّر كما هي (QuantityItem / LumpSumItem)
     */
    fun createProject(
        projectName: String,
        addressText: String?,
        latitude: Double?,
        longitude: Double?,
        startDateStr: String,
        endDateStr: String,
        workType: String,
        quantitiesTableData: List<QuantityItem>?,
        lumpSumTableData: List<LumpSumItem>?,
        calculatedContractValue: Double?,
        plusCode: String?
    ) {
        val organizationId = auth.currentUser?.uid
        if (organizationId.isNullOrEmpty()) {
            _errorMessage.value = "خطأ: المستخدم غير مسجل الدخول."
            return
        }

        val startTs = FirestoreTimestampConverter.fromAny(startDateStr)
        val endTs = FirestoreTimestampConverter.fromAny(endDateStr)

        if (startTs == null || endTs == null) {
            _errorMessage.value = "تأكد من إدخال تواريخ صحيحة."
            return
        }

        AppLogger.i(
            "CreateProject",
            "Normalized project dates start=${startTs.seconds} end=${endTs.seconds}"
        )

        val normalizedAddress = ProjectLocationUtils.normalizeAddressText(addressText)
        val normalizedPlusCode = ProjectLocationUtils.normalizePlusCode(plusCode)
        val projectData = hashMapOf<String, Any?>(
            "projectName" to projectName,
            "location" to normalizedAddress,
            "addressText" to normalizedAddress,
            "latitude" to latitude,
            "longitude" to longitude,
            "plusCode" to normalizedPlusCode,
            "startDate" to startTs,
            "endDate" to endTs,
            "workType" to workType,
            "createdAt" to Timestamp.now()
        )

        ProjectLocationUtils.buildGoogleMapsUrl(latitude, longitude)?.let { url ->
            projectData["googleMapsUrl"] = url
        }

        when (workType) {
            "جدول كميات" -> {
                if (quantitiesTableData.isNullOrEmpty() || calculatedContractValue == null) {
                    _errorMessage.value = "الرجاء استيراد ملف جدول الكميات أولاً."
                    return
                }
                projectData["contractValue"] = calculatedContractValue
                projectData["quantitiesTable"] = quantitiesTableData
            }
            "مقطوعية" -> {
                if (lumpSumTableData.isNullOrEmpty() || calculatedContractValue == null) {
                    _errorMessage.value = "الرجاء استيراد ملف عقد المقطوعية أولاً."
                    return
                }
                projectData["contractValue"] = calculatedContractValue
                projectData["lumpSumTable"] = lumpSumTableData
            }
            else -> {
                _errorMessage.value = "الرجاء اختيار نوع العمل (مقطوعية أو جدول كميات)."
                return
            }
        }

        _isLoading.value = true
        viewModelScope.launch {
            val result = repository.createProject(
                organizationId = organizationId,
                projectData = projectData
            )
            _isLoading.value = false
            result.onSuccess { projectId ->
                _createSuccess.value = projectId
            }.onFailure { e ->
                _errorMessage.value = "فشل في إنشاء المشروع: ${e.message}"
            }
        }
    }
}
