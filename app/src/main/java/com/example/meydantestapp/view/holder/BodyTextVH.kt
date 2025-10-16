package com.example.meydantestapp.view.holder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.view.ReportItem

class BodyTextVH private constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val bodyView: TextView = itemView.findViewById(R.id.bodyText)

    fun bind(item: ReportItem.BodyText) {
        bodyView.text = item.text
    }

    companion object {
        fun inflate(inflater: LayoutInflater, parent: ViewGroup): BodyTextVH {
            val view = inflater.inflate(R.layout.item_report_body_text, parent, false)
            return BodyTextVH(view)
        }
    }
}
