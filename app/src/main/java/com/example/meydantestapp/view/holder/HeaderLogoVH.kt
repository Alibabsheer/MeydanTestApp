package com.example.meydantestapp.view.holder

import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R

class HeaderLogoVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val logoImage: ImageView = itemView.findViewById(R.id.headerLogoImage)

    fun bind(logo: Bitmap?) {
        if (logo != null) {
            logoImage.setImageBitmap(logo)
        } else {
            val placeholder = AppCompatResources.getDrawable(itemView.context, R.drawable.default_logo)
            logoImage.setImageDrawable(placeholder)
        }
    }
}
