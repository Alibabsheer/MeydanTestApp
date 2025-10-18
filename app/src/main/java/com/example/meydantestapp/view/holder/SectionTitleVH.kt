package com.example.meydantestapp.view.holder

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.common.ReportHeadings

class SectionTitleVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val titleView: TextView = itemView.findViewById(R.id.sectionTitle)

    fun bind(@StyleRes textAppearance: Int, @StringRes titleRes: Int) {
        val context = itemView.context
        val text = when (titleRes) {
            R.string.report_section_info -> ReportHeadings.info(context)
            R.string.report_section_activities -> ReportHeadings.activities(context)
            R.string.report_section_equipment -> ReportHeadings.equipment(context)
            R.string.report_section_obstacles -> ReportHeadings.obstacles(context)
            else -> context.getString(titleRes)
        }
        titleView.text = text
        TextViewCompat.setTextAppearance(titleView, textAppearance)
    }
}
