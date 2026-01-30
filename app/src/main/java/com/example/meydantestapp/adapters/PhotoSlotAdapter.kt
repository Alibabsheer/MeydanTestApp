package com.example.meydantestapp.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.clear
import com.example.meydantestapp.R
import com.example.meydantestapp.models.PhotoEntry
import com.example.meydantestapp.models.PhotoTemplate

/**
 * PhotoSlotAdapter – إدارة شبكة القالب (صور + تعليق أسفل كل خانة) بأسلوب متوافق مع الخطة الاستثنائية.
 *
 * المزايا الرئيسية:
 * - تثبيت Stable IDs في init{} (وليس بعد الارتباط) لمنع IllegalStateException.
 * - إشعارات Safe للـ RecyclerView عند التحديث أثناء حساب الـ layout.
 * - دعم تحديث التعليق Payload لتجنب إعادة ربط مكلفة.
 * - الحفاظ على نفس الدوال/التواقيع المستخدمة سابقًا.
 */
class PhotoSlotAdapter(
    private var template: PhotoTemplate,
    private val onPickImage: (slotIndex: Int) -> Unit,
    private val onCaptionChanged: (slotIndex: Int, caption: String?) -> Unit,
    private val onRemoveImage: (slotIndex: Int) -> Unit
) : RecyclerView.Adapter<PhotoSlotAdapter.SlotVH>() {

    private val slots: MutableList<PhotoEntry?> = MutableList(template.slots.size) { null }
    private var attachedRv: RecyclerView? = null

    companion object {
        private const val PAYLOAD_CAPTION_ONLY = "payload_caption_only"
    }

    init {
        // ✅ تثبيت Stable IDs مبكّرًا قبل ربط الـ Adapter بأي RecyclerView
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRv = recyclerView
        // ⚠️ لا تستدعِ setHasStableIds هنا إطلاقًا
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (attachedRv === recyclerView) attachedRv = null
    }

    override fun getItemId(position: Int): Long = position.toLong()

    private fun safeNotifyDataSetChanged() {
        val rv = attachedRv
        if (rv?.isComputingLayout == true) rv.post { notifyDataSetChanged() } else notifyDataSetChanged()
    }

    private fun safeNotifyItemChanged(position: Int, payload: Any? = null) {
        if (position !in 0 until itemCount) return
        val rv = attachedRv
        if (rv?.isComputingLayout == true) {
            rv.post { if (payload != null) notifyItemChanged(position, payload) else notifyItemChanged(position) }
        } else {
            if (payload != null) notifyItemChanged(position, payload) else notifyItemChanged(position)
        }
    }

    /** استبدال القالب وإعادة تهيئة الخانات فارغة */
    fun submitTemplate(newTemplate: PhotoTemplate) {
        template = newTemplate
        slots.clear()
        repeat(template.slots.size) { slots.add(null) }
        safeNotifyDataSetChanged()
    }

    /** حقن عناصر جاهزة (مثلاً عند الاسترجاع) بافتراض slotIndex متوافق مع القالب */
    fun submitEntries(entries: List<PhotoEntry>) {
        for (i in slots.indices) slots[i] = null
        entries.forEach { e -> if (e.slotIndex in slots.indices) slots[e.slotIndex] = e }
        safeNotifyDataSetChanged()
    }

    /** الحصول على جميع الخانات كما هي (قد تحتوي null) */
    fun getCurrentEntries(): List<PhotoEntry?> = slots.toList()

    /** إرجاع الخانات المملوءة فقط – للاستخدام عند الحفظ/الرفع */
    fun getFilledEntries(): List<PhotoEntry> = slots.mapNotNull { entry -> if (hasImage(entry)) entry else null }

    /** استبدال عنصر خانة محددة بالكامل */
    fun setEntry(slotIndex: Int, entry: PhotoEntry?) {
        if (slotIndex !in slots.indices) return
        slots[slotIndex] = entry
        safeNotifyItemChanged(slotIndex)
    }

    /** تفريغ خانة محددة */
    fun clearSlot(slotIndex: Int) = setEntry(slotIndex, null)

    /** تحديث التعليق لخانة معينة بدون إعادة تحميل كامل */
    fun updateCaptionAt(slotIndex: Int, caption: String?) {
        if (slotIndex !in slots.indices) return
        val cur = slots[slotIndex]
        if (hasImage(cur)) {
            slots[slotIndex] = cur?.copy(caption = caption)
            safeNotifyItemChanged(slotIndex, PAYLOAD_CAPTION_ONLY)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_photo_slot, parent, false)
        return SlotVH(v)
    }

    override fun getItemCount(): Int = template.slots.size

    override fun onBindViewHolder(holder: SlotVH, position: Int) {
        holder.bind(slots[position])
        holder.btnPick.setOnClickListener { onPickImage(position) }
        holder.btnRemove.setOnClickListener { onRemoveImage(position) }
    }

    override fun onBindViewHolder(holder: SlotVH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_CAPTION_ONLY)) {
            holder.updateCaptionOnly(slots[position]?.caption)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onViewRecycled(holder: SlotVH) {
        super.onViewRecycled(holder)
        holder.iv.clear()
    }

    inner class SlotVH(view: View) : RecyclerView.ViewHolder(view) {
        val iv: ImageView = view.findViewById(R.id.ivSlot)
        val tvHint: TextView = view.findViewById(R.id.tvHint)
        val etCaption: EditText = view.findViewById(R.id.etCaption)
        val btnPick: ImageButton = view.findViewById(R.id.btnPick)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemove)

        private var watcher: TextWatcher? = null
        private var suppressCallback = false

        fun bind(entry: PhotoEntry?) {
            // إزالة أي مراقب سابق لمنع التكرار
            watcher?.let { etCaption.removeTextChangedListener(it) }
            watcher = null

            if (hasImage(entry)) {
                tvHint.visibility = View.GONE
                val src: Any = entry!!.originalUrl ?: entry.localUri!!
                iv.load(src) {
                    crossfade(true)
                    placeholder(R.drawable.ic_image_placeholder)
                    error(R.drawable.ic_image_placeholder)
                }

                suppressCallback = true
                etCaption.setText(entry.caption ?: "")
                suppressCallback = false

                etCaption.isEnabled = true
                btnRemove.visibility = View.VISIBLE

                // تحديث ذاكرة التعليق أثناء الكتابة (بدون إشعارات متكررة)
                watcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (suppressCallback) return
                        val pos = bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            slots[pos] = slots[pos]?.copy(caption = s?.toString())
                        }
                    }
                }
                etCaption.addTextChangedListener(watcher)

                // نُبلّغ الـ ViewModel عند فقدان التركيز فقط
                etCaption.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val pos = bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            onCaptionChanged(pos, etCaption.text?.toString())
                        }
                    }
                }

            } else {
                tvHint.visibility = View.VISIBLE
                iv.setImageResource(R.drawable.ic_image_placeholder)

                suppressCallback = true
                etCaption.setText("")
                suppressCallback = false

                etCaption.isEnabled = false
                btnRemove.visibility = View.INVISIBLE
                etCaption.onFocusChangeListener = null
            }
        }

        fun updateCaptionOnly(caption: String?) {
            watcher?.let { etCaption.removeTextChangedListener(it) }
            watcher = null
            suppressCallback = true
            etCaption.setText(caption ?: "")
            suppressCallback = false
        }
    }

    private fun hasImage(entry: PhotoEntry?): Boolean {
        return entry?.localUri != null || !entry?.originalUrl.isNullOrBlank()
    }
}

/**
 * مرفق مساعد لتكوين GridLayoutManager بعدد أعمدة القالب.
 * ملاحظة: القوالب عمودية؛ لذا يُفضّل استخدام أعمدة قليلة (1..3) حسب تعريف القالب.
 */
fun RecyclerView.applyTemplateGrid(template: PhotoTemplate) {
    val glm = GridLayoutManager(context, template.columns)
    layoutManager = glm
}
