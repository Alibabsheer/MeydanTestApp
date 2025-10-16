package com.example.meydantestapp.view.holder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.view.ReportItem

class SectionTitleVH private constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val titleView: TextView = itemView.findViewById(R.id.sectionTitle)

    fun bind(item: ReportItem.SectionTitle, headingResolver: (android.content.Context, Int) -> String) {
        val context = itemView.context
        titleView.text = headingResolver(context, item.titleRes)
        val appearance = if (item.level == 1) {
            R.style.TextAppearance_Heading_H1
        } else {
            R.style.TextAppearance_Heading_H2
        }
        TextViewCompat.setTextAppearance(titleView, appearance)
    }

    companion object {
        fun inflate(inflater: LayoutInflater, parent: ViewGroup): SectionTitleVH {
            val view = inflater.inflate(R.layout.item_report_section_title, parent, false)
            return SectionTitleVH(view)
        }
    }
}
