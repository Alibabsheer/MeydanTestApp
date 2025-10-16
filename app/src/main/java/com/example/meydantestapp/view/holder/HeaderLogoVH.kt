package com.example.meydantestapp.view.holder

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R

class HeaderLogoVH private constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val logoView: ImageView = itemView.findViewById(R.id.headerLogo)

    fun bind(bitmap: Bitmap?) {
        if (bitmap != null) {
            logoView.setImageBitmap(bitmap)
        } else {
            logoView.setImageResource(R.drawable.default_logo)
        }
    }

    companion object {
        fun inflate(inflater: LayoutInflater, parent: ViewGroup): HeaderLogoVH {
            val view = inflater.inflate(R.layout.item_report_header_logo, parent, false)
            return HeaderLogoVH(view)
        }
    }
}
