package com.example.meydantestapp.view.holder

import android.graphics.Paint
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.view.ReportItem

class InfoRowVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val labelView: TextView = itemView.findViewById(R.id.infoLabel)
    private val valueView: TextView = itemView.findViewById(R.id.infoValue)
    private val defaultColor: Int = valueView.currentTextColor

    fun bind(item: ReportItem.InfoRow, onLinkClicked: (String) -> Unit) {
        labelView.setText(item.labelRes)
        valueView.text = item.value

        val link = item.linkUrl
        if (!link.isNullOrEmpty()) {
            valueView.paintFlags = valueView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            valueView.setTextColor(ContextCompat.getColor(itemView.context, R.color.hyperlink_blue))
            valueView.isClickable = true
            valueView.isFocusable = true
            valueView.isFocusableInTouchMode = true
            valueView.setOnClickListener { onLinkClicked(link) }
        } else {
            valueView.paintFlags = valueView.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            valueView.setTextColor(defaultColor)
            valueView.isClickable = false
            valueView.isFocusable = false
            valueView.isFocusableInTouchMode = false
            valueView.setOnClickListener(null)
        }
    }
}
