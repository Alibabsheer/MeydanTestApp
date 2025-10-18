package com.example.meydantestapp

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.jvm.JvmSuppressWildcards

class ViewDailyReportViewModel(application: Application) : AndroidViewModel(application) {

    private val storage by lazy { FirebaseStorage.getInstance() }

    private val _logoBitmap = MutableStateFlow<Bitmap?>(null)
    val logoBitmap: StateFlow<Bitmap?> = _logoBitmap.asStateFlow()

    private val _nativeItems = MutableStateFlow<List<ReportItem>>(emptyList())
    val nativeItems: StateFlow<List<ReportItem>> = _nativeItems.asStateFlow()

    private var lastOrgId: String? = null
    private var cachedLogo: Bitmap? = null
    private var currentLogoJob: Job? = null

    private val appContext = application.applicationContext
    private val numberFormatter: NumberFormat = NumberFormat.getInstance(Locale.getDefault())

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

        val workforceEntries = buildWorkforceEntries(report)
        if (workforceEntries.isNotEmpty()) {
            items += ReportItem.Workforce(workforceEntries)
        }

        val sitePages = buildSitePages(report)
        if (sitePages.isNotEmpty()) {
            items += ReportItem.SectionTitle(level = 2, titleRes = R.string.report_section_site_pages)
            items += sitePages
        }

        val photos = report.photos.orEmpty()
            .mapNotNull { raw ->
                val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                runCatching { Uri.parse(normalized) }.getOrNull()
            }

        if (photos.isNotEmpty()) {
            items += ReportItem.SectionTitle(level = 2, titleRes = R.string.report_section_photos)
            items += photos.map { uri -> ReportItem.Photo(uri) }
        }

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

    private fun buildWorkforceEntries(report: DailyReport): List<String> {
        val context = appContext
        val entries = mutableListOf<String>()
        report.skilledLabor?.let {
            entries += context.getString(
                R.string.report_workforce_entry_format,
                context.getString(R.string.report_workforce_label_skilled),
                numberFormatter.format(it)
            )
        }
        report.unskilledLabor?.let {
            entries += context.getString(
                R.string.report_workforce_entry_format,
                context.getString(R.string.report_workforce_label_unskilled),
                numberFormatter.format(it)
            )
        }
        report.totalLabor?.let {
            entries += context.getString(
                R.string.report_workforce_entry_format,
                context.getString(R.string.report_workforce_label_total),
                numberFormatter.format(it)
            )
        }
        return entries
    }

    private fun buildSitePages(report: DailyReport): List<ReportItem.SitePage> {
        val meta = report.sitepagesmeta
        return report.sitepages.orEmpty()
            .mapIndexedNotNull { index, raw ->
                val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
                val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return@mapIndexedNotNull null
                val caption = resolveSitePageCaption(index, meta)
                ReportItem.SitePage(uri, caption)
            }
    }

    private fun resolveSitePageCaption(
        index: Int,
        meta: List<@JvmSuppressWildcards Map<String, Any?>>?,
    ): String? {
        if (meta.isNullOrEmpty()) return null
        val pageMeta = meta.withIndex().firstNotNullOfOrNull { (metaIndex, entry) ->
            val pageIndex = (entry["pageIndex"] as? Number)?.toInt()
            when {
                pageIndex != null && pageIndex == index -> entry
                pageIndex == null && metaIndex == index -> entry
                else -> null
            }
        }
        val directCaption = (pageMeta?.get("caption") as? String)?.trim()
        if (!directCaption.isNullOrEmpty()) {
            return directCaption
        }
        val slots = pageMeta?.get("slots")
        if (slots is Iterable<*>) {
            val slotCaption = slots.firstNotNullOfOrNull { slot ->
                val slotMap = slot as? Map<*, *> ?: return@firstNotNullOfOrNull null
                val slotIndex = (slotMap["slotIndex"] as? Number)?.toInt()
                val caption = (slotMap["caption"] as? String)?.trim()
                when {
                    caption.isNullOrEmpty() -> null
                    slotIndex == null || slotIndex == index -> caption
                    else -> null
                }
            }
            if (!slotCaption.isNullOrEmpty()) {
                return slotCaption
            }
        }
        return null
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
