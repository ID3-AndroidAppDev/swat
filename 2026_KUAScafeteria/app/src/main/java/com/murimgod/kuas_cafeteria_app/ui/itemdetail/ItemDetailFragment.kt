package com.murimgod.kuas_cafeteria_app.ui.itemdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.murimgod.kuas_cafeteria_app.R
import com.murimgod.kuas_cafeteria_app.data.model.MenuItem
import com.murimgod.kuas_cafeteria_app.databinding.FragmentItemDetailBinding

class ItemDetailFragment : Fragment() {

    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!

    private val gson = Gson()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { v, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.updatePadding(top = statusInsets.top)
            insets
        }

        val itemJson = arguments?.getString("itemJson") ?: return
        val item = gson.fromJson(itemJson, MenuItem::class.java) ?: return
        val campus = arguments?.getString("campus") ?: ""
        val date = arguments?.getString("date") ?: ""

        bindItem(item, campus, date)
    }

    private fun bindItem(item: MenuItem, campus: String, date: String) {
        binding.tvName.text = item.name

        if (item.nameJa?.isNotBlank() == true) {
            binding.tvNameJa.text = item.nameJa
            binding.tvNameJa.visibility = View.VISIBLE
        }

        if (campus.isNotBlank() && date.isNotBlank()) {
            val campusLabel = if (campus == "uzumasa") "Uzumasa" else "Kameoka"
            binding.tvContext.text = "$campusLabel · $date"
        }

        binding.priceRows.removeAllViews()
        item.prices.forEach { price ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 4
                layoutParams = lp
            }
            val tierLabel = TextView(requireContext()).apply {
                // Web parity: "set" → Set, anything else ("base"/"single") → Base.
                text = if (price.tier == "set") getString(R.string.set_meal)
                       else getString(R.string.la_carte)
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val effective = price.discountedYen ?: price.yen
            val priceText = TextView(requireContext()).apply {
                text = "¥$effective" + if (price.discount != null && price.discount > 0)
                    " (−${price.discount})" else ""
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            }
            row.addView(tierLabel)
            row.addView(priceText)
            binding.priceRows.addView(row)
        }

        val nutrition = item.nutrition
        if (nutrition != null && nutrition.kcal != null) {
            binding.cardNutrition.visibility = View.VISIBLE
            binding.tvKcalDetail.text = "${nutrition.kcal.toInt()} kcal"
            binding.tvProteinDetail.text = nutrition.protein?.let { "Protein %.1f g".format(it) } ?: ""
            binding.tvFatDetail.text = nutrition.fat?.let { "Fat %.1f g".format(it) } ?: ""
            binding.tvSaltDetail.text = nutrition.salt?.let { "Salt %.1f g".format(it) } ?: ""
        }

        if (item.allergens.isNotEmpty()) {
            binding.allergenSection.visibility = View.VISIBLE
            binding.chipsAllergenDetail.removeAllViews()
            item.allergens.forEach { allergen ->
                val chip = TextView(requireContext()).apply {
                    text = allergen.replaceFirstChar { it.uppercase() }
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.chip_allergen_fg))
                    background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_allergen)
                    setPadding(12, 6, 12, 6)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.setMargins(0, 0, 8, 0)
                    layoutParams = lp
                }
                binding.chipsAllergenDetail.addView(chip)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
