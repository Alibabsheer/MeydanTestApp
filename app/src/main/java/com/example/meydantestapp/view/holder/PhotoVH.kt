package com.example.meydantestapp.view.holder

import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.meydantestapp.R

class PhotoVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val photoView: ImageView = itemView.findViewById(R.id.reportPhoto)

    fun bind(uri: Uri?) {
        loadInto(photoView, uri)
    }

    fun clear() {
        Glide.with(photoView).clear(photoView)
        photoView.setImageResource(R.drawable.ic_image_placeholder)
    }

    private fun loadInto(target: ImageView, uri: Uri?) {
        Glide.with(target)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .into(target)
    }
}

class SitePageVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val captionView: TextView = itemView.findViewById(R.id.sitePageCaption)
    private val imageView: ImageView = itemView.findViewById(R.id.sitePageImage)

    fun bind(uri: Uri?, caption: String? = null) {
        loadInto(imageView, uri)
        val cleanCaption = caption?.trim()?.takeIf { it.isNotEmpty() }
        captionView.isVisible = cleanCaption != null
        captionView.text = cleanCaption
    }

    fun clear() {
        Glide.with(imageView).clear(imageView)
        imageView.setImageResource(R.drawable.ic_image_placeholder)
        captionView.text = null
        captionView.isVisible = false
    }

    private fun loadInto(target: ImageView, uri: Uri?) {
        Glide.with(target)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .into(target)
    }
}
