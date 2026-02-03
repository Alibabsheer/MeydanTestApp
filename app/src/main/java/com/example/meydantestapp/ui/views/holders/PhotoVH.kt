package com.example.meydantestapp.view.holder

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.clear
import com.example.meydantestapp.R
import com.example.meydantestapp.view.ReportItem

open class PhotoVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val photoView: ImageView = itemView.findViewById(R.id.reportPhoto)

    open fun bind(item: ReportItem.Photo) {
        photoView.load(item.uri) {
            crossfade(true)
            placeholder(R.drawable.default_logo)
            error(R.drawable.default_logo)
        }
    }

    open fun clear() {
        photoView.clear()
    }
}
