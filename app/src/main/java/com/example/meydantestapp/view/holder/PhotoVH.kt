package com.example.meydantestapp.view.holder

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.meydantestapp.R

class PhotoVH private constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val photoView: ImageView = itemView.findViewById(R.id.reportPhoto)

    fun bind(uri: Uri) {
        Glide.with(photoView)
            .load(uri)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .into(photoView)
    }

    companion object {
        fun inflate(inflater: LayoutInflater, parent: ViewGroup): PhotoVH {
            val view = inflater.inflate(R.layout.item_report_photo, parent, false)
            return PhotoVH(view)
        }
    }
}
