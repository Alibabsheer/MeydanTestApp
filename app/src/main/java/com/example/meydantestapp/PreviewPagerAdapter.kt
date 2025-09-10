package com.example.meydantestapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * PreviewPagerAdapter
 * - يعرض الصور داخل ViewPager2 في شاشة المعاينة.
 * - يدعم نداء اختياري عند النقر على الصورة (لإخفاء/إظهار الأشرطة).
 * - تفعيل معرفات ثابتة لتحسين التحديث عند استبدال صورة بعد التحرير.
 */
class PreviewPagerAdapter(
    private val images: MutableList<SelectedImage>,
    private val onImageTap: (() -> Unit)? = null
) : RecyclerView.Adapter<PreviewPagerAdapter.PreviewVH>() {

    init { setHasStableIds(true) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preview_page, parent, false)
        return PreviewVH(view)
    }

    override fun onBindViewHolder(holder: PreviewVH, position: Int) {
        val ctx = holder.itemView.context
        val item = images[position]

        Glide.with(ctx)
            .load(item.uri)
            .fitCenter()
            .into(holder.photo)

        holder.photo.setOnClickListener { onImageTap?.invoke() }
    }

    override fun getItemCount(): Int = images.size

    override fun getItemId(position: Int): Long {
        return images[position].uri?.hashCode()?.toLong() ?: position.toLong()
    }

    fun replaceAt(position: Int, newItem: SelectedImage) {
        if (position in images.indices) {
            images[position] = newItem
            notifyItemChanged(position)
        }
    }

    inner class PreviewVH(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.ivPhoto)
    }
}
