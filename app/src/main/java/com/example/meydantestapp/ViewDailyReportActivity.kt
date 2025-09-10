package com.example.meydantestapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.example.meydantestapp.report.ReportPdfBuilder
import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.ImageUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.otaliastudios.zoom.ZoomLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

class ViewDailyReportActivity : AppCompatActivity() {

    // ===== Views =====
    private lateinit var recycler: RecyclerView
    private lateinit var backButton: Button
    private lateinit var sharePdfButton: Button
    private var progressBar: ProgressBar? = null
    private lateinit var zoomLayout: ZoomLayout

    // ===== VM / Data =====
    private lateinit var viewModel: ViewDailyReportViewModel
    private val db by lazy { FirebaseFirestore.getInstance() }

    // وضع العرض الحالي
    private enum class DisplayMode { SITE_PAGES, PDF_RENDER }
    private var currentMode: DisplayMode? = null

    // ملفات/صفحات
    private var pdfFile: File? = null
    private val pageBitmaps = mutableListOf<Bitmap>() // لعرض PDF المُرندَر
    private val sitePageUrls = mutableListOf<String>() // لعرض صفحات جاهزة حسب القالب

    // ===== Rendering guards =====
    private var alreadyRendered = false
    private var renderJob: Job? = null

    // ===== Zoom config / state =====
    private var initialZoomFromIntent: Float? = null
    private var savedZoom: Float? = null

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
        progressBar = findViewById(R.id.progressBar)
        zoomLayout = findViewById(R.id.zoomContainer)

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

        // ✅ جديد: تفعيل عرض site_pages مباشرة إن وُجدت داخل DailyReport أو ضمن الـ Intent
        val sitePagesFromReport = (report.sitepages ?: emptyList()).filter { it.startsWith("http://") || it.startsWith("https://") }
        val sitePagesExtra: ArrayList<String>? = intent.getStringArrayListExtra("site_pages")
        val chosenSitePages = when {
            sitePagesFromReport.isNotEmpty() -> sitePagesFromReport
            !sitePagesExtra.isNullOrEmpty() -> sitePagesExtra.filter { it.startsWith("http://") || it.startsWith("https://") }
            else -> emptyList()
        }
        if (chosenSitePages.isNotEmpty()) {
            displaySitePages(chosenSitePages)
            tryResolveOrganizationAndLoadLogo(report, explicitOrgId)
            return
        }

        // 🔁 خلاف ذلك، نولّد PDF ونعرض صوره كما في السابق (سيسقط إلى photos القديمة)
        showLoading(true)
        viewModel.logo.observe(this) { logoBitmap ->
            if (!alreadyRendered) {
                alreadyRendered = true
                generateAndDisplayPdf(report, logoBitmap)
            }
        }
        tryResolveOrganizationAndLoadLogo(report, explicitOrgId)
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

    private fun renderPdfWithoutLogoIfNotYet(report: DailyReport) {
        if (!alreadyRendered && currentMode != DisplayMode.SITE_PAGES) {
            alreadyRendered = true
            generateAndDisplayPdf(report, null)
        }
    }

    // ---------------------------------------------------------------------
    // 2A) Display pre-composed site pages (full images per template)
    // ---------------------------------------------------------------------
    private fun displaySitePages(urls: List<String>) {
        currentMode = DisplayMode.SITE_PAGES
        renderJob?.cancel()
        sitePageUrls.clear()
        sitePageUrls.addAll(urls)
        sharePdfButton.isEnabled = true // يمكن التصدير إلى PDF من هذه الصفحات

        // استبدل الـ Adapter بواحد للروابط
        recycler.adapter = SitePagesAdapter(this, sitePageUrls)
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

                val builderInput = ReportPdfBuilder.DailyReport(
                    organizationName = report.organizationName,
                    projectName = report.projectName,
                    projectLocation = report.projectLocation,
                    reportNumber = report.reportNumber,
                    dateText = dateText,
                    weatherText = formatWeatherLine(report.temperature, report.weatherStatus),
                    createdBy = (report.createdByName?.takeIf { it.isNotBlank() } ?: report.createdBy),
                    dailyActivities = report.dailyActivities,
                    skilledLabor = report.skilledLabor?.toString(),
                    unskilledLabor = report.unskilledLabor?.toString(),
                    totalLabor = report.totalLabor?.toString(),
                    resourcesUsed = report.resourcesUsed,
                    challenges = report.challenges,
                    notes = report.notes,
                    // ✅ تم تمرير القيم الجديدة/القديمة معًا
                    photoUrls = report.photos,
                    sitepages = report.sitepages
                )

                val out = File(cacheDir, "reports/report_${report.id ?: System.currentTimeMillis()}.pdf")
                out.parentFile?.mkdirs()

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
                val out = File(cacheDir, "reports/sitepages_${System.currentTimeMillis()}.pdf")
                out.parentFile?.mkdirs()

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
            val progress: ProgressBar? = v.findViewById(R.id.progress)
            val errorText: TextView? = v.findViewById(R.id.errorText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.progress?.visibility = View.GONE
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
        private val urls: List<String>
    ) : RecyclerView.Adapter<SitePagesAdapter.Holder>() {

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.pageImage)
            val progress: ProgressBar? = v.findViewById(R.id.progress)
            val errorText: TextView? = v.findViewById(R.id.errorText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            return Holder(v)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val url = urls[position]
            holder.progress?.visibility = View.VISIBLE
            holder.errorText?.visibility = View.GONE

            Glide.with(holder.img.context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .fitCenter()
                .into(holder.img)

            holder.progress?.visibility = View.GONE

            holder.img.setOnLongClickListener {
                // تنزيل وحفظ في الخلفية بدون كروتينز لتجنّب الاعتماد هنا على النطاق الحياتي
                Thread {
                    try {
                        val future = Glide.with(holder.img.context)
                            .asBitmap()
                            .load(url)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                        val bmp = future.get()
                        val name = "SitePage_${position + 1}_${System.currentTimeMillis()}.jpg"
                        val uri = ImageUtils.saveToGallery(holder.img.context, bmp, name, 92)
                        Handler(Looper.getMainLooper()).post {
                            if (uri != null) {
                                Toast.makeText(holder.img.context, "تم حفظ الصورة في الاستديو", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(holder.img.context, "تعذر حفظ الصورة", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (_: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(holder.img.context, "تعذر تنزيل الصورة", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
                true
            }
        }

        override fun getItemCount(): Int = urls.size
    }
}
