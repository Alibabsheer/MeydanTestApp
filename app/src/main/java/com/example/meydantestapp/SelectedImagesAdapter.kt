package com.example.meydantestapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * SelectedImagesAdapter
 * شبكة المصغّرات للصور المختارة (تُعرض عادةً بعد الاختيار/داخل شاشة إنشاء التقرير).
 * التعديلات:
 * - استبدال شارة الترتيب الرقمية بعلامة ✓ في أسفل-يسار مع خلفية دائرية (bg_badge_check).
 * - إضافة طبقة تعتيم خفيفة selectionScrim عند التحديد (كل العناصر هنا مختارة أساسًا).
 * - الحفاظ على تراكب +N في آخر خانة مرئية عندما يكون هناك صور أكثر من maxVisible.
 */
class SelectedImagesAdapter(
    private val images: MutableList<SelectedImage>,
    private val maxVisible: Int = 3,
    private val onItemClick: (position: Int) -> Unit
) : RecyclerView.Adapter<SelectedImagesAdapter.ImageVH>() {

    companion object {
        const val MAX_SELECTION = 10
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_image, parent, false)
        return ImageVH(view)
    }

    override fun onBindViewHolder(holder: ImageVH, position: Int) {
        val ctx = holder.itemView.context
        val total = images.size

        // حمّل المصغّر دائمًا
        val item = images[position]
        Glide.with(ctx)
            .load(item.uri)
            .centerCrop()
            .into(holder.thumb)

        // افتراضيًا: كل العناصر مختارة => أظهر الشارة ✓ وطبقة التعتيم
        holder.selectionScrim.visibility = View.VISIBLE
        holder.indexBadge.visibility = View.VISIBLE
        holder.indexBadge.text = "✓"

        // خانة +N (آخر عنصر مرئي عند وجود صور أكثر من maxVisible)
        if (position == maxVisible - 1 && total > maxVisible) {
            val hidden = total - maxVisible
            holder.moreOverlay.visibility = View.VISIBLE
            holder.moreCount.text = "+$hidden"
            // لإبراز +N، أخفِ الشارة في هذه الخانة فقط
            holder.indexBadge.visibility = View.GONE
        } else {
            holder.moreOverlay.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onItemClick(position) }
    }

    override fun getItemCount(): Int = if (images.size > maxVisible) maxVisible else images.size

    fun updateData(newList: List<SelectedImage>) {
        images.clear()
        images.addAll(newList.take(MAX_SELECTION))
        notifyDataSetChanged()
    }

    inner class ImageVH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.ivThumb)
        val indexBadge: TextView = view.findViewById(R.id.tvIndexBadge)
        val moreOverlay: View = view.findViewById(R.id.overlayMore)
        val moreCount: TextView = view.findViewById(R.id.tvMoreCount)
        val selectionScrim: View = view.findViewById(R.id.selectionScrim)
    }
}
