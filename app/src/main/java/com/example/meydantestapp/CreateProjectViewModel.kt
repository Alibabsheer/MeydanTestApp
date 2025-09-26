package com.example.meydantestapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.repository.ProjectRepository
import com.example.meydantestapp.utils.MapLinkUtils
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

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
        location: String,
        latitude: Double?,
        longitude: Double?,
        plusCode: String?,
        addressText: String?,
        localityHint: String?,
        startDateStr: String,
        endDateStr: String,
        workType: String,
        quantitiesTableData: List<QuantityItem>?,
        lumpSumTableData: List<LumpSumItem>?,
        calculatedContractValue: Double?
    ) {
        val organizationId = auth.currentUser?.uid
        if (organizationId.isNullOrEmpty()) {
            _errorMessage.value = "خطأ: المستخدم غير مسجل الدخول."
            return
        }

        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val startTs = try { Timestamp(formatter.parse(startDateStr)!!) } catch (e: Exception) { null }
        val endTs = try { Timestamp(formatter.parse(endDateStr)!!) } catch (e: Exception) { null }

        if (startTs == null || endTs == null) {
            _errorMessage.value = "تأكد من إدخال تواريخ صحيحة."
            return
        }

        val sanitizedAddress = addressText?.trim()?.takeIf { it.isNotEmpty() }
        val sanitizedPlusCode = plusCode?.trim()?.takeIf { it.isNotEmpty() }
        val sanitizedLocality = localityHint?.trim()?.takeIf { it.isNotEmpty() }

        val locationLabel = MapLinkUtils.formatDisplayLabel(
            MapLinkUtils.ProjectLocationInfo(
                latitude = latitude,
                longitude = longitude,
                plusCode = sanitizedPlusCode,
                addressText = sanitizedAddress,
                localityHint = sanitizedLocality,
                displayLabel = location
            )
        ) ?: location.trim().takeIf { it.isNotEmpty() }

        val projectData = hashMapOf<String, Any?>(
            "projectName" to projectName,
            "location" to locationLabel,
            "projectLocation" to locationLabel,
            "latitude" to latitude,
            "longitude" to longitude,
            "lat" to latitude,
            "lng" to longitude,
            "plusCode" to sanitizedPlusCode,
            "plus_code" to sanitizedPlusCode,
            "addressText" to sanitizedAddress,
            "address_text" to sanitizedAddress,
            "localityHint" to sanitizedLocality,
            "locality_hint" to sanitizedLocality,
            "startDate" to startTs,
            "endDate" to endTs,
            "workType" to workType,
            "createdAt" to Timestamp.now()
        )

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
