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
import com.example.meydantestapp.view.ReportItem

class PhotoVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val photoView: ImageView = itemView.findViewById(R.id.reportPhoto)

    fun bind(item: ReportItem.Photo) {
        loadInto(photoView, item.uri)
    }

    open fun clear() {
        Glide.with(photoView).clear(photoView)
        photoView.setImageResource(R.drawable.ic_image_placeholder)
    }

    protected fun loadInto(target: ImageView, uri: Uri?) {
        Glide.with(target)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_image_placeholder)
            .error(R.drawable.ic_image_placeholder)
            .into(target)
    }
}

class SitePageVH(itemView: View) : PhotoVH(itemView) {

    private val captionView: TextView = itemView.findViewById(R.id.sitePageCaption)
    private val imageView: ImageView = itemView.findViewById(R.id.sitePageImage)

    fun bind(item: ReportItem.SitePage) {
        loadInto(imageView, item.uri)
        val caption = item.caption?.trim()?.takeIf { it.isNotEmpty() }
        captionView.isVisible = caption != null
        captionView.text = caption
    }

    override fun clear() {
        Glide.with(imageView).clear(imageView)
        imageView.setImageResource(R.drawable.ic_image_placeholder)
        captionView.text = null
        captionView.isVisible = false
    }
}
