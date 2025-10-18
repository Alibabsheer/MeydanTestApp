package com.example.meydantestapp.view.holder

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.view.ReportItem

class InfoRowVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val labelView: TextView = itemView.findViewById(R.id.infoLabel)
    private val valueView: TextView = itemView.findViewById(R.id.infoValue)
    private val defaultColor: Int = valueView.currentTextColor

    fun bind(item: ReportItem.InfoRow) {
        labelView.setText(item.labelRes)
        valueView.text = item.value

        val link = item.linkUrl?.takeIf { it.isNotBlank() }
        if (link != null) {
            valueView.paintFlags = valueView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            valueView.setTextColor(ContextCompat.getColor(itemView.context, R.color.hyperlink_blue))
            valueView.isClickable = true
            valueView.setOnClickListener { openLink(link) }
        } else {
            valueView.paintFlags = valueView.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            valueView.setTextColor(defaultColor)
            valueView.isClickable = false
            valueView.setOnClickListener(null)
        }
    }

    private fun openLink(link: String) {
        val context = itemView.context
        val uri = runCatching { Uri.parse(link) }.getOrNull()
        if (uri == null) {
            Toast.makeText(context, R.string.report_error_invalid_link, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.report_error_open_location, Toast.LENGTH_SHORT).show()
        }
    }
}
