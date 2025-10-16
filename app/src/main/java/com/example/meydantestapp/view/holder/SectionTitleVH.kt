package com.example.meydantestapp.view.holder

import android.view.View
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.common.ReportHeadings
import com.example.meydantestapp.view.ReportItem

class SectionTitleVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val titleView: TextView = itemView.findViewById(R.id.sectionTitle)

    fun bind(item: ReportItem.SectionTitle) {
        val context = itemView.context
        val text = when (item.titleRes) {
            R.string.report_section_info -> ReportHeadings.info(context)
            R.string.report_section_activities -> ReportHeadings.activities(context)
            R.string.report_section_equipment -> ReportHeadings.equipment(context)
            R.string.report_section_obstacles -> ReportHeadings.obstacles(context)
            else -> context.getString(item.titleRes)
        }
        titleView.text = text
        val style = when (item.level) {
            1 -> R.style.TextAppearance_Heading_H1
            else -> R.style.TextAppearance_Heading_H2
        }
        TextViewCompat.setTextAppearance(titleView, style)
    }
}
