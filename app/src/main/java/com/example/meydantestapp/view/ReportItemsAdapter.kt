package com.example.meydantestapp.view

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.common.ReportHeadings
import com.example.meydantestapp.view.holder.BodyTextVH
import com.example.meydantestapp.view.holder.HeaderLogoVH
import com.example.meydantestapp.view.holder.InfoRowVH
import com.example.meydantestapp.view.holder.PhotoVH
import com.example.meydantestapp.view.holder.SectionTitleVH

class ReportItemsAdapter(
    private val onLinkClicked: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ReportItem>()
    private var logoBitmap: Bitmap? = null

    fun submitItems(newItems: List<ReportItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun updateLogo(bitmap: Bitmap?) {
        logoBitmap = bitmap
        val index = items.indexOfFirst { it is ReportItem.HeaderLogo }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        ReportItem.HeaderLogo -> VIEW_TYPE_HEADER
        is ReportItem.InfoRow -> VIEW_TYPE_INFO
        is ReportItem.SectionTitle -> VIEW_TYPE_SECTION
        is ReportItem.BodyText -> VIEW_TYPE_BODY
        is ReportItem.Photo -> VIEW_TYPE_PHOTO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderLogoVH.inflate(inflater, parent)
            VIEW_TYPE_INFO -> InfoRowVH.inflate(inflater, parent)
            VIEW_TYPE_SECTION -> SectionTitleVH.inflate(inflater, parent)
            VIEW_TYPE_PHOTO -> PhotoVH.inflate(inflater, parent)
            else -> BodyTextVH.inflate(inflater, parent)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            ReportItem.HeaderLogo -> (holder as HeaderLogoVH).bind(logoBitmap)
            is ReportItem.InfoRow -> (holder as InfoRowVH).bind(item, onLinkClicked)
            is ReportItem.SectionTitle -> (holder as SectionTitleVH).bind(item) { context, @StringRes resId ->
                resolveHeadingText(context, resId)
            }
            is ReportItem.BodyText -> (holder as BodyTextVH).bind(item)
            is ReportItem.Photo -> (holder as PhotoVH).bind(item.uri)
        }
    }

    private fun resolveHeadingText(context: android.content.Context, @StringRes resId: Int): String {
        return when (resId) {
            R.string.report_section_info -> ReportHeadings.info(context)
            R.string.report_section_project_location -> ReportHeadings.projectLocation(context)
            R.string.report_section_activities -> ReportHeadings.activities(context)
            R.string.report_section_equipment -> ReportHeadings.equipment(context)
            R.string.report_section_obstacles -> ReportHeadings.obstacles(context)
            else -> context.getString(resId)
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_INFO = 1
        private const val VIEW_TYPE_SECTION = 2
        private const val VIEW_TYPE_BODY = 3
        private const val VIEW_TYPE_PHOTO = 4
    }
}
