package com.example.meydantestapp.view

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.view.holder.BodyTextVH
import com.example.meydantestapp.view.holder.HeaderLogoVH
import com.example.meydantestapp.view.holder.InfoRowVH
import com.example.meydantestapp.view.holder.PhotoVH
import com.example.meydantestapp.view.holder.SectionTitleVH

class ReportItemsAdapter(
    private val logoProvider: () -> Bitmap?,
    private val onLinkClicked: (String) -> Unit
) : ListAdapter<ReportItem, RecyclerView.ViewHolder>(DiffCallback) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ReportItem.HeaderLogo -> VIEW_TYPE_HEADER_LOGO
            is ReportItem.InfoRow -> VIEW_TYPE_INFO_ROW
            is ReportItem.SectionTitle -> VIEW_TYPE_SECTION_TITLE
            is ReportItem.BodyText -> VIEW_TYPE_BODY_TEXT
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
            else -> PhotoVH(inflater.inflate(R.layout.item_report_photo, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ReportItem.HeaderLogo -> (holder as HeaderLogoVH).bind(item, logoProvider())
            is ReportItem.InfoRow -> (holder as InfoRowVH).bind(item, onLinkClicked)
            is ReportItem.SectionTitle -> (holder as SectionTitleVH).bind(item)
            is ReportItem.BodyText -> (holder as BodyTextVH).bind(item)
            is ReportItem.Photo -> (holder as PhotoVH).bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is PhotoVH) {
            holder.clear()
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ReportItem>() {
        override fun areItemsTheSame(oldItem: ReportItem, newItem: ReportItem): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: ReportItem, newItem: ReportItem): Boolean = oldItem == newItem
    }

    companion object {
        private const val VIEW_TYPE_HEADER_LOGO = 0
        private const val VIEW_TYPE_INFO_ROW = 1
        private const val VIEW_TYPE_SECTION_TITLE = 2
        private const val VIEW_TYPE_BODY_TEXT = 3
        private const val VIEW_TYPE_PHOTO = 4
    }
}
