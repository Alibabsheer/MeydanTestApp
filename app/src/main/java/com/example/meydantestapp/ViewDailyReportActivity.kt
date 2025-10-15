package com.example.meydantestapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import com.example.meydantestapp.ui.DailyReportAdapter
import com.example.meydantestapp.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewDailyReportActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var backButton: Button
    private lateinit var sharePdfButton: Button
    private var progressBar: ProgressBar? = null

    private lateinit var viewModel: ViewDailyReportViewModel
    private lateinit var adapter: DailyReportAdapter

    private val db by lazy { FirebaseFirestore.getInstance() }

    private var headerUi: DailyReportHeaderUi? = null
    private var sectionsUi: List<DailyReportSectionUi> = emptyList()
    private var imagesUi: List<DailyReportImageUi> = emptyList()
    private var locationUi: ProjectLocationUi? = null
    private var headerLogo: Bitmap? = null
    private var pdfData: ReportPdfBuilder.DailyReport? = null
    private var pdfFile: File? = null
    private var resolvedReportNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_daily_report)

        window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_RTL

        recycler = findViewById(R.id.reportRecycler)
        backButton = findViewById(R.id.backButton)
        sharePdfButton = findViewById(R.id.sharePdfButton)
        progressBar = findViewById(R.id.progressBar)

        adapter = DailyReportAdapter { url -> openProjectLocationLink(url) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.setHasFixedSize(false)
        recycler.itemAnimator = null
        recycler.adapter = adapter

        backButton.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        sharePdfButton.isEnabled = false
        sharePdfButton.setOnClickListener { onShareClicked() }

        viewModel = ViewModelProvider(this)[ViewDailyReportViewModel::class.java]

        viewModel.header.observe(this) {
            headerUi = it
            refreshAdapter()
        }
        viewModel.sections.observe(this) {
            sectionsUi = it
            refreshAdapter()
        }
        viewModel.images.observe(this) {
            imagesUi = it
            refreshAdapter()
        }
        viewModel.projectLocation.observe(this) {
            locationUi = it
            refreshAdapter()
        }
        viewModel.logo.observe(this) { logoBitmap ->
            headerLogo = logoBitmap
            adapter.updateLogo(logoBitmap)
        }
        viewModel.pdfData.observe(this) { data ->
            pdfData = data
            sharePdfButton.isEnabled = data != null
        }

        val report: DailyReport? =
            intent.getParcelableExtra("dailyReport")
                ?: intent.getParcelableExtra("report")

        val explicitOrgId: String? =
            intent.getStringExtra(Constants.EXTRA_ORGANIZATION_ID)
                ?: intent.getStringExtra("organizationId")

        if (report == null) {
            Toast.makeText(this, "تعذر فتح التقرير: بيانات مفقودة.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        resolvedReportNumber = report.reportNumber
        viewModel.setReport(report)
        tryResolveOrganizationAndLoadLogo(report, explicitOrgId)
    }

    private fun refreshAdapter() {
        adapter.submit(headerUi, locationUi, sectionsUi, imagesUi, headerLogo)
    }

    private fun onShareClicked() {
        val data = pdfData ?: run {
            Toast.makeText(this, "لا يوجد تقرير لمشاركته بعد.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                showLoading(true)
                sharePdfButton.isEnabled = false
                val out = prepareExportFile()
                withContext(Dispatchers.IO) {
                    ReportPdfBuilder(this@ViewDailyReportActivity).buildPdf(data, headerLogo, out)
                }
                pdfFile = out
                sharePdfIfAny()
            } catch (_: Exception) {
                pdfFile = null
                Toast.makeText(
                    this@ViewDailyReportActivity,
                    "تعذر توليد/عرض التقرير.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                showLoading(false)
                sharePdfButton.isEnabled = pdfData != null
            }
        }
    }

    private fun sharePdfIfAny() {
        val file = pdfFile ?: return
        val uri: Uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "مشاركة التقرير PDF"))
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
            db.collection("organizations")
                .whereEqualTo("name", orgName)
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    val orgId = snap.documents.firstOrNull()?.id
                    if (!orgId.isNullOrBlank()) {
                        viewModel.loadOrganizationLogo(orgId)
                    } else {
                        viewModel.loadOrganizationLogo(null)
                    }
                }
                .addOnFailureListener { viewModel.loadOrganizationLogo(null) }
            return
        }

        viewModel.loadOrganizationLogo(null)
    }

    private fun openProjectLocationLink(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "لا يمكن فتح رابط الموقع.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoading(loading: Boolean) {
        progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
