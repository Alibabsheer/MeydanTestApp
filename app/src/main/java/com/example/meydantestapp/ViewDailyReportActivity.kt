package com.example.meydantestapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.report.ReportPdfBuilder
import com.example.meydantestapp.utils.resolveDailyReportSections
import com.example.meydantestapp.view.ReportItem
import com.example.meydantestapp.view.ReportItemsAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewDailyReportActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var sharePdfButton: Button
    private lateinit var backButton: Button
    private var progressBar: ProgressBar? = null

    private lateinit var adapter: ReportItemsAdapter
    private lateinit var viewModel: ViewDailyReportViewModel

    private var currentReport: DailyReport? = null
    private var resolvedReportNumber: String? = null
    private var logoBitmap: Bitmap? = null
    private var exportJob: Job? = null
    private var pdfFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_daily_report)

        window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        recycler = findViewById(R.id.reportList)
        sharePdfButton = findViewById(R.id.sharePdfButton)
        backButton = findViewById(R.id.backButton)
        progressBar = findViewById(R.id.progressBar)

        adapter = ReportItemsAdapter(
            onLinkClicked = { url -> openProjectLocationLink(url) }
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.setHasFixedSize(false)
        recycler.itemAnimator = null
        recycler.adapter = adapter

        backButton.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        sharePdfButton.setOnClickListener { onShareClicked() }
        sharePdfButton.isEnabled = false

        viewModel = ViewModelProvider(this)[ViewDailyReportViewModel::class.java]
        viewModel.logo.observe(this) { bitmap ->
            logoBitmap = bitmap
            adapter.updateLogo(bitmap)
        }

        val report: DailyReport? =
            intent.getParcelableExtra("dailyReport")
                ?: intent.getParcelableExtra("report")

        val explicitOrgId: String? =
            intent.getStringExtra(com.example.meydantestapp.utils.Constants.EXTRA_ORGANIZATION_ID)
                ?: intent.getStringExtra("organizationId")

        if (report == null) {
            Toast.makeText(this, R.string.daily_report_error_missing_report, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        currentReport = report
        resolvedReportNumber = report.reportNumber

        renderReport(report)
        sharePdfButton.isEnabled = true

        tryResolveOrganizationAndLoadLogo(report, explicitOrgId)
    }

    override fun onDestroy() {
        super.onDestroy()
        exportJob?.cancel()
    }

    private fun renderReport(report: DailyReport) {
        val items = mutableListOf<ReportItem>()
        items += ReportItem.HeaderLogo
        items += ReportItem.SectionTitle(level = 1, titleRes = R.string.report_section_info)

        fun addInfo(@androidx.annotation.StringRes labelRes: Int, value: String?, linkUrl: String? = null) {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return
            items += ReportItem.InfoRow(labelRes = labelRes, value = trimmed, linkUrl = linkUrl)
        }

        addInfo(R.string.label_project_name, report.projectName)
        addInfo(R.string.label_report_number, report.reportNumber)
        addInfo(R.string.label_report_date, report.date?.let { formatReportDate(it) })
        addInfo(R.string.label_project_owner, report.ownerName)
        addInfo(R.string.label_project_contractor, report.contractorName)
        addInfo(R.string.label_project_consultant, report.consultantName)
        addInfo(R.string.label_temperature, normalizeCelsius(report.temperature))
        addInfo(R.string.label_weather_status, report.weatherStatus)
        val createdBy = report.createdByName?.takeIf { it.isNotBlank() } ?: report.createdBy
        addInfo(R.string.label_report_created_by, createdBy)
        addInfo(R.string.label_skilled_labor, report.skilledLabor?.toString())
        addInfo(R.string.label_unskilled_labor, report.unskilledLabor?.toString())
        addInfo(R.string.label_total_labor, report.totalLabor?.toString())
        val locationText = report.addressText.normalizedOrNull()
        if (!locationText.isNullOrEmpty()) {
            items += ReportItem.SectionTitle(level = 1, titleRes = R.string.report_section_project_location)
            val mapsUrl = report.googleMapsUrl?.takeIf { it.isNotBlank() }
            items += ReportItem.InfoRow(R.string.label_project_location, locationText, mapsUrl)
        }

        val sections = resolveDailyReportSections(
            activitiesList = report.dailyActivities,
            machinesList = report.resourcesUsed,
            obstaclesList = report.challenges,
            activitiesText = report.activitiesText,
            machinesText = report.machinesText,
            obstaclesText = report.obstaclesText
        )

        fun addSection(titleRes: Int, value: String) {
            val text = value.trim()
            if (text.isEmpty()) return
            items += ReportItem.SectionTitle(level = 1, titleRes = titleRes)
            text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                items += ReportItem.BodyText(line)
            }
        }

        addSection(R.string.report_section_activities, sections.activities)
        addSection(R.string.report_section_equipment, sections.machines)
        addSection(R.string.report_section_obstacles, sections.obstacles)

        report.notes?.filterNotNull()?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { notes ->
            if (notes.isNotEmpty()) {
                items += ReportItem.SectionTitle(level = 2, titleRes = R.string.daily_report_section_notes)
                notes.forEach { items += ReportItem.BodyText(it) }
            }
        }

        report.photos?.mapNotNull { it?.trim()?.takeIf { text -> text.isNotEmpty() } }?.forEach { path ->
            val uri = runCatching { Uri.parse(path) }.getOrNull()
            if (uri != null) {
                items += ReportItem.Photo(uri)
            }
        }

        adapter.submitItems(items)
    }

    private fun onShareClicked() {
        val report = currentReport ?: return
        exportJob?.cancel()
        exportJob = lifecycleScope.launch {
            try {
                showLoading(true)
                val sections = resolveDailyReportSections(
                    activitiesList = report.dailyActivities,
                    machinesList = report.resourcesUsed,
                    obstaclesList = report.challenges,
                    activitiesText = report.activitiesText,
                    machinesText = report.machinesText,
                    obstaclesText = report.obstaclesText
                )

                val dateText = report.date?.let { formatReportDate(it) }
                val builderInput = ReportPdfBuilder.DailyReport(
                    projectName = report.projectName,
                    ownerName = report.ownerName,
                    contractorName = report.contractorName,
                    consultantName = report.consultantName,
                    projectAddressText = report.addressText.normalizedOrNull(),
                    projectGoogleMapsUrl = report.googleMapsUrl,
                    reportNumber = report.reportNumber,
                    dateText = dateText,
                    temperatureC = report.temperature,
                    weatherCondition = report.weatherStatus,
                    weatherText = formatWeatherLine(report.temperature, report.weatherStatus),
                    createdBy = (report.createdByName?.takeIf { it.isNotBlank() } ?: report.createdBy),
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
                    sitepages = report.sitepages
                )

                val out = prepareExportFile()
                withContext(Dispatchers.IO) {
                    ReportPdfBuilder(this@ViewDailyReportActivity).buildPdf(builderInput, logoBitmap, out)
                }
                pdfFile = out
                sharePdfIfAny()
            } catch (_: Exception) {
                Toast.makeText(this@ViewDailyReportActivity, R.string.daily_report_error_share_failed, Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun sharePdfIfAny() {
        val file = pdfFile ?: return
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.daily_report_share_pdf_title)))
    }

    private fun prepareExportFile(): File {
        val reportsDir = File(cacheDir, "reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }
        val fileName = resolveExportFileName()
        val target = File(reportsDir, fileName)
        target.parentFile?.mkdirs()
        if (target.exists() && !target.isDirectory) {
            target.delete()
        }
        return target
    }

    private fun resolveExportFileName(): String {
        val rawNumber = resolvedReportNumber?.trim()?.takeIf { it.isNotEmpty() }
        val numberWithoutPrefix = rawNumber?.let { value ->
            val prefix = "DailyReport-"
            when {
                value.startsWith(prefix) -> value.removePrefix(prefix).trim()
                value.startsWith(prefix, ignoreCase = true) -> value.substring(prefix.length).trim()
                else -> value
            }
        }?.takeIf { it.isNotEmpty() }

        val candidate = numberWithoutPrefix ?: rawNumber
        val sanitizedCandidate = candidate
            ?.replace(Regex("[^0-9A-Za-z_-]"), "_")
            ?.trim('_')
            ?.takeIf { it.isNotEmpty() }

        if (sanitizedCandidate != null) {
            return "DailyReport-$sanitizedCandidate.pdf"
        }

        val fallback = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "DailyReport-$fallback.pdf"
    }

    private fun tryResolveOrganizationAndLoadLogo(report: DailyReport, explicitOrgId: String?) {
        val trimmed = explicitOrgId?.trim()
        if (!trimmed.isNullOrEmpty()) {
            viewModel.loadOrganizationLogo(trimmed)
            return
        }
        val orgName = report.organizationName?.trim()
        if (!orgName.isNullOrEmpty()) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("organizations")
                .whereEqualTo("name", orgName)
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    if (!snap.isEmpty) {
                        val orgId = snap.documents.first().id
                        viewModel.loadOrganizationLogo(orgId)
                    }
                }
                .addOnFailureListener { }
        }
    }

    private fun openProjectLocationLink(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.daily_report_error_open_location, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatReportDate(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date(timestamp))
    }

    private fun showLoading(loading: Boolean) {
        progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
        sharePdfButton.isEnabled = !loading
    }

    private fun formatWeatherLine(tempRaw: String?, statusRaw: String?): String? {
        val t = normalizeCelsius(tempRaw)
        val s = statusRaw?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            t != null && s != null -> getString(R.string.daily_report_weather_format_full, t, s)
            t != null -> getString(R.string.daily_report_weather_format_temperature_only, t)
            s != null -> getString(R.string.daily_report_weather_format_status_only, s)
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
        val fmt = if (asDouble % 1.0 == 0.0) DecimalFormat("#").format(asDouble) else DecimalFormat("#.#").format(asDouble)
        return "$fmt °C"
    }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
