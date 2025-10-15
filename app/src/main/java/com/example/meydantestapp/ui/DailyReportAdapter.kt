package com.example.meydantestapp.ui

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.meydantestapp.DailyReportHeaderUi
import com.example.meydantestapp.DailyReportImageUi
import com.example.meydantestapp.DailyReportSectionType
import com.example.meydantestapp.DailyReportSectionUi
import com.example.meydantestapp.ProjectLocationUi
import com.example.meydantestapp.R
import com.example.meydantestapp.common.ReportHeadings
import com.example.meydantestapp.report.ReportInfoEntry
import com.otaliastudios.zoom.ZoomLayout
import kotlin.math.roundToInt

class DailyReportAdapter(
    private val onProjectLocationClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item {
        data class Header(val data: DailyReportHeaderUi) : Item()
        data class Location(val data: ProjectLocationUi) : Item()
        data class SectionTitle(val section: DailyReportSectionUi) : Item()
        data class Paragraph(val text: String) : Item()
        data class Image(val data: DailyReportImageUi) : Item()
    }

    private val items = mutableListOf<Item>()
    private var headerLogo: Bitmap? = null

    fun submit(
        header: DailyReportHeaderUi?,
        location: ProjectLocationUi?,
        sections: List<DailyReportSectionUi>,
        images: List<DailyReportImageUi>,
        logo: Bitmap?
    ) {
        headerLogo = logo
        items.clear()
        header?.let { items += Item.Header(it) }

        location?.let {
            val normalized = it.address.trim()
            if (normalized.isNotEmpty()) {
                items += Item.Location(it.copy(address = normalized))
            }
        }

        sections.forEach { section ->
            items += Item.SectionTitle(section)
            section.paragraphs.forEach { paragraph ->
                val normalized = paragraph.trim()
                if (normalized.isNotEmpty()) {
                    items += Item.Paragraph(normalized)
                }
            }
        }

        images.forEach { items += Item.Image(it) }
        notifyDataSetChanged()
    }

    fun updateLogo(logo: Bitmap?) {
        headerLogo = logo
        val index = items.indexOfFirst { it is Item.Header }
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Header -> VIEW_TYPE_HEADER
        is Item.Location -> VIEW_TYPE_LOCATION
        is Item.SectionTitle -> VIEW_TYPE_SECTION_TITLE
        is Item.Paragraph -> VIEW_TYPE_PARAGRAPH
        is Item.Image -> VIEW_TYPE_IMAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        VIEW_TYPE_HEADER -> HeaderViewHolder.create(parent)
        VIEW_TYPE_LOCATION -> LocationViewHolder.create(parent)
        VIEW_TYPE_SECTION_TITLE -> SectionHeaderViewHolder.create(parent)
        VIEW_TYPE_PARAGRAPH -> ParagraphViewHolder.create(parent)
        VIEW_TYPE_IMAGE -> ImageViewHolder.create(parent)
        else -> throw IllegalArgumentException("Unknown view type: $viewType")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderViewHolder).bind(item, headerLogo)
            is Item.Location -> (holder as LocationViewHolder).bind(item, onProjectLocationClick)
            is Item.SectionTitle -> (holder as SectionHeaderViewHolder).bind(item.section)
            is Item.Paragraph -> (holder as ParagraphViewHolder).bind(item.text)
            is Item.Image -> (holder as ImageViewHolder).bind(item.data)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ImageViewHolder) {
            holder.recycle()
        }
    }

    private class HeaderViewHolder(
        private val container: LinearLayout,
        private val titleView: TextView,
        private val subtitleView: TextView,
        private val logoView: ImageView,
        private val infoContainer: LinearLayout
    ) : RecyclerView.ViewHolder(container) {

        fun bind(item: Item.Header, logo: Bitmap?) {
            val context = container.context
            val heading = context.getString(item.data.headingRes)
            val projectName = item.data.projectName
            if (!projectName.isNullOrEmpty()) {
                titleView.text = projectName
                subtitleView.text = heading
                subtitleView.visibility = View.VISIBLE
            } else {
                titleView.text = heading
                subtitleView.visibility = View.GONE
            }

            if (logo != null) {
                logoView.setImageBitmap(logo)
                logoView.visibility = View.VISIBLE
            } else {
                logoView.setImageDrawable(null)
                logoView.visibility = View.GONE
            }

            infoContainer.removeAllViews()
            val entries = item.data.infoEntries
            entries.forEachIndexed { index, entry ->
                infoContainer.addView(createRow(context, entry))
                if (index < entries.lastIndex) {
                    infoContainer.addView(createDivider(context))
                }
            }
        }

        private fun createRow(context: Context, entry: ReportInfoEntry): View {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                textDirection = View.TEXT_DIRECTION_ANY_RTL
                weightSum = 1f
                val padding = context.dp(6)
                setPadding(padding, padding, padding, padding)
            }

            val labelView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.38f)
                TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Heading_H2)
                typeface = Typeface.create(typeface, Typeface.BOLD)
                text = context.getString(entry.labelRes)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                gravity = Gravity.START
            }

            val valueView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.62f)
                textSize = 15f
                setLineSpacing(0f, 1.15f)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                gravity = Gravity.START
                text = entry.value
            }

            row.addView(labelView)
            row.addView(valueView)
            return row
        }

        private fun createDivider(context: Context): View {
            return View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.dp(1)
                ).apply {
                    setMargins(0, context.dp(4), 0, context.dp(4))
                }
                setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
            }
        }

        companion object {
            fun create(parent: ViewGroup): HeaderViewHolder {
                val context = parent.context
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    textDirection = View.TEXT_DIRECTION_ANY_RTL
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        val margin = context.dp(12)
                        setMargins(margin, margin, margin, context.dp(8))
                    }
                    setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
                    setBackgroundResource(R.drawable.bg_daily_report_table)
                }

                val titleView = TextView(context).apply {
                    TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Heading_H1)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    gravity = Gravity.START
                }

                val subtitleView = TextView(context).apply {
                    TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Heading_H2)
                    visibility = View.GONE
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    gravity = Gravity.START
                    setPadding(0, context.dp(4), 0, context.dp(4))
                }

                val logoView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, context.dp(8), 0, context.dp(12))
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    visibility = View.GONE
                }

                val infoContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    textDirection = View.TEXT_DIRECTION_ANY_RTL
                }

                container.addView(titleView)
                container.addView(subtitleView)
                container.addView(logoView)
                container.addView(infoContainer)

                return HeaderViewHolder(container, titleView, subtitleView, logoView, infoContainer)
            }
        }
    }

    private class LocationViewHolder(
        private val container: LinearLayout,
        private val labelView: TextView,
        private val valueView: TextView
    ) : RecyclerView.ViewHolder(container) {

        fun bind(item: Item.Location, onClick: (String) -> Unit) {
            val context = container.context
            labelView.text = ReportHeadings.projectLocation(context)
            valueView.text = item.data.address
            val url = item.data.url?.trim()?.takeIf { it.isNotEmpty() }
            val defaultColor = ContextCompat.getColor(context, R.color.black)
            val linkColor = ContextCompat.getColor(context, R.color.hyperlink_blue)

            if (url != null) {
                valueView.paintFlags = valueView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                valueView.setTextColor(linkColor)
                container.isClickable = true
                container.isFocusable = true
                container.foreground = selectableBackground(context)
                val listener = View.OnClickListener { onClick(url) }
                container.setOnClickListener(listener)
                valueView.setOnClickListener(listener)
            } else {
                valueView.paintFlags = valueView.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                valueView.setTextColor(defaultColor)
                container.isClickable = false
                container.isFocusable = false
                container.foreground = null
                container.setOnClickListener(null)
                valueView.setOnClickListener(null)
            }
        }

        companion object {
            fun create(parent: ViewGroup): LocationViewHolder {
                val context = parent.context
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    textDirection = View.TEXT_DIRECTION_ANY_RTL
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        val margin = context.dp(12)
                        setMargins(margin, context.dp(8), margin, context.dp(12))
                    }
                    setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
                    setBackgroundResource(R.drawable.bg_daily_report_table)
                }

                val labelView = TextView(context).apply {
                    TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Heading_H2)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    gravity = Gravity.START
                }

                val valueView = TextView(context).apply {
                    textSize = 15f
                    setLineSpacing(0f, 1.15f)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    gravity = Gravity.START
                    setPadding(0, context.dp(6), 0, 0)
                }

                container.addView(labelView)
                container.addView(valueView)
                return LocationViewHolder(container, labelView, valueView)
            }
        }
    }

    private class SectionHeaderViewHolder(
        private val titleView: TextView
    ) : RecyclerView.ViewHolder(titleView) {

        fun bind(section: DailyReportSectionUi) {
            val context = titleView.context
            val text = when (section.type) {
                DailyReportSectionType.ACTIVITIES -> ReportHeadings.activities(context)
                DailyReportSectionType.EQUIPMENT -> ReportHeadings.equipment(context)
                DailyReportSectionType.OBSTACLES -> ReportHeadings.obstacles(context)
                DailyReportSectionType.NOTES -> NOTES_LABEL
            }
            titleView.text = text
        }

        companion object {
            fun create(parent: ViewGroup): SectionHeaderViewHolder {
                val context = parent.context
                val view = TextView(context).apply {
                    TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Heading_H2)
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        val margin = context.dp(12)
                        setMargins(margin, context.dp(20), margin, context.dp(8))
                    }
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    gravity = Gravity.START
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    textDirection = View.TEXT_DIRECTION_ANY_RTL
                }
                return SectionHeaderViewHolder(view)
            }
        }
    }

    private class ParagraphViewHolder(
        private val textView: TextView
    ) : RecyclerView.ViewHolder(textView) {

        fun bind(rawText: String) {
            val trimmed = rawText.trim()
            textView.text = if (trimmed == PLACEHOLDER_TEXT) trimmed else "• $trimmed"
        }

        companion object {
            fun create(parent: ViewGroup): ParagraphViewHolder {
                val context = parent.context
                val view = TextView(context).apply {
                    textSize = 15f
                    setLineSpacing(0f, 1.2f)
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        val margin = context.dp(16)
                        setMargins(margin, context.dp(4), margin, context.dp(4))
                    }
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    gravity = Gravity.START
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    textDirection = View.TEXT_DIRECTION_ANY_RTL
                }
                return ParagraphViewHolder(view)
            }
        }
    }

    private class ImageViewHolder(
        private val container: FrameLayout,
        private val zoomLayout: ZoomLayout,
        private val imageView: ImageView,
        private val progressBar: ProgressBar,
        private val errorView: TextView
    ) : RecyclerView.ViewHolder(container) {

        fun bind(image: DailyReportImageUi) {
            zoomLayout.zoomTo(1f, false)
            progressBar.visibility = View.VISIBLE
            errorView.visibility = View.GONE
            imageView.setImageDrawable(null)

            Glide.with(imageView)
                .load(image.url)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBar.visibility = View.GONE
                        errorView.visibility = View.VISIBLE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBar.visibility = View.GONE
                        errorView.visibility = View.GONE
                        return false
                    }
                })
                .into(imageView)
        }

        fun recycle() {
            Glide.with(imageView).clear(imageView)
            imageView.setImageDrawable(null)
            progressBar.visibility = View.GONE
            errorView.visibility = View.GONE
        }

        companion object {
            fun create(parent: ViewGroup): ImageViewHolder {
                val context = parent.context
                val frame = FrameLayout(context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        val margin = context.dp(12)
                        setMargins(margin, context.dp(12), margin, context.dp(12))
                    }
                    setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    textDirection = View.TEXT_DIRECTION_ANY_RTL
                }

                val zoom = ZoomLayout(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setMinZoom(1f)
                    setMaxZoom(4f)
                }

                val imageView = ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = context.getString(R.string.report_section_info)
                }

                zoom.addView(imageView)

                val progress = ProgressBar(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                    visibility = View.GONE
                }

                val error = TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    )
                    visibility = View.GONE
                    setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
                    text = ERROR_IMAGE
                    setTextColor(ContextCompat.getColor(context, R.color.brand_red_light_theme))
                    setBackgroundColor(0x80FFFFFF.toInt())
                }

                frame.addView(zoom)
                frame.addView(progress)
                frame.addView(error)

                return ImageViewHolder(frame, zoom, imageView, progress, error)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_LOCATION = 1
        private const val VIEW_TYPE_SECTION_TITLE = 2
        private const val VIEW_TYPE_PARAGRAPH = 3
        private const val VIEW_TYPE_IMAGE = 4

        private const val NOTES_LABEL = "الملاحظات"
        private const val PLACEHOLDER_TEXT = "—"
        private const val ERROR_IMAGE = "تعذّر تحميل الصورة"

        private fun Context.dp(value: Int): Int = (resources.displayMetrics.density * value).roundToInt()

        private fun selectableBackground(context: Context) = context.obtainStyledAttributes(
            intArrayOf(android.R.attr.selectableItemBackground)
        ).use { it.getDrawable(0) }
    }
}

private inline fun <T : TypedArray?, R> T.use(block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        this?.recycle()
    }
}
