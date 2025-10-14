package com.example.meydantestapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import kotlin.math.roundToInt
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.meydantestapp.report.ReportPdfBuilder
import com.example.meydantestapp.repository.DailyReportRepository
import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.ImageUtils
import com.example.meydantestapp.utils.DailyReportTextSections
import com.example.meydantestapp.utils.resolveDailyReportSections
import com.google.firebase.firestore.FirebaseFirestore
import com.otaliastudios.zoom.ZoomLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewDailyReportActivity : AppCompatActivity() {

    // ===== Views =====
    private lateinit var recycler: RecyclerView
    private lateinit var backButton: Button
    private lateinit var sharePdfButton: Button
    private lateinit var projectLocationLabel: TextView
    private lateinit var projectLocationValue: TextView
    private lateinit var activitiesValue: TextView
    private lateinit var machinesValue: TextView
    private lateinit var obstaclesValue: TextView
    private lateinit var sectionsTable: View
    private var progressBar: ProgressBar? = null
    private lateinit var zoomLayout: ZoomLayout
    // ===== VM / Data =====
    private lateinit var viewModel: ViewDailyReportViewModel
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val draftStorage by lazy { DailyReportDraftStorage(applicationContext) }

    // وضع العرض الحالي
    private enum class DisplayMode { SITE_PAGES, PDF_RENDER }
    private var currentMode: DisplayMode? = null

    // ملفات/صفحات
    private var pdfFile: File? = null
    private val pageBitmaps = mutableListOf<Bitmap>() // لعرض PDF المُرندَر
    private val sitePageUrls = mutableListOf<String>() // لعرض صفحات جاهزة حسب القالب
    private var resolvedReportNumber: String? = null

    // ===== Rendering guards =====
    private var alreadyRendered = false
    private var renderJob: Job? = null

    // ===== Zoom config / state =====
    private var initialZoomFromIntent: Float? = null
    private var savedZoom: Float? = null
    private var defaultProjectLocationColor: Int = 0
    private val sectionPlaceholder = "—"

    companion object {
        const val EXTRA_INITIAL_ZOOM = "initial_zoom"
        private const val STATE_ZOOM = "state_zoom"
        private const val DEFAULT_MIN_ZOOM = 0.8f
        private const val DEFAULT_MAX_ZOOM = 5.0f
        private const val DEFAULT_INITIAL_ZOOM = 1.0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_daily_report)

        // دعم RTL على مستوى النافذة
        window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_RTL

        // ===== Bind Views =====
        recycler = findViewById(R.id.pdfPagesRecycler)
        backButton = findViewById(R.id.backButton)
        sharePdfButton = findViewById(R.id.sharePdfButton)
        projectLocationLabel = findViewById(R.id.projectLocationLabel)
        projectLocationValue = findViewById(R.id.projectLocationValue)
        sectionsTable = findViewById(R.id.reportSectionsTable)
        activitiesValue = findViewById(R.id.activitiesValue)
        machinesValue = findViewById(R.id.machinesValue)
        obstaclesValue = findViewById(R.id.obstaclesValue)
        progressBar = findViewById(R.id.progressBar)
        zoomLayout = findViewById(R.id.zoomContainer)
        val reportInfoHeading: TextView = findViewById(R.id.reportInfoHeading)
        val activitiesHeading: TextView = findViewById(R.id.activitiesHeading)
        val equipmentHeading: TextView = findViewById(R.id.equipmentHeading)
        val obstaclesHeading: TextView = findViewById(R.id.obstaclesHeading)
        projectLocationLabel.setText(R.string.heading_project_location)
        reportInfoHeading.setText(R.string.heading_report_info)
        activitiesHeading.setText(R.string.heading_activities)
        equipmentHeading.setText(R.string.heading_equipment)
        obstaclesHeading.setText(R.string.heading_obstacles)
        defaultProjectLocationColor = projectLocationValue.currentTextColor
        // ===== Zoom setup =====
        zoomLayout.setMinZoom(DEFAULT_MIN_ZOOM)
        zoomLayout.setMaxZoom(DEFAULT_MAX_ZOOM)
        initialZoomFromIntent = intent.getFloatExtra(EXTRA_INITIAL_ZOOM, Float.NaN).let { if (it.isNaN()) null else it }
        savedZoom = savedInstanceState?.getFloat(STATE_ZOOM)
        val startZoom = savedZoom ?: initialZoomFromIntent ?: DEFAULT_INITIAL_ZOOM
        zoomLayout.zoomTo(startZoom, false)

        // ===== Recycler setup =====
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.setHasFixedSize(false)
        recycler.itemAnimator = null
        recycler.isNestedScrollingEnabled = true
        recycler.adapter = PdfPagesAdapter(this, pageBitmaps) // افتراضيًا

        // ===== Back / Share =====
        backButton.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
        sharePdfButton.setOnClickListener { onShareClicked() }
        sharePdfButton.isEnabled = false

        // ===== ViewModel =====
        viewModel = ViewModelProvider(this)[ViewDailyReportViewModel::class.java]

        // استلام التقرير من الـIntent
        val report: DailyReport? =
            intent.getParcelableExtra("dailyReport")
                ?: intent.getParcelableExtra("report")

        // استلام orgId لو مُرسَل
        val explicitOrgId: String? =
            intent.getStringExtra(Constants.EXTRA_ORGANIZATION_ID)
                ?: intent.getStringExtra("organizationId")

        if (report == null) {
            Toast.makeText(this, "تعذر فتح التقرير: بيانات مفقودة.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        resolvedReportNumber = report.reportNumber
        updateReportInfoSection(report)

        lifecycleScope.launch {
            val cachedPages = withContext(Dispatchers.IO) {
                val reportId = report.id
                if (reportId.isNullOrBlank()) {
                    emptyList()
                } else {
                    draftStorage.cleanupExpiredSitePages()
                    draftStorage.getCachedSitePages(reportId)
                }
            }

            val fromReport = runCatching {
                DailyReportRepository.normalizePageUrls(report.sitepages)
            }.onFailure {
                Log.w("ViewDailyReportActivity", "normalizePageUrls(report) failed", it)
            }.getOrDefault(emptyList())

            val extraRaw: ArrayList<String>? = intent.getStringArrayListExtra("site_pages")
            val fromExtra = runCatching {
                DailyReportRepository.normalizePageUrls(extraRaw)
            }.onFailure {
                Log.w("ViewDailyReportActivity", "normalizePageUrls(extra) failed", it)
            }.getOrDefault(emptyList())

            val chosenSitePages = when {
                fromReport.isNotEmpty() -> fromReport
                fromExtra.isNotEmpty() -> fromExtra
                else -> emptyList()
            }

            val fallbackFromCache = cachedPages
                .sortedBy { it.pageIndex }
                .mapNotNull { entry ->
                    when {
                        entry.remoteUrl.isNotBlank() -> entry.remoteUrl
                        else -> {
                            val file = entry.resolveFile(cacheDir)
                            if (file.exists() && file.isFile) file.toURI().toString() else null
                        }
                    }
                }

            val urlsForDisplay = when {
                chosenSitePages.isNotEmpty() -> chosenSitePages
                fallbackFromCache.isNotEmpty() -> fallbackFromCache
                else -> emptyList()
            }

            val reportForRendering = if (urlsForDisplay.isNotEmpty()) {
                report.copy(sitepages = urlsForDisplay)
            } else {
                report
            }

            if (urlsForDisplay.isNotEmpty()) {
                sitePageUrls.clear()
                sitePageUrls.addAll(urlsForDisplay)
            }

            updateReportInfoSection(reportForRendering)
            setupPdfRenderFlow(reportForRendering, explicitOrgId)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        runCatching { outState.putFloat(STATE_ZOOM, zoomLayout.zoom) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        renderJob?.cancel()
        pageBitmaps.forEach { it.recycle() }
        pageBitmaps.clear()
    }

    private fun updateReportInfoSection(report: DailyReport?) {
        resolvedReportNumber = report?.reportNumber
        val addressText = report?.addressText.normalizedOrNull()
        val mapsUrl = report?.googleMapsUrl
        renderProjectLocation(addressText, mapsUrl)
        renderReportSections(report)
    }

    private fun renderProjectLocation(addressText: String?, googleMapsUrl: String?) {
        val trimmedAddress = addressText?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedAddress == null) {
            projectLocationLabel.visibility = View.GONE
            projectLocationValue.visibility = View.GONE
            projectLocationValue.text = ""
            projectLocationValue.paintFlags = projectLocationValue.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            projectLocationValue.setTextColor(defaultProjectLocationColor)
            projectLocationValue.setOnClickListener(null)
            projectLocationValue.isClickable = false
            projectLocationValue.isFocusable = false
            return
        }

        projectLocationLabel.visibility = View.VISIBLE
        projectLocationValue.visibility = View.VISIBLE
        projectLocationValue.text = trimmedAddress

        val mapsUrl = googleMapsUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (mapsUrl != null) {
            projectLocationValue.setTextColor(ContextCompat.getColor(this, R.color.hyperlink_blue))
            projectLocationValue.paintFlags = projectLocationValue.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            projectLocationValue.isClickable = true
            projectLocationValue.isFocusable = true
            projectLocationValue.setOnClickListener { openProjectLocationLink(mapsUrl) }
        } else {
            projectLocationValue.setTextColor(defaultProjectLocationColor)
            projectLocationValue.paintFlags = projectLocationValue.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            projectLocationValue.isClickable = false
            projectLocationValue.isFocusable = false
            projectLocationValue.setOnClickListener(null)
        }
    }

    private fun renderReportSections(report: DailyReport?) {
        val sections = if (report != null) {
            resolveDailyReportSections(
                activitiesList = report.dailyActivities,
                machinesList = report.resourcesUsed,
                obstaclesList = report.challenges,
                activitiesText = report.activitiesText,
                machinesText = report.machinesText,
                obstaclesText = report.obstaclesText
            )
        } else {
            DailyReportTextSections.EMPTY
        }

        applySectionValue(activitiesValue, sections.activities)
        applySectionValue(machinesValue, sections.machines)
        applySectionValue(obstaclesValue, sections.obstacles)
    }

    private fun applySectionValue(view: TextView, value: String) {
        val display = value.takeIf { it.isNotBlank() } ?: sectionPlaceholder
        view.text = display
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

    // ---------------------------------------------------------------------
    // 1) Resolve organization & load logo
    // ---------------------------------------------------------------------
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
                    if (!snap.isEmpty) {
                        val orgId = snap.documents.first().id
                        viewModel.loadOrganizationLogo(orgId)
                    } else {
                        renderPdfWithoutLogoIfNotYet(report)
                    }
                }
                .addOnFailureListener { renderPdfWithoutLogoIfNotYet(report) }
            return
        }
        renderPdfWithoutLogoIfNotYet(report)
    }

    private fun setupPdfRenderFlow(report: DailyReport, explicitOrgId: String?) {
        showLoading(true)
        viewModel.logo.observe(this) { logoBitmap ->
            if (!alreadyRendered) {
                alreadyRendered = true
                generateAndDisplayPdf(report, logoBitmap)
            }
        }
        tryResolveOrganizationAndLoadLogo(report, explicitOrgId)
    }

    private fun renderPdfWithoutLogoIfNotYet(report: DailyReport) {
        if (!alreadyRendered && currentMode != DisplayMode.SITE_PAGES) {
            alreadyRendered = true
            generateAndDisplayPdf(report, null)
        }
    }

    // ---------------------------------------------------------------------
    // 2A) Display pre-composed site pages (full images per template)
    // ---------------------------------------------------------------------
    private fun displaySitePages(
        urls: List<String>,
        reportId: String?,
        cachedPages: List<DailyReportDraftStorage.CachedSitePage>
    ) {
        currentMode = DisplayMode.SITE_PAGES
        renderJob?.cancel()
        sitePageUrls.clear()
        sitePageUrls.addAll(urls)
        sharePdfButton.isEnabled = true // يمكن التصدير إلى PDF من هذه الصفحات

        var adapter: SitePagesAdapter? = null
        adapter = SitePagesAdapter(
            context = this,
            scope = lifecycleScope,
            urls = sitePageUrls,
            reportId = reportId,
            cachedPages = cachedPages,
            draftStorage = draftStorage,
            networkChecker = { isNetworkAvailable() }
        ) { pos ->
            lifecycleScope.launch {
                val adapterRef = adapter ?: return@launch
                val candidate = adapterRef.remoteSourceAt(pos) ?: sitePageUrls[pos]
                if (candidate.startsWith("file:")) {
                    adapterRef.notifyItemChanged(pos)
                    return@launch
                }
                val retried = runCatching {
                    DailyReportRepository.normalizePageUrls(listOf(candidate)).firstOrNull()
                }.onFailure {
                    Log.w("ViewDailyReportActivity", "Retry normalize failed", it)
                }.getOrNull() ?: candidate
                sitePageUrls[pos] = retried
                adapterRef.updateAt(pos, retried)
            }
        }
        recycler.adapter = adapter
        recycler.scrollToPosition(0)
        showLoading(false)
    }

    // ---------------------------------------------------------------------
    // 2B) Build PDF + render pages (fallback/legacy)
    // ---------------------------------------------------------------------
    private fun generateAndDisplayPdf(report: DailyReport, logo: Bitmap?) {
        currentMode = DisplayMode.PDF_RENDER
        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            try {
                showLoading(true)
                sharePdfButton.isEnabled = false

                val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dateText = report.date?.let { df.format(java.util.Date(it)) }

                val sectionsForPdf = resolveDailyReportSections(
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
                    activitiesText = sectionsForPdf.activities,
                    machinesText = sectionsForPdf.machines,
                    obstaclesText = sectionsForPdf.obstacles,
                    notes = report.notes,
                    // ✅ تم تمرير القيم الجديدة/القديمة معًا
                    photoUrls = report.photos,
                    sitepages = report.sitepages
                )

                val out = prepareExportFile()

                val pdf = withContext(Dispatchers.IO) {
                    ReportPdfBuilder(this@ViewDailyReportActivity).buildPdf(builderInput, logo, out)
                }
                pdfFile = pdf

                val previews = withContext(Dispatchers.Default) { renderPdfIntoImages(pdf) }

                pageBitmaps.forEach { it.recycle() }
                pageBitmaps.clear()
                pageBitmaps.addAll(previews)
                recycler.adapter = PdfPagesAdapter(this@ViewDailyReportActivity, pageBitmaps)
                recycler.scrollToPosition(0)

                sharePdfButton.isEnabled = true
            } catch (e: Exception) {
                sharePdfButton.isEnabled = false
                Toast.makeText(this@ViewDailyReportActivity, "تعذر توليد/عرض التقرير.", Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private suspend fun renderPdfIntoImages(pdf: File): List<Bitmap> = withContext(Dispatchers.Default) {
        val result = mutableListOf<Bitmap>()
        val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val horizontalPaddingPx = (resources.displayMetrics.density * 24).toInt()
                    val width = resources.displayMetrics.widthPixels - horizontalPaddingPx
                    val height = (width * (page.height.toFloat() / page.width)).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result += bitmap
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        return@withContext result
    }

    // ---------------------------------------------------------------------
    // 3) Export / Share
    // ---------------------------------------------------------------------
    private fun onShareClicked() {
        when (currentMode) {
            DisplayMode.SITE_PAGES -> exportSitePagesThenShare()
            DisplayMode.PDF_RENDER -> sharePdfIfAny()
            else -> Unit
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

    private fun exportSitePagesThenShare() {
        // إن كان لدينا ملف PDF جاهز من قبل (من صفحات الموقع)، شاركه مباشرة
        if (pdfFile != null && currentMode == DisplayMode.SITE_PAGES) {
            sharePdfIfAny()
            return
        }
        val urls = sitePageUrls.toList()
        if (urls.isEmpty()) {
            Toast.makeText(this, "لا توجد صفحات لتصديرها.", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                showLoading(true)
                sharePdfButton.isEnabled = false
                val out = prepareExportFile()

                withContext(Dispatchers.IO) { buildPdfFromSitePages(urls, out) }
                pdfFile = out
                sharePdfIfAny()
            } catch (_: Exception) {
                Toast.makeText(this@ViewDailyReportActivity, "تعذر إنشاء ملف PDF من الصفحات.", Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
                sharePdfButton.isEnabled = true
            }
        }
    }

    /** يبني PDF من قائمة صور (روابط) – صفحة = صورة كاملة. */
    private fun buildPdfFromSitePages(urls: List<String>, outFile: File) {
        val doc = PdfDocument()
        try {
            urls.forEachIndexed { index, url ->
                val bmp = loadBitmapBlocking(url) ?: return@forEachIndexed
                val scaled = scaleBitmapMaxDim(bmp, maxDim = 2048)
                val pageInfo = PdfDocument.PageInfo.Builder(scaled.width, scaled.height, index + 1).create()
                val page = doc.startPage(pageInfo)
                page.canvas.drawBitmap(scaled, 0f, 0f, null)
                doc.finishPage(page)
                if (scaled !== bmp) scaled.recycle()
                bmp.recycle()
            }
            FileOutputStream(outFile).use { fos -> doc.writeTo(fos) }
        } finally {
            doc.close()
        }
    }

    /** تحميل متزامن للصورة كـ Bitmap عبر Glide (استعمال داخلي فقط داخل Dispatchers.IO). */
    private fun loadBitmapBlocking(url: String): Bitmap? = try {
        Glide.with(this)
            .asBitmap()
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
            .get()
    } catch (_: Exception) { null }

    private fun scaleBitmapMaxDim(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        val maxSrc = maxOf(w, h)
        if (maxSrc <= maxDim) return src
        val scale = maxDim.toFloat() / maxSrc.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    // ---------------------------------------------------------------------
    // 4) UI helpers
    // ---------------------------------------------------------------------
    private fun showLoading(loading: Boolean) {
        progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) sharePdfButton.isEnabled = false
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            info != null && info.isConnected
        }
    }

    /**
     * يُرجع سطرًا واحدًا كما طُلِب:
     * "درجة الحرارة : 45 °C    حالة الطقس : غائم"
     * مع تطبيع تمثيل الدرجة إلى "NN °C" إن لم تكن مضبوطة.
     */
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

    /** يحوّل أي تمثيل للحرارة ("45", "45C", "45 °C") إلى صيغة موحَّدة: "45 °C" */
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

    // ---------------------------------------------------------------------
    // 5) Adapters
    // ---------------------------------------------------------------------
    /**
     * عارض لصفحات PDF (Bitmaps) – يدعم الحفظ بالضغط المطوّل.
     */
    private class PdfPagesAdapter(
        private val context: android.content.Context,
        private val pages: List<Bitmap>
    ) : RecyclerView.Adapter<PdfPagesAdapter.Holder>() {

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.pageImage)
            val progressBar: ProgressBar? = v.findViewById(R.id.progressBar)
            val errorText: TextView? = v.findViewById(R.id.errorText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.progressBar?.visibility = View.GONE
            holder.errorText?.visibility = View.GONE
            holder.img.setImageBitmap(pages[position])

            holder.img.setOnLongClickListener {
                val name = "ReportPage_${position + 1}_${System.currentTimeMillis()}.jpg"
                val uri = ImageUtils.saveToGallery(context, pages[position], name, 90)
                if (uri != null) {
                    Toast.makeText(context, "تم حفظ الصورة في الاستديو", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "تعذر حفظ الصورة", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        override fun getItemCount(): Int = pages.size
    }

    /**
     * عارض لروابط صفحات جاهزة (site_pages) – صفحة = صورة كاملة.
     * يدعم الحفظ بالضغط المطوّل بتنزيل الصورة وحفظها في الاستديو.
     */
    private class SitePagesAdapter(
        private val context: android.content.Context,
        private val scope: CoroutineScope,
        private val urls: MutableList<String>,
        private val reportId: String?,
        cachedPages: List<DailyReportDraftStorage.CachedSitePage>,
        private val draftStorage: DailyReportDraftStorage,
        private val networkChecker: () -> Boolean,
        private val onRetry: (position: Int) -> Unit
    ) : RecyclerView.Adapter<SitePagesAdapter.Holder>() {

        companion object {
            private const val PAGE_ASPECT_RATIO = 1.4142f
            private const val MIN_OVERRIDE_DP = 320
            private const val DOWNLOAD_THUMBNAIL = 0.25f
        }

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val pageImage: ImageView = v.findViewById(R.id.pageImage)
            val progressBar: ProgressBar = v.findViewById(R.id.progressBar)
            val errorContainer: View = v.findViewById(R.id.errorContainer)
            val retryButton: Button = v.findViewById(R.id.retryButton)
            val errorText: TextView = v.findViewById(R.id.errorText)
        }

        private val cachedByIndex = cachedPages.associateBy { it.pageIndex }.toMutableMap()
        private val remoteByIndex = mutableMapOf<Int, String?>().apply {
            urls.forEachIndexed { index, value ->
                this[index] = cachedByIndex[index]?.remoteUrl?.takeIf { it.isNotBlank() } ?: value
            }
        }
        private val displayOverrideMax = ImageUtils.displayAwareMaxDim(context)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val url = urls[position]
            val remoteSource = remoteByIndex[position]
            val cachedEntry = cachedByIndex[position]
            val fallbackFile = cachedEntry?.resolveFile(holder.pageImage.context.cacheDir)
                ?.takeIf { it.exists() && it.isFile }

            holder.progressBar.visibility = View.VISIBLE
            holder.errorContainer.visibility = View.GONE
            holder.pageImage.setImageDrawable(null)

            val (overrideW, overrideH) = computeOverrideSize(holder.pageImage)

            val modelToLoad: Any? = when {
                !remoteSource.isNullOrBlank() -> remoteSource
                fallbackFile != null -> fallbackFile
                url.startsWith("file:") -> Uri.parse(url)
                url.isNotBlank() -> url
                else -> null
            }

            if (modelToLoad == null) {
                holder.progressBar.visibility = View.GONE
                holder.errorContainer.visibility = View.VISIBLE
                holder.errorText.text = "تعذّر تحميل الصفحة"
                holder.retryButton.setOnClickListener {
                    val adapterPos = holder.bindingAdapterPosition
                    if (adapterPos != RecyclerView.NO_POSITION) {
                        onRetry(adapterPos)
                    }
                }
                return
            }

            var request = Glide.with(holder.pageImage.context)
                .asBitmap()
                .load(modelToLoad)
                .dontAnimate()
                .override(overrideW, overrideH)
                .thumbnail(DOWNLOAD_THUMBNAIL)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .fitCenter()

            if (fallbackFile != null && !remoteSource.isNullOrBlank()) {
                request = request.error(
                    Glide.with(holder.pageImage.context)
                        .asBitmap()
                        .load(fallbackFile)
                        .dontAnimate()
                        .override(overrideW, overrideH)
                        .fitCenter()
                )
            } else {
                request = request.error(R.drawable.ic_error)
            }

            request = request.listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap>,
                    isFirstResource: Boolean
                ): Boolean {
                    holder.progressBar.visibility = View.GONE
                    holder.errorContainer.visibility = View.VISIBLE
                    holder.errorText.text = if (!networkChecker()) {
                        "لا يوجد اتصال"
                    } else {
                        "تعذّر تحميل الصفحة"
                    }
                    holder.retryButton.setOnClickListener {
                        val adapterPos = holder.bindingAdapterPosition
                        if (adapterPos != RecyclerView.NO_POSITION) {
                            onRetry(adapterPos)
                        }
                    }
                    val adapterPos = holder.bindingAdapterPosition
                    Log.e(
                        "SitePagesAdapter",
                        "Load failed report=$reportId page=$adapterPos model=$modelToLoad",
                        e
                    )
                    return false
                }

                override fun onResourceReady(
                    resource: Bitmap,
                    model: Any,
                    target: Target<Bitmap>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    holder.progressBar.visibility = View.GONE
                    holder.errorContainer.visibility = View.GONE
                    val adapterPos = holder.bindingAdapterPosition
                    if (adapterPos != RecyclerView.NO_POSITION && !reportId.isNullOrBlank()) {
                        scope.launch(Dispatchers.IO) {
                            val cached = draftStorage.cacheSitePageBitmap(
                                reportId,
                                adapterPos,
                                remoteSource?.takeIf { it.isNotBlank() } ?: url,
                                resource
                            )
                            if (cached != null) {
                                withContext(Dispatchers.Main) {
                                    cachedByIndex[adapterPos] = cached
                                    if (cached.remoteUrl.isNotBlank()) {
                                        remoteByIndex[adapterPos] = cached.remoteUrl
                                    }
                                }
                            }
                        }
                    }
                    Log.d("SitePagesAdapter", "Loaded report=$reportId page=$adapterPos")
                    return false
                }
            })

            request
                .placeholder(R.drawable.ic_image_placeholder)
                .into(holder.pageImage)

            holder.pageImage.setOnLongClickListener {
                val adapterPos = holder.bindingAdapterPosition
                if (adapterPos == RecyclerView.NO_POSITION) {
                    return@setOnLongClickListener true
                }
                scope.launch(Dispatchers.IO) {
                    try {
                        val downloadSource: Any = remoteByIndex[adapterPos]?.takeIf { !it.isNullOrBlank() }
                            ?: cachedByIndex[adapterPos]?.resolveFile(context.cacheDir)
                            ?: url
                        val targetWidth = displayOverrideMax
                        val targetHeight = (targetWidth * PAGE_ASPECT_RATIO).roundToInt().coerceAtLeast(targetWidth)
                        val future = Glide.with(holder.pageImage.context)
                            .asBitmap()
                            .load(downloadSource)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .thumbnail(DOWNLOAD_THUMBNAIL)
                            .submit(targetWidth, targetHeight)
                        val bmp = future.get()
                        val name = "SitePage_${adapterPos + 1}_${System.currentTimeMillis()}.jpg"
                        val uri = ImageUtils.saveToGallery(context, bmp, name, 92)
                        withContext(Dispatchers.Main) {
                            if (uri != null) {
                                Toast.makeText(context, "تم حفظ الصورة في الاستديو", Toast.LENGTH_SHORT).show()
                            } else {
                            Toast.makeText(context, "تعذر حفظ الصورة", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "تعذر تنزيل الصورة", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                true
            }
        }

        override fun onViewRecycled(holder: Holder) {
            super.onViewRecycled(holder)
            Glide.with(holder.pageImage.context).clear(holder.pageImage)
            holder.pageImage.setImageDrawable(null)
        }

        fun updateAt(position: Int, newUrl: String) {
            urls[position] = newUrl
            remoteByIndex[position] = newUrl
            notifyItemChanged(position)
        }

        fun remoteSourceAt(position: Int): String? = remoteByIndex[position]

        override fun getItemCount(): Int = urls.size

        private fun computeOverrideSize(imageView: ImageView): Pair<Int, Int> {
            val dm = imageView.resources.displayMetrics
            val minWidth = (MIN_OVERRIDE_DP * dm.density).roundToInt().coerceAtLeast(1)
            val fallbackWidth = (dm.widthPixels * 0.9f).roundToInt().coerceAtLeast(minWidth)
            val measuredWidth = imageView.width.takeIf { it > 0 } ?: fallbackWidth
            val overrideWidth = measuredWidth.coerceIn(minWidth, displayOverrideMax)
            val overrideHeight = (overrideWidth * PAGE_ASPECT_RATIO).roundToInt()
                .coerceAtMost(displayOverrideMax)
                .coerceAtLeast(minWidth)
            return overrideWidth to overrideHeight
        }
    }


}

private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
