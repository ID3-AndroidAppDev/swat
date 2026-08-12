package com.murimgod.kuas_cafeteria_app.ui.dayview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.murimgod.kuas_cafeteria_app.R
import com.murimgod.kuas_cafeteria_app.databinding.ItemDayPillBinding
import java.time.format.DateTimeFormatter

class DayPillAdapter(
    private val onDaySelected: (DayPill) -> Unit
) : ListAdapter<DayPill, DayPillAdapter.VH>(DIFF) {

    private val dayNameFmt = DateTimeFormatter.ofPattern("EEE")

    inner class VH(private val b: ItemDayPillBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(pill: DayPill) {
            b.tvDayName.text = pill.date.format(dayNameFmt).uppercase()
            b.tvDayNumber.text = pill.date.dayOfMonth.toString()

            b.root.isSelected = pill.isSelected
            b.root.alpha = if (pill.isAvailable) 1f else 0.35f

            val textColor = if (pill.isSelected)
                ContextCompat.getColor(b.root.context, R.color.white)
            else
                ContextCompat.getColor(b.root.context, R.color.text_secondary)
            b.tvDayName.setTextColor(textColor)
            b.tvDayNumber.setTextColor(textColor)

            b.root.setOnClickListener { onDaySelected(pill) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemDayPillBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val availableWidth = parent.width - parent.paddingStart - parent.paddingEnd
        if (availableWidth > 0) {
            b.root.layoutParams = RecyclerView.LayoutParams(availableWidth / 5, RecyclerView.LayoutParams.WRAP_CONTENT)
        }
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<DayPill>() {
            override fun areItemsTheSame(a: DayPill, b: DayPill) = a.date == b.date
            override fun areContentsTheSame(a: DayPill, b: DayPill) = a == b
        }
    }
}
