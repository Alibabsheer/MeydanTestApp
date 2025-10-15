package com.example.meydantestapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.report.ReportInfoEntry
import com.example.meydantestapp.report.ReportPdfBuilder
import com.example.meydantestapp.report.buildReportInfoEntries
import com.example.meydantestapp.utils.DailyReportTextSections
import com.example.meydantestapp.utils.resolveDailyReportSections
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale

private const val SECTION_PLACEHOLDER = "—"

data class DailyReportHeaderUi(
    @StringRes val headingRes: Int,
    val projectName: String?,
    val infoEntries: List<ReportInfoEntry>,
    val locationAddress: String?
)

enum class DailyReportSectionType {
    ACTIVITIES,
    EQUIPMENT,
    OBSTACLES,
    NOTES
}

data class DailyReportSectionUi(
    val type: DailyReportSectionType,
    val paragraphs: List<String>
)

data class DailyReportImageUi(
    val url: String,
    val description: String? = null
)

data class ProjectLocationUi(
    val address: String,
    val url: String?
)

class ViewDailyReportViewModel : ViewModel() {

    private val storage by lazy { FirebaseStorage.getInstance() }

    private val _logo = MutableLiveData<Bitmap?>()
    val logo: LiveData<Bitmap?> = _logo

    private var lastOrgId: String? = null
    private var cachedLogo: Bitmap? = null
    private var currentLogoJob: Job? = null

    private val _header = MutableLiveData<DailyReportHeaderUi?>()
    val header: LiveData<DailyReportHeaderUi?> = _header

    private val _sections = MutableLiveData<List<DailyReportSectionUi>>(emptyList())
    val sections: LiveData<List<DailyReportSectionUi>> = _sections

    private val _images = MutableLiveData<List<DailyReportImageUi>>(emptyList())
    val images: LiveData<List<DailyReportImageUi>> = _images

    private val _projectLocation = MutableLiveData<ProjectLocationUi?>(null)
    val projectLocation: LiveData<ProjectLocationUi?> = _projectLocation
    val projectLocationUrl: LiveData<String?> = Transformations.map(_projectLocation) { it?.url }

    private val _pdfData = MutableLiveData<ReportPdfBuilder.DailyReport?>(null)
    val pdfData: LiveData<ReportPdfBuilder.DailyReport?> = _pdfData

    fun loadOrganizationLogo(organizationId: String?) {
        val orgId = organizationId?.trim()
        if (orgId.isNullOrEmpty()) {
            _logo.postValue(null)
            return
        }

        if (orgId == lastOrgId && cachedLogo != null) {
            _logo.postValue(cachedLogo)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            currentLogoJob?.cancelAndJoin()
            currentLogoJob = launch {
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
                }
            }
        }
    }

    fun clearLogoCache() {
        cachedLogo = null
        lastOrgId = null
    }

    override fun onCleared() {
        super.onCleared()
        currentLogoJob?.cancel()
    }

    fun setReport(report: DailyReport) {
        viewModelScope.launch(Dispatchers.Default) {
            val sections = resolveSections(report)
            val pdfData = createPdfData(report, sections)
            val headerUi = DailyReportHeaderUi(
                headingRes = R.string.report_section_info,
                projectName = report.projectName.normalizedOrNull(),
                infoEntries = buildReportInfoEntries(pdfData),
                locationAddress = pdfData.projectAddressText
            )

            val locationUi = pdfData.projectAddressText?.normalizedOrNull()?.let { address ->
                ProjectLocationUi(
                    address = address,
                    url = pdfData.projectGoogleMapsUrl?.normalizedOrNull()
                )
            }

            val sectionItems = buildSectionItems(sections, report.notes)
            val imageItems = buildImageItems(report)

            _header.postValue(headerUi)
            _sections.postValue(sectionItems)
            _images.postValue(imageItems)
            _projectLocation.postValue(locationUi)
            _pdfData.postValue(pdfData)
        }
    }

    private fun resolveSections(report: DailyReport): DailyReportTextSections {
        return resolveDailyReportSections(
            activitiesList = report.dailyActivities,
            machinesList = report.resourcesUsed,
            obstaclesList = report.challenges,
            activitiesText = report.activitiesText,
            machinesText = report.machinesText,
            obstaclesText = report.obstaclesText
        )
    }

    private fun createPdfData(
        report: DailyReport,
        sections: DailyReportTextSections
    ): ReportPdfBuilder.DailyReport {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateText = report.date?.let { df.format(Date(it)) }
        return ReportPdfBuilder.DailyReport(
            organizationName = report.organizationName,
            projectName = report.projectName,
            ownerName = report.ownerName,
            contractorName = report.contractorName,
            consultantName = report.consultantName,
            projectAddressText = report.addressText.normalizedOrNull(),
            projectGoogleMapsUrl = report.googleMapsUrl?.normalizedOrNull(),
            reportNumber = report.reportNumber,
            dateText = dateText,
            temperatureC = report.temperature,
            weatherCondition = report.weatherStatus,
            weatherText = formatWeatherLine(report.temperature, report.weatherStatus),
            createdBy = report.createdByName?.takeIf { it.isNotBlank() } ?: report.createdBy,
            dailyActivities = report.dailyActivities,
            skilledLabor = report.skilledLabor?.toString(),
            unskilledLabor = report.unskilledLabor?.toString(),
            totalLabor = report.totalLabor?.toString(),
            resourcesUsed = report.resourcesUsed,
            challenges = report.challenges,
            activitiesText = sections.activities,
            machinesText = sections.machines,
            obstaclesText = sections.obstacles,
            notes = report.notes,
            photoUrls = report.photos,
            site_photos = null,
            sitepages = report.sitepages
        )
    }

    private fun buildSectionItems(
        sections: DailyReportTextSections,
        notes: List<String>?
    ): List<DailyReportSectionUi> {
        val items = mutableListOf<DailyReportSectionUi>()
        items += DailyReportSectionUi(
            type = DailyReportSectionType.ACTIVITIES,
            paragraphs = paragraphsFrom(sections.activities)
        )
        items += DailyReportSectionUi(
            type = DailyReportSectionType.EQUIPMENT,
            paragraphs = paragraphsFrom(sections.machines)
        )
        items += DailyReportSectionUi(
            type = DailyReportSectionType.OBSTACLES,
            paragraphs = paragraphsFrom(sections.obstacles)
        )

        val sanitizedNotes = notes
            ?.flatMap { it.split('\n') }
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        if (sanitizedNotes.isNotEmpty()) {
            items += DailyReportSectionUi(
                type = DailyReportSectionType.NOTES,
                paragraphs = sanitizedNotes
            )
        }
        return items
    }

    private fun buildImageItems(report: DailyReport): List<DailyReportImageUi> {
        val seen = LinkedHashSet<String>()
        fun collect(values: List<String>?): List<String> {
            val result = mutableListOf<String>()
            values?.forEach { raw ->
                val normalized = raw.trim().takeIf { it.isNotEmpty() }
                val isHttp = normalized != null && (
                    normalized.startsWith("http://") || normalized.startsWith("https://")
                )
                if (isHttp && seen.add(normalized!!)) {
                    result += normalized
                }
            }
            return result
        }
        val primary = collect(report.photos)
        val secondary = collect(report.sitepages)
        return (primary + secondary).map { DailyReportImageUi(url = it) }
    }

    private fun paragraphsFrom(text: String): List<String> {
        val paragraphs = text
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) {
            return listOf(SECTION_PLACEHOLDER)
        }
        return paragraphs
    }

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

    companion object {
        private const val MAX_LOGO_BYTES: Long = 512 * 1024
    }
}

internal fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun formatWeatherLine(tempRaw: String?, statusRaw: String?): String? {
    val t = normalizeCelsius(tempRaw)
    val s = statusRaw?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        t != null && s != null -> "درجة الحرارة : $t    حالة الطقس : $s"
        t != null -> "درجة الحرارة : $t"
        s != null -> "حالة الطقس : $s"
        else -> null
    }
}

private fun normalizeCelsius(input: String?): String? {
    val src = input?.trim()?.replace('\u00A0'.toString(), " ")?.replace(",", ".") ?: return null
    if (src.isEmpty()) return null
    val regex = Regex("[+-]?\\d+(?:\\.\\d+)?")
    val match = regex.find(src)
    val number = match?.value ?: return null
    val asDouble = number.toDoubleOrNull() ?: return null
    val fmt = if (asDouble % 1.0 == 0.0) {
        DecimalFormat("#").format(asDouble)
    } else {
        DecimalFormat("#.#").format(asDouble)
    }
    return "$fmt °C"
}
