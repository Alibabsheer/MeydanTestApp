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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.DailyReport
import com.example.meydantestapp.report.ReportPdfBuilder
import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.resolveDailyReportSections
import com.example.meydantestapp.view.ReportItem
import com.example.meydantestapp.view.ReportItemsAdapter
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewDailyReportActivity : AppCompatActivity() {

    private lateinit var reportList: RecyclerView
    private lateinit var shareButton: Button
    private lateinit var backButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var viewModel: ViewDailyReportViewModel
    private lateinit var adapter: ReportItemsAdapter

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var currentReport: DailyReport? = null
    private var resolvedReportNumber: String? = null
    private var latestLogo: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_daily_report)

        window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_RTL

        reportList = findViewById(R.id.reportList)
        shareButton = findViewById(R.id.sharePdfButton)
        backButton = findViewById(R.id.backButton)
        progressBar = findViewById(R.id.progressBar)

        viewModel = ViewModelProvider(this)[ViewDailyReportViewModel::class.java]

        adapter = ReportItemsAdapter(
            logoProvider = { latestLogo },
            onLinkClicked = { openProjectLocationLink(it) }
        )

        reportList.layoutManager = LinearLayoutManager(this)
        reportList.itemAnimator = null
        reportList.adapter = adapter

        backButton.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        shareButton.setOnClickListener { onShareClicked() }

        val report: DailyReport? = intent.getParcelableExtra("dailyReport")
            ?: intent.getParcelableExtra("report")

        if (report == null) {
            Toast.makeText(this, R.string.report_error_missing_data, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        currentReport = report
        resolvedReportNumber = report.reportNumber

        viewModel.buildNativeItems(report)
        collectViewModel()

        val explicitOrgId: String? = intent.getStringExtra(Constants.EXTRA_ORGANIZATION_ID)
            ?: intent.getStringExtra("organizationId")
        tryResolveOrganizationLogo(report, explicitOrgId)
    }

    private fun collectViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.nativeItems.collectLatest { items ->
                        adapter.submitList(items)
                    }
                }
                launch {
                    viewModel.logoBitmap.collectLatest { bitmap ->
                        latestLogo = bitmap
                        val index = adapter.currentList.indexOfFirst { it is ReportItem.HeaderLogo }
                        if (index >= 0) {
                            adapter.notifyItemChanged(index)
                        }
                    }
                }
            }
        }
    }

    private fun tryResolveOrganizationLogo(report: DailyReport, explicitOrgId: String?) {
        val orgId = explicitOrgId?.trim()
        if (!orgId.isNullOrEmpty()) {
            viewModel.loadOrganizationLogo(orgId)
            return
        }

        val orgName = report.organizationName?.trim()
        if (orgName.isNullOrEmpty()) {
            viewModel.loadOrganizationLogo(null)
            return
        }

        firestore.collection("organizations")
            .whereEqualTo("name", orgName)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    viewModel.loadOrganizationLogo(snapshot.documents.first().id)
                } else {
                    viewModel.loadOrganizationLogo(null)
                }
            }
            .addOnFailureListener { viewModel.loadOrganizationLogo(null) }
    }

    private fun onShareClicked() {
        val report = currentReport ?: return
        lifecycleScope.launch {
            try {
                setLoading(true)
                shareButton.isEnabled = false

                val pdf = withContext(Dispatchers.IO) {
                    val output = prepareExportFile()
                    val sections = resolveDailyReportSections(
                        activitiesList = report.dailyActivities,
                        machinesList = report.resourcesUsed,
                        obstaclesList = report.challenges,
                        activitiesText = report.activitiesText,
                        machinesText = report.machinesText,
                        obstaclesText = report.obstaclesText
                    )
                    val builderInput = ReportPdfBuilder.DailyReport(
                        projectName = report.projectName,
                        ownerName = report.ownerName,
                        contractorName = report.contractorName,
                        consultantName = report.consultantName,
                        projectAddressText = report.addressText?.trim().takeUnless { it.isNullOrEmpty() },
                        projectGoogleMapsUrl = report.googleMapsUrl,
                        reportNumber = report.reportNumber,
                        dateText = report.date?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) },
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
                        sitepages = report.sitepages
                    )
                    val logo = viewModel.logoBitmap.value
                    ReportPdfBuilder(this@ViewDailyReportActivity).buildPdf(builderInput, logo, output)
                }

                sharePdfFile(pdf)
            } catch (_: Exception) {
                Toast.makeText(this@ViewDailyReportActivity, R.string.report_error_share_pdf, Toast.LENGTH_LONG).show()
            } finally {
                shareButton.isEnabled = true
                setLoading(false)
            }
        }
    }

    private fun sharePdfFile(file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.report_action_share_pdf)))
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
        val number = rawNumber?.let { value ->
            val prefix = "DailyReport-"
            when {
                value.startsWith(prefix) -> value.removePrefix(prefix).trim()
                value.startsWith(prefix, ignoreCase = true) -> value.substring(prefix.length).trim()
                else -> value
            }
        }?.takeIf { it.isNotEmpty() }

        val sanitized = number?.replace(Regex("[^0-9A-Za-z_-]"), "_")?.trim('_')?.takeIf { it.isNotEmpty() }
        if (sanitized != null) {
            return "DailyReport-$sanitized.pdf"
        }
        val fallback = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "DailyReport-$fallback.pdf"
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun openProjectLocationLink(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(this, R.string.report_error_invalid_link, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.ROOT)
        if (uri == null || scheme == null || (scheme != "http" && scheme != "https")) {
            Toast.makeText(this, R.string.report_error_invalid_link, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.report_error_open_location, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatWeatherLine(tempRaw: String?, statusRaw: String?): String? {
        val temperature = normalizeCelsius(tempRaw)
        val status = statusRaw?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            temperature != null && status != null -> getString(R.string.report_weather_line_format, temperature, status)
            temperature != null -> getString(R.string.report_weather_temp_format, temperature)
            status != null -> getString(R.string.report_weather_status_format, status)
            else -> null
        }
    }

    private fun normalizeCelsius(input: String?): String? {
        val src = input?.trim()?.replace('\u00A0'.toString(), " ")?.replace(",", ".") ?: return null
        if (src.isEmpty()) return null
        val regex = Regex("[+-]?\\d+(?:\\.\\d+)?")
        val match = regex.find(src)
        val number = match?.value ?: return null
        val value = number.toDoubleOrNull() ?: return null
        val formatter = if (value % 1.0 == 0.0) DecimalFormat("#") else DecimalFormat("#.#")
        return "${formatter.format(value)} °C"
    }

}
