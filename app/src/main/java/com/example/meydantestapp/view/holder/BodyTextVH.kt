package com.example.meydantestapp.view.holder

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
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

    fun bind(item: ReportItem.Workforce) {
        titleView.text = itemView.context.getString(R.string.report_section_workforce)
        if (item.entries.isEmpty()) {
            entriesView.text = ""
            entriesView.visibility = View.GONE
        } else {
            entriesView.visibility = View.VISIBLE
            entriesView.text = item.entries.joinToString(separator = "\n") { entry ->
                val cleaned = entry.trim()
                "\u2022 $cleaned"
            }
        }
    }
}
