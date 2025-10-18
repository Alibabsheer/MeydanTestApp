package com.example.meydantestapp.view

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.meydantestapp.R
import com.example.meydantestapp.common.ReportHeadings
import com.example.meydantestapp.view.holder.BodyTextVH
import com.example.meydantestapp.view.holder.HeaderLogoVH
import com.example.meydantestapp.view.holder.InfoRowVH
import com.example.meydantestapp.view.holder.PhotoVH
import com.example.meydantestapp.view.holder.SectionTitleVH

class ReportItemsAdapter(
    private val logoProvider: () -> Bitmap?
) : ListAdapter<ReportItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ReportItem.HeaderLogo -> VIEW_TYPE_HEADER_LOGO
            is ReportItem.InfoRow -> VIEW_TYPE_INFO_ROW
            is ReportItem.SectionTitle -> VIEW_TYPE_SECTION_TITLE
            is ReportItem.BodyText -> VIEW_TYPE_BODY_TEXT
            is ReportItem.Workforce -> VIEW_TYPE_WORKFORCE
            is ReportItem.SitePage -> VIEW_TYPE_SITE_PAGE
            is ReportItem.Photo -> VIEW_TYPE_PHOTO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER_LOGO -> HeaderLogoVH(inflater.inflate(R.layout.item_report_header_logo, parent, false))
            VIEW_TYPE_INFO_ROW -> InfoRowVH(inflater.inflate(R.layout.item_report_info_row, parent, false))
            VIEW_TYPE_SECTION_TITLE -> SectionTitleVH(inflater.inflate(R.layout.item_report_section_title, parent, false))
            VIEW_TYPE_BODY_TEXT -> BodyTextVH(inflater.inflate(R.layout.item_report_body_text, parent, false))
            VIEW_TYPE_WORKFORCE -> WorkforceVH(inflater.inflate(R.layout.item_report_workforce, parent, false))
            VIEW_TYPE_SITE_PAGE -> SitePageVH(inflater.inflate(R.layout.item_report_site_page, parent, false))
            else -> PhotoVH(inflater.inflate(R.layout.item_report_photo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ReportItem.HeaderLogo -> (holder as HeaderLogoVH).bind(item, logoProvider())
            is ReportItem.InfoRow -> (holder as InfoRowVH).bind(item)
            is ReportItem.SectionTitle -> (holder as SectionTitleVH).bind(item)
            is ReportItem.BodyText -> (holder as BodyTextVH).bind(item)
            is ReportItem.Workforce -> (holder as WorkforceVH).bind(item)
            is ReportItem.SitePage -> (holder as SitePageVH).bind(item)
            is ReportItem.Photo -> (holder as PhotoVH).bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is PhotoVH -> holder.clear()
            is SitePageVH -> holder.clear()
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ReportItem>() {
        override fun areItemsTheSame(oldItem: ReportItem, newItem: ReportItem): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: ReportItem, newItem: ReportItem): Boolean = oldItem == newItem
    }

    private class WorkforceVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.workforceTitle)
        private val entriesView: TextView = itemView.findViewById(R.id.workforceEntries)

        fun bind(item: ReportItem.Workforce) {
            val context = itemView.context
            titleView.text = ReportHeadings.workforce(context)
            if (item.entries.isEmpty()) {
                entriesView.text = null
                entriesView.isVisible = false
                return
            }
            entriesView.isVisible = true
            entriesView.text = item.entries.joinToString("\n") { entry ->
                context.getString(R.string.report_workforce_bullet_format, entry)
            }
        }
    }

    private class SitePageVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.sitePageImage)
        private val captionView: TextView = itemView.findViewById(R.id.sitePageCaption)

        fun bind(item: ReportItem.SitePage) {
            imageView.setImageResource(R.drawable.ic_image_placeholder)
            Glide.with(imageView)
                .load(item.uri)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(imageView)

            val caption = item.caption?.trim().takeUnless { it.isNullOrEmpty() }
            if (caption != null) {
                captionView.text = caption
                captionView.isVisible = true
            } else {
                captionView.text = null
                captionView.isVisible = false
            }
        }

        fun clear() {
            Glide.with(imageView).clear(imageView)
            imageView.setImageResource(R.drawable.ic_image_placeholder)
            captionView.text = null
            captionView.isVisible = false
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER_LOGO = 0
        private const val VIEW_TYPE_INFO_ROW = 1
        private const val VIEW_TYPE_SECTION_TITLE = 2
        private const val VIEW_TYPE_BODY_TEXT = 3
        private const val VIEW_TYPE_PHOTO = 4
        private const val VIEW_TYPE_WORKFORCE = 5
        private const val VIEW_TYPE_SITE_PAGE = 6
    }
}
