package com.example.meydantestapp.view.holder

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.view.ReportItem

class InfoRowVH private constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val labelView: TextView = itemView.findViewById(R.id.infoLabel)
    private val valueView: TextView = itemView.findViewById(R.id.infoValue)
    private val defaultColor: Int = valueView.currentTextColor

    fun bind(item: ReportItem.InfoRow, onLinkClicked: (String) -> Unit) {
        labelView.setText(item.labelRes)
        valueView.text = item.value
        val link = item.linkUrl?.takeIf { it.isNotBlank() }
        if (link != null) {
            valueView.setTextColor(ContextCompat.getColor(valueView.context, R.color.hyperlink_blue))
            valueView.paintFlags = valueView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            valueView.isClickable = true
            valueView.isFocusable = true
            valueView.setOnClickListener { onLinkClicked(link) }
        } else {
            valueView.setTextColor(defaultColor)
            valueView.paintFlags = valueView.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            valueView.isClickable = false
            valueView.isFocusable = false
            valueView.setOnClickListener(null)
        }
    }

    companion object {
        fun inflate(inflater: LayoutInflater, parent: ViewGroup): InfoRowVH {
            val view = inflater.inflate(R.layout.item_report_info_row, parent, false)
            return InfoRowVH(view)
        }
    }
}
