package com.example.meydantestapp.ui.photolayout

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.R
import com.example.meydantestapp.models.PhotoTemplate
import com.example.meydantestapp.models.PhotoTemplates
import com.example.meydantestapp.models.TemplateId
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors

/**
 * BottomSheet لاختيار قالب شبكة الصور (Even/Odd).
 * - يعرض كل القوالب مع إمكانية التصفية.
 * - يعيد TemplateId عبر Fragment Result API.
 */
class TemplatePickerBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val REQUEST_KEY = "TemplatePickerBottomSheet:result"
        const val RESULT_TEMPLATE_ID = "templateId"
        private const val ARG_SELECTED = "selectedTemplateId"

        fun newInstance(preSelected: TemplateId? = null): TemplatePickerBottomSheet =
            TemplatePickerBottomSheet().apply {
                arguments = bundleOf(ARG_SELECTED to preSelected?.name)
            }

        fun show(
            fm: FragmentManager,
            preSelected: TemplateId? = null,
            tag: String = "TemplatePickerBottomSheet"
        ) {
            newInstance(preSelected).show(fm, tag)
        }
    }

    private lateinit var rv: RecyclerView
    private var adapter: TemplateOptionAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottomsheet_template_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rv = view.findViewById(R.id.rvTemplates)
        val chipGroup = view.findViewById<ChipGroup?>(R.id.chipGroupFilter)
        val chipAll = view.findViewById<Chip?>(R.id.chipAll)
        val chipEven = view.findViewById<Chip?>(R.id.chipEven)
        val chipOdd = view.findViewById<Chip?>(R.id.chipOdd)

        val pre = arguments?.getString(ARG_SELECTED)?.let { runCatching { TemplateId.valueOf(it) }.getOrNull() }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.addItemDecoration(DividerItemDecoration(requireContext(), RecyclerView.VERTICAL))

        fun submit(list: List<PhotoTemplate>) {
            if (adapter == null) {
                adapter = TemplateOptionAdapter(list, pre) { chosen ->
                    parentFragmentManager.setFragmentResult(
                        REQUEST_KEY,
                        bundleOf(RESULT_TEMPLATE_ID to chosen.id.name)
                    )
                    dismissAllowingStateLoss()
                }
                rv.adapter = adapter
            } else {
                adapter?.update(list)
            }
        }

        // الإعداد الافتراضي: عرض جميع القوالب
        submit(PhotoTemplates.all)

        // التصفية إن وُجدت الشرائح
        chipGroup?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                chipEven?.id -> submit(PhotoTemplates.even())
                chipOdd?.id -> submit(PhotoTemplates.odd())
                chipAll?.id -> submit(PhotoTemplates.all)
                else -> submit(PhotoTemplates.all)
            }
        }
    }
}

private class TemplateOptionAdapter(
    private var items: List<PhotoTemplate>,
    preSelected: TemplateId? = null,
    private val onClick: (PhotoTemplate) -> Unit
) : RecyclerView.Adapter<TemplateOptionVH>() {

    private var selectedId: TemplateId? = preSelected

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateOptionVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_template_option, parent, false)
        return TemplateOptionVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TemplateOptionVH, position: Int) {
        val item = items[position]
        holder.bind(item, selectedId)
        holder.itemView.setOnClickListener {
            selectedId = item.id
            notifyDataSetChanged()
            onClick(item)
        }
    }

    fun update(newItems: List<PhotoTemplate>) {
        items = newItems
        notifyDataSetChanged()
    }
}

private class TemplateOptionVH(view: View) : RecyclerView.ViewHolder(view) {
    private val card: MaterialCardView = view.findViewById(R.id.card)
    private val tvName: TextView = view.findViewById(R.id.tvName)
    private val tvMeta: TextView = view.findViewById(R.id.tvMeta)

    fun bind(item: PhotoTemplate, selected: TemplateId?) {
        tvName.text = item.displayName
        val type = if (item.isOdd) "Odd" else "Even"
        val slotsCount = item.slots.size
        tvMeta.text = "$type • ${item.columns}x${item.rows} • $slotsCount slots"

        // بما أننا أزلنا app:checkable من XML، نفعّلها برمجيًا ونُظهر تحديدًا بصريًا
        card.isCheckable = true
        val isSelected = item.id == selected
        card.isChecked = isSelected

        // ترسيم حدود التحديد بشكل أوضح
        val outline = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutline)
        val primary = MaterialColors.getColor(card, com.google.android.material.R.attr.colorPrimary)
        card.strokeColor = if (isSelected) primary else outline
        card.strokeWidth = if (isSelected) dpToPx(card, 2f) else dpToPx(card, 1f)

        // إمكانية النقر على البطاقة نفسها
        card.isClickable = true
        card.isFocusable = true
    }

    private fun dpToPx(v: View, dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, v.resources.displayMetrics).toInt()
}
