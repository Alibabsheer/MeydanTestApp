package com.example.meydantestapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyReportAdapter(
    private val reports: List<DailyReport>,
    private val onItemClick: (DailyReport) -> Unit,
    // دعم تمرير المعرّفات إلى شاشة العرض بدون كسر الاستدعاءات القديمة
    private val onItemClickWithMeta: ((report: DailyReport, organizationId: String?, projectId: String?, projectName: String?) -> Unit)? = null,
    private val organizationId: String? = null,
    private val projectId: String? = null,
    private val projectName: String? = null
) : RecyclerView.Adapter<DailyReportAdapter.ReportViewHolder>() {

    class ReportViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reportDate: TextView = view.findViewById(R.id.reportDate)
        val reportNumber: TextView = view.findViewById(R.id.reportNumber)
        val projectName: TextView = view.findViewById(R.id.projectName) // مخفي في التخطيط، يُترك للتوافق
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val dateText = try {
            val ms = when (val d = report.date) {
                is Long -> d
                is Date -> d.time
                else -> null
            }
            ms?.let { df.format(Date(it)) } ?: "-"
        } catch (_: Exception) {
            "-"
        }

        val numberText = report.reportNumber?.takeIf { it.isNotBlank() } ?: "DailyReport-${position + 1}"
        holder.reportNumber.text = numberText
        holder.reportDate.text = " • $dateText" // نقطة فاصلة قبل التاريخ
        holder.projectName.text = report.projectName ?: ""

        // الوصولية: وسم المحتوى للقرّاء
        holder.itemView.contentDescription = buildString {
            append("تقرير ")
            append(numberText)
            append(", التاريخ ")
            append(dateText)
            report.projectName?.let {
                append(", المشروع ")
                append(it)
            }
        }

        holder.itemView.setOnClickListener {
            // الاستدعاء الأصلي (توافق كامل مع الكود القائم)
            onItemClick(report)
            // استدعاء اختياري يمرّر المعرّفات إلى شاشة العرض لمن يريد استخدامه
            onItemClickWithMeta?.invoke(report, organizationId, projectId, projectName)
        }
    }

    override fun getItemCount(): Int = reports.size
}
