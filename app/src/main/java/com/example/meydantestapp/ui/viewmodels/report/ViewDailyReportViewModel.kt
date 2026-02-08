package com.example.meydantestapp.ui.viewmodels.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.StringRes
import com.example.meydantestapp.DailyReport
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meydantestapp.utils.resolveDailyReportSections
import com.example.meydantestapp.view.ReportItem
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.meydantestapp.ui.viewmodels.BaseViewModel

class ViewDailyReportViewModel : BaseViewModel() {

    private val storage by lazy { FirebaseStorage.getInstance() }

    private val _logoBitmap = MutableStateFlow<Bitmap?>(null)
    val logoBitmap: StateFlow<Bitmap?> = _logoBitmap.asStateFlow()

    private val _nativeItems = MutableStateFlow<List<ReportItem>>(emptyList())
    val nativeItems: StateFlow<List<ReportItem>> = _nativeItems.asStateFlow()

    private var lastOrgId: String? = null
    private var cachedLogo: Bitmap? = null
    private var currentLogoJob: Job? = null

    fun buildNativeItems(report: DailyReport?) {
        val placeholder = REPORT_PLACEHOLDER
        val items = mutableListOf<ReportItem>()
        items += ReportItem.HeaderLogo

        if (report == null) {
            _nativeItems.value = items
            return
        }

        val infoRows = listOf(
            infoRow(R.string.label_project_name, report.projectName),
            infoRow(R.string.label_project_owner, report.ownerName),
            infoRow(R.string.label_project_contractor, report.contractorName),
            infoRow(R.string.label_project_consultant, report.consultantName),
            infoRow(R.string.label_report_number, report.reportNumber),
            infoRow(R.string.label_report_date, formatReportDate(report.date)),
            infoRow(R.string.label_temperature, normalizeCelsius(report.temperature)),
            infoRow(R.string.label_weather_status, report.weatherStatus),
            infoRow(
                labelRes = R.string.label_project_location,
                value = report.addressText,
                link = report.googleMapsUrl
            ),
            infoRow(
                labelRes = R.string.label_report_created_by,
                value = report.createdByName?.takeIf { it.isNotBlank() }
                    ?: report.createdBy
            )
        )

        items += ReportItem.SectionTitle(level = 1, titleRes = R.string.report_section_info)
        items += infoRows.map { row ->
            val hasValue = row.value.isNotBlank()
            val value = if (hasValue) row.value else placeholder
            val link = row.link.takeIf { hasValue }
            ReportItem.InfoRow(row.labelRes, value, link)
        }

        val sections = resolveDailyReportSections(
            activitiesList = report.dailyActivities,
            machinesList = report.resourcesUsed,
            obstaclesList = report.challenges,
            activitiesText = report.activitiesText,
            machinesText = report.machinesText,
            obstaclesText = report.obstaclesText
        )

        items += ReportItem.SectionTitle(level = 2, titleRes = R.string.report_section_activities)
        items += ReportItem.BodyText(sections.activities.ifBlank { placeholder })
        items += ReportItem.SectionTitle(level = 2, titleRes = R.string.report_section_equipment)
        items += ReportItem.BodyText(sections.machines.ifBlank { placeholder })
        items += ReportItem.SectionTitle(level = 2, titleRes = R.string.report_section_obstacles)
        items += ReportItem.BodyText(sections.obstacles.ifBlank { placeholder })

        val photos = report.photos.orEmpty()
            .mapNotNull { raw ->
                val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                runCatching { Uri.parse(normalized) }.getOrNull()
            }
        items += photos.map { uri -> ReportItem.Photo(uri) }

        _nativeItems.value = items
    }

    fun loadOrganizationLogo(organizationId: String?) {
        val orgId = organizationId?.trim()
        if (orgId.isNullOrEmpty()) {
            _logoBitmap.value = null
            return
        }

        if (orgId == lastOrgId && cachedLogo != null) {
            _logoBitmap.value = cachedLogo
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            currentLogoJob?.cancelAndJoin()
            currentLogoJob = launch {
                try {
                    val logo = fetchOrganizationLogo(orgId)
                    cachedLogo = logo
                    lastOrgId = orgId
                    _logoBitmap.value = logo
                } catch (_: Exception) {
                    _logoBitmap.value = null
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

    private suspend fun fetchOrganizationLogo(orgId: String): Bitmap? {
        val extensions = listOf("png", "webp", "jpg", "jpeg")
        val paths = buildList {
            extensions.forEach { ext -> add("organization_logos/$orgId.$ext") }
            extensions.forEach { ext -> add("organizations/$orgId/logo.$ext") }
        }

        paths.forEach { path ->
            val bitmap = runCatching {
                val bytes = storage.reference.child(path).getBytes(MAX_LOGO_BYTES).await()
                decodeDownsampled(bytes, maxDim = 1024)
            }.getOrNull()
            if (bitmap != null) {
                return bitmap
            }
        }

        return null
    }

    private fun decodeDownsampled(bytes: ByteArray, maxDim: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var inSample = 1
        while (bounds.outWidth / inSample > maxDim || bounds.outHeight / inSample > maxDim) {
            inSample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = inSample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (_: Exception) {
        null
    }

    private fun infoRow(@StringRes labelRes: Int, value: String?, link: String? = null): InfoRowData {
        val normalizedValue = value?.trim().orEmpty()
        val normalizedLink = link?.trim()?.takeIf { it.isNotEmpty() }
        return InfoRowData(labelRes, normalizedValue, normalizedLink)
    }

    private fun formatReportDate(timestamp: Long?): String? {
        if (timestamp == null) return null
        return runCatching {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            formatter.format(java.util.Date(timestamp))
        }.getOrNull()
    }

    private fun normalizeCelsius(input: String?): String? {
        val src = input?.trim()?.replace('\u00A0'.toString(), " ")?.replace(",", ".") ?: return null
        if (src.isEmpty()) return null
        val regex = Regex("[+-]?\\d+(?:\\.\\d+)?")
        val match = regex.find(src) ?: return null
        val value = match.value.toDoubleOrNull() ?: return null
        val format = if (value % 1.0 == 0.0) DecimalFormat("#") else DecimalFormat("#.#")
        return "${format.format(value)} °C"
    }

    private data class InfoRowData(
        @StringRes val labelRes: Int,
        val value: String,
        val link: String?
    )

    companion object {
        private const val REPORT_PLACEHOLDER = "—"
        private const val MAX_LOGO_BYTES = 5_000_000L
    }
}
