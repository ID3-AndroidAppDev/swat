package com.murimgod.kuas_cafeteria_app.ui.tray

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.murimgod.kuas_cafeteria_app.databinding.ItemTrayEntryBinding

class TrayAdapter(
    private val onIncrease: (TrayEntry) -> Unit,
    private val onDecrease: (TrayEntry) -> Unit
) : ListAdapter<TrayEntry, TrayAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemTrayEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: TrayEntry) {
            binding.tvTrayItemName.text = entry.item.name
            val kcal = entry.item.nutrition?.kcal?.toInt()
            val tierLabel = if (entry.isSet) "set" else "single"
            binding.tvTrayItemMeta.text = buildString {
                if (kcal != null) append("${kcal * entry.qty} kcal · ")
                append(tierLabel)
                if (entry.qty > 1) append(" ×${entry.qty}")
            }
            val price = entry.effectivePrice
            binding.tvTrayItemPrice.text = if (price == 0) "¥0" else "¥$price"
            binding.tvQty.text = entry.qty.toString()
            binding.btnIncrease.setOnClickListener { onIncrease(entry) }
            binding.btnDecrease.setOnClickListener { onDecrease(entry) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTrayEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TrayEntry>() {
            override fun areItemsTheSame(a: TrayEntry, b: TrayEntry) =
                a.item.id == b.item.id && a.isSet == b.isSet
            override fun areContentsTheSame(a: TrayEntry, b: TrayEntry) = a == b
        }
    }
}
