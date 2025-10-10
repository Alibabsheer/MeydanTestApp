package com.example.meydantestapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meydantestapp.databinding.ActivityDailyReportsBinding
import com.example.meydantestapp.utils.Constants
import com.example.meydantestapp.utils.ProjectLocationSnapshotResolver
import com.google.firebase.auth.FirebaseAuth

/**
 * DailyReportsActivity
 * - تعرض قائمة التقارير اليومية غير المؤرشفة بالأحدث أولاً.
 * - تعتمد على DailyReportsViewModel (MVVM) بدل الاستعلام المباشر.
 * - organizationId يُمرَّر عبر الـ Intent إن كان متاحًا، أو يُستخدم UID كخيار افتراضي.
 * - عند النقر على عنصر: الانتقال إلى ViewDailyReportActivity وتمرير DailyReport كاملاً.
 *
 * الإضافات الأهم في هذا التحديث:
 * 1) تمرير حقول جديدة ضمن DailyReport لعرض "معلومات التقرير" كاملة لاحقًا:
 *    - createdByName (اسم مُنشئ التقرير المقروء إن وُجد)
 *    - addressText (موقع المشروع النصي)
 * 2) قراءة درجة الحرارة سواء كانت محفوظة كنص أو رقم، وتوحيدها إلى نص.
 */
class DailyReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDailyReportsBinding
    private val vm: DailyReportsViewModel by viewModels()

    private val auth by lazy { FirebaseAuth.getInstance() }

    private val reportList = mutableListOf<DailyReport>()
    private lateinit var adapter: DailyReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailyReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // عنوان / رجوع
        binding.backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.titleText.text = getString(R.string.daily_reports_title)

        // قراءة القيم الممرَّرة أولًا (قبل تهيئة الـ Adapter)
        val projectId = intent.getStringExtra("projectId")
        val passedOrgId = intent.getStringExtra("organizationId")
        val projectName = intent.getStringExtra("projectName")

        if (projectId.isNullOrBlank()) {
            Toast.makeText(this, "لم يتم تمرير معرف المشروع.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // organizationId: إمّا من الـ Intent أو UID
        val resolvedOrgId = when {
            !passedOrgId.isNullOrBlank() -> passedOrgId
            auth.currentUser?.uid != null -> auth.currentUser!!.uid
            else -> null
        }

        if (resolvedOrgId == null) {
            Toast.makeText(this, "تعذّر تحديد المؤسسة.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Recycler
        binding.reportsRecycler.layoutManager = LinearLayoutManager(this)
        adapter = DailyReportAdapter(
            reports = reportList,
            onItemClick = { report: DailyReport ->
                val intent = Intent(this, ViewDailyReportActivity::class.java)
                intent.putExtra("dailyReport", report)
                // تمرير organizationId لضمان الشعار وسياق الـ PDF
                intent.putExtra(Constants.EXTRA_ORGANIZATION_ID, resolvedOrgId)
                intent.putExtra("organizationId", resolvedOrgId) // احتياط للتوافق
                intent.putExtra("projectId", projectId)
                intent.putExtra("projectName", projectName)
                startActivity(intent)
            },
            // تم الإبقاء على التواقيع الموسّعة للتوافق مع نسخك السابقة من الـ Adapter
            organizationId = resolvedOrgId,
            projectId = projectId,
            projectName = projectName
        )
        binding.reportsRecycler.adapter = adapter

        // مراقبة الحالة من الـ ViewModel
        vm.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading == true) View.VISIBLE else View.GONE
        }
        vm.errorMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                binding.emptyView.visibility = View.VISIBLE
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
        vm.dailyReports.observe(this) { list ->
            reportList.clear()
            // فلترة غير المؤرشف فقط + الأحدث أولاً (الريبو يرتّب بالتاريخ DESC)
            val filtered = list
                .mapNotNull { map -> map.toDailyReportOrNull() }
                .filter { it.isArchived != true }
                .sortedByDescending { it.date ?: 0L }

            reportList.addAll(filtered)
            adapter.notifyDataSetChanged()
            binding.emptyView.visibility = if (reportList.isEmpty()) View.VISIBLE else View.GONE
        }

        // بدء الجلب (مسار هرمي: organizations/{orgId}/projects/{projectId}/dailyReports)
        vm.fetchDailyReports(resolvedOrgId, projectId)
    }

    /** تحويل Map لِـ DailyReport بشكل آمن وشامل للحقول الجديدة */
    private fun Map<String, Any>.toDailyReportOrNull(): DailyReport? {
        return try {
            val dateMs: Long? = when (val d = this["date"]) {
                is Number -> d.toLong()
                is String -> d.toLongOrNull()
                else -> null
            }

            val tempText: String? = when (val t = this["temperature"]) {
                is Number -> t.toString()
                is String -> t.trim().ifEmpty { null }
                else -> null
            }

            val skilled: Int? = when (val v = this["skilledLabor"]) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull()
                else -> null
            }
            val unskilled: Int? = when (val v = this["unskilledLabor"]) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull()
                else -> null
            }
            val totalLaborSafe: Int? = when (val v = this["totalLabor"]) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull()
                else -> null
            } ?: run {
                // احسبه إذا لم يكن محفوظًا ومتوفر العاملان
                if (skilled != null && unskilled != null) skilled + unskilled else null
            }

            val locationSnapshot = ProjectLocationSnapshotResolver.fromReportData(this)

            DailyReport(
                id = this["id"] as? String,
                reportNumber = this["reportNumber"] as? String,
                date = dateMs,
                projectId = this["projectId"] as? String,
                isArchived = this["isArchived"] as? Boolean,
                projectName = this["projectName"] as? String,
                ownerName = (this["ownerName"] as? String)?.takeIf { it.isNotBlank() },
                contractorName = (this["contractorName"] as? String)?.takeIf { it.isNotBlank() },
                consultantName = (this["consultantName"] as? String)?.takeIf { it.isNotBlank() },
                // درجة الحرارة كنص موحّد (لا نضيف رمز °C هنا حتى لا نفرض التنسيق قبل العرض)
                temperature = tempText,
                weatherStatus = (this["weatherStatus"] as? String)?.trim(),
                skilledLabor = skilled,
                unskilledLabor = unskilled,
                totalLabor = totalLaborSafe,
                dailyActivities = (this["dailyActivities"] as? List<*>)?.mapNotNull { it?.toString() },
                resourcesUsed = (this["resourcesUsed"] as? List<*>)?.mapNotNull { it?.toString() },
                challenges = (this["challenges"] as? List<*>)?.mapNotNull { it?.toString() },
                notes = (this["notes"] as? List<*>)?.mapNotNull { it?.toString() },
                photos = (this["photos"] as? List<*>)?.mapNotNull { it?.toString() },
                sitepages = (this["sitepages"] as? List<*>)?.mapNotNull { it?.toString() },
                // الحقول الجديدة المطلوبة لعرض "معلومات التقرير"
                createdBy = (this["createdBy"] as? String),
                createdByName = (this["createdByName"] as? String)?.takeIf { it.isNotBlank() },
                addressText = locationSnapshot.addressText,
                googleMapsUrl = locationSnapshot.googleMapsUrl,
                organizationName = (this["organizationName"] as? String)
            )
        } catch (_: Exception) {
            null
        }
    }
}
