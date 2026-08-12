package com.murimgod.kuas_cafeteria_app.ui.dayview

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.murimgod.kuas_cafeteria_app.R
import com.murimgod.kuas_cafeteria_app.data.model.MenuItem

/**
 * Side-by-side nutrition/price comparison for the dishes the user added to the
 * compare set. Built programmatically as a simple horizontal-scrolling table:
 * one column per dish, one row per metric. The lowest value in each numeric row
 * is highlighted so the "leaner / cheaper" option is obvious at a glance.
 */
class CompareBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: DayViewViewModel by lazy {
        ViewModelProvider(requireParentFragment())[DayViewViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val items = viewModel.compareItems.value
        val ctx = requireContext()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.background_primary))
        }

        val title = TextView(ctx).apply {
            text = getString(R.string.compare_title)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(title)

        if (items.size < 2) {
            root.addView(TextView(ctx).apply {
                text = getString(R.string.compare_need_two)
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                setPadding(0, dp(24), 0, dp(24))
            })
            return root
        }

        val table = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        // First column: row labels.
        table.addView(labelColumn(items))
        // One column per dish.
        items.forEach { table.addView(dishColumn(it, items)) }

        val scroll = android.widget.HorizontalScrollView(ctx).apply { addView(table) }
        root.addView(ScrollView(ctx).apply { addView(scroll) })
        return root
    }

    private fun labelColumn(items: List<MenuItem>): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(cell("", header = true))
            addView(cell(getString(R.string.compare_price)))
            addView(cell(getString(R.string.compare_kcal)))
            addView(cell(getString(R.string.compare_protein)))
            addView(cell(getString(R.string.compare_fat)))
            addView(cell(getString(R.string.compare_salt)))
        }
    }

    private fun dishColumn(item: MenuItem, all: List<MenuItem>): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(cell(item.name, header = true))
            addView(cell(item.basePrice?.let { "¥$it" } ?: "—",
                best = isMin(item.basePrice?.toDouble(), all.map { it.basePrice?.toDouble() })))
            addView(cell(item.nutrition?.kcal?.let { "${it.toInt()}" } ?: "—",
                best = isMin(item.nutrition?.kcal, all.map { it.nutrition?.kcal })))
            addView(cell(item.nutrition?.protein?.let { fmt(it) } ?: "—",
                best = isMax(item.nutrition?.protein, all.map { it.nutrition?.protein })))
            addView(cell(item.nutrition?.fat?.let { fmt(it) } ?: "—",
                best = isMin(item.nutrition?.fat, all.map { it.nutrition?.fat })))
            addView(cell(item.nutrition?.salt?.let { fmt(it) } ?: "—",
                best = isMin(item.nutrition?.salt, all.map { it.nutrition?.salt })))
        }
    }

    private fun cell(text: String, header: Boolean = false, best: Boolean = false): TextView {
        val ctx = requireContext()
        return TextView(ctx).apply {
            this.text = text
            textSize = if (header) 13f else 14f
            gravity = Gravity.CENTER
            minWidth = dp(96)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            when {
                header -> {
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                }
                best -> {
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(ctx, R.color.accent))
                }
                else -> setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }
        }
    }

    private fun fmt(v: Double) = "%.1f g".format(v)

    private fun isMin(value: Double?, all: List<Double?>): Boolean {
        if (value == null) return false
        val nums = all.filterNotNull()
        return nums.size > 1 && value == nums.min()
    }

    private fun isMax(value: Double?, all: List<Double?>): Boolean {
        if (value == null) return false
        val nums = all.filterNotNull()
        return nums.size > 1 && value == nums.max()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
