package com.example.meydantestapp.view.holder

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.meydantestapp.R
import com.example.meydantestapp.view.ReportItem

class PhotoVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val photoView: ImageView = itemView.findViewById(R.id.reportPhoto)

    fun bind(item: ReportItem.Photo) {
        Glide.with(photoView)
            .load(item.uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .into(photoView)
    }

    fun clear() {
        Glide.with(photoView).clear(photoView)
        photoView.setImageDrawable(null)
    }
}

class SitePageVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val imageView: ImageView = itemView.findViewById(R.id.sitePageImage)
    private val captionView: TextView = itemView.findViewById(R.id.sitePageCaption)

    fun bind(item: ReportItem.SitePage) {
        Glide.with(imageView)
            .load(item.uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .into(imageView)

        val caption = item.caption?.trim()
        if (caption.isNullOrEmpty()) {
            captionView.text = null
            captionView.isVisible = false
        } else {
            captionView.isVisible = true
            captionView.text = caption
        }
    }

    fun clear() {
        Glide.with(imageView).clear(imageView)
        imageView.setImageDrawable(null)
        captionView.text = null
        captionView.isVisible = false
    }
}
