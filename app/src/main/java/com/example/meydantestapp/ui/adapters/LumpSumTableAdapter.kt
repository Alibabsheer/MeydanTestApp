package com.example.meydantestapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.databinding.ItemContractLumpSumRowBinding
import java.text.NumberFormat
import java.util.*

class LumpSumTableAdapter(
    private var items: MutableList<LumpSumItem>
) : RecyclerView.Adapter<LumpSumTableAdapter.LumpSumViewHolder>() {

    fun updateData(newItems: MutableList<LumpSumItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LumpSumViewHolder {
        val binding = ItemContractLumpSumRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LumpSumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LumpSumViewHolder, position: Int) {
        val currentItem = items[position]
        holder.bind(currentItem)
    }

    override fun getItemCount(): Int = items.size

    inner class LumpSumViewHolder(private val binding: ItemContractLumpSumRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LumpSumItem) {
            // تحويل رقم البند لأرقام إنجليزية دائمًا
            val formattedItemNumber = item.itemNumber?.map {
                if (it.isDigit()) '0' + (it.code - '0'.code) else it
            }?.joinToString("") ?: ""

            binding.itemNumberText.text = formattedItemNumber
            binding.descriptionText.text = item.description

            // تنسيق القيمة الإجمالية فقط (مسموح لأنها رقم حقيقي)
            val numberFormat = NumberFormat.getInstance(Locale.US)
            binding.totalValueText.text = numberFormat.format(item.totalValue)
        }
    }
}
