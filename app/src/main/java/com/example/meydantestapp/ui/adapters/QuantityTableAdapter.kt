package com.example.meydantestapp

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.meydantestapp.databinding.ItemContractQuantityRowBinding
import java.text.NumberFormat
import java.util.*

class QuantityTableAdapter(
    private var items: MutableList<QuantityItem>
) : RecyclerView.Adapter<QuantityTableAdapter.QuantityViewHolder>() {

    fun updateData(newItems: MutableList<QuantityItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuantityViewHolder {
        val binding = ItemContractQuantityRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuantityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuantityViewHolder, position: Int) {
        val currentItem = items[position]
        holder.bind(currentItem)
    }

    override fun getItemCount(): Int = items.size

    inner class QuantityViewHolder(private val binding: ItemContractQuantityRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var quantityTextWatcher: TextWatcher? = null
        private var unitPriceTextWatcher: TextWatcher? = null

        fun bind(item: QuantityItem) {
            // تحويل رقم البند لأرقام إنجليزية
            val formattedItemNumber = item.itemNumber?.map {
                if (it.isDigit()) '0' + (it.code - '0'.code) else it
            }?.joinToString("") ?: ""

            binding.itemNumberText.text = formattedItemNumber
            binding.descriptionText.text = item.description
            binding.unitText.text = item.unit

            quantityTextWatcher?.let { binding.quantityEditText.removeTextChangedListener(it) }
            unitPriceTextWatcher?.let { binding.unitPriceEditText.removeTextChangedListener(it) }

            binding.quantityEditText.setText(NumberFormat.getInstance(Locale.US).format(item.quantity ?: 0.0))
            binding.unitPriceEditText.setText(NumberFormat.getInstance(Locale.US).format(item.unitPrice ?: 0.0))

            val total = (item.quantity ?: 0.0) * (item.unitPrice ?: 0.0)
            binding.totalValueText.text = NumberFormat.getInstance(Locale.US).format(total)

            quantityTextWatcher = object : TextWatcher {
                private var current = ""
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s.toString() != current) {
                        binding.quantityEditText.removeTextChangedListener(this)
                        val cleanString = s.toString().replace(",", "")
                        val parsed = cleanString.toDoubleOrNull() ?: 0.0
                        item.quantity = parsed
                        val newTotal = (item.quantity ?: 0.0) * (item.unitPrice ?: 0.0)
                        binding.totalValueText.text = NumberFormat.getInstance(Locale.US).format(newTotal)
                        val formatted = NumberFormat.getInstance(Locale.US).format(parsed)
                        current = formatted
                        binding.quantityEditText.setText(formatted)
                        binding.quantityEditText.setSelection(formatted.length)
                        binding.quantityEditText.addTextChangedListener(this)
                    }
                }
            }
            binding.quantityEditText.addTextChangedListener(quantityTextWatcher)

            unitPriceTextWatcher = object : TextWatcher {
                private var current = ""
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s.toString() != current) {
                        binding.unitPriceEditText.removeTextChangedListener(this)
                        val cleanString = s.toString().replace(",", "")
                        val parsed = cleanString.toDoubleOrNull() ?: 0.0
                        item.unitPrice = parsed
                        val newTotal = (item.quantity ?: 0.0) * (item.unitPrice ?: 0.0)
                        binding.totalValueText.text = NumberFormat.getInstance(Locale.US).format(newTotal)
                        val formatted = NumberFormat.getInstance(Locale.US).format(parsed)
                        current = formatted
                        binding.unitPriceEditText.setText(formatted)
                        binding.unitPriceEditText.setSelection(formatted.length)
                        binding.unitPriceEditText.addTextChangedListener(this)
                    }
                }
            }
            binding.unitPriceEditText.addTextChangedListener(unitPriceTextWatcher)
        }
    }
}
