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
