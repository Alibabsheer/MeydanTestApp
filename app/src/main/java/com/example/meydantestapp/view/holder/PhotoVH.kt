package com.example.meydantestapp.view.holder

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.meydantestapp.R
import com.example.meydantestapp.view.ReportItem

open class PhotoVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val photoView: ImageView = itemView.findViewById(R.id.reportPhoto)

    open fun bind(item: ReportItem.Photo) {
        Glide.with(photoView)
            .load(item.uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.default_logo)
            .into(photoView)
    }

    open fun clear() {
        Glide.with(photoView).clear(photoView)
    }
}
