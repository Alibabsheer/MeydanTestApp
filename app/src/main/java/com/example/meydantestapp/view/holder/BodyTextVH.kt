package com.example.meydantestapp.view.holder

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.common.ReportHeadings
import com.example.meydantestapp.view.ReportItem

class BodyTextVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val bodyView: TextView = itemView.findViewById(R.id.bodyText)

    fun bind(item: ReportItem.BodyText) {
        bodyView.text = item.text
    }
}

class WorkforceVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val titleView: TextView = itemView.findViewById(R.id.workforceTitle)
    private val entriesView: TextView = itemView.findViewById(R.id.workforceEntries)
    private val bulletPrefix: String = "\u2022 "

    fun bind(item: ReportItem.Workforce) {
        val context = itemView.context
        titleView.text = ReportHeadings.workforce(context)

        val lines = item.entries.mapNotNull { raw ->
            val parsed = ReportItem.Workforce.parseEntry(raw)
            if (parsed != null) {
                val (key, valueRaw) = parsed
                val labelRes = when (key) {
                    ReportItem.Workforce.KEY_SKILLED -> R.string.report_workforce_label_skilled
                    ReportItem.Workforce.KEY_UNSKILLED -> R.string.report_workforce_label_unskilled
                    ReportItem.Workforce.KEY_TOTAL -> R.string.report_workforce_label_total
                    else -> null
                }
                val label = labelRes?.let { context.getString(it) } ?: key
                val value = valueRaw.trim()
                if (value.isEmpty()) {
                    null
                } else {
                    context.getString(R.string.report_workforce_item_format, label, value)
                }
            } else {
                raw.trim().takeIf { it.isNotEmpty() }
            }
        }

        if (lines.isEmpty()) {
            entriesView.text = ""
            entriesView.visibility = View.GONE
        } else {
            entriesView.visibility = View.VISIBLE
            entriesView.text = lines.joinToString(separator = "\n") { line ->
                bulletPrefix + line
            }
        }
    }
}
